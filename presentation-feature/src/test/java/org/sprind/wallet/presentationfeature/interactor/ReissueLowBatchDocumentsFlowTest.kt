/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.presentationfeature.interactor

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.DisclosedDocumentDomain
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.DocumentFormat
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingReissuePartialState
import eu.europa.ec.presentationfeature.interactor.reissueLowBatchDocumentsFlow
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.sprind.wallet.corelogic.controller.ReissueDocumentPartialState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the batch-refresh trigger logic shared by both presentation interactors. The key
 * behaviour: a PID is stored as two documents (mdoc + SD-JWT VC) but a presentation only discloses
 * one, so whenever a PID was presented both formats are evaluated, and the moment EITHER of them is
 * down to `minAvailableCredentials` (1) the two are refreshed together as a single batch.
 */
class ReissueLowBatchDocumentsFlowTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val presentationController: WalletCorePresentationController = mock()
    private val documentsController: WalletCoreDocumentsController = mock()
    private val logController: LogController = mock()

    // Format types must match DocumentIdentifier.MdocPid / SdJwtPid so toDocumentIdentifier().isPid is true.
    private val mdocPidFormat = MsoMdocFormat("eu.europa.ec.eudi.pid.1")
    private val sdJwtPidFormat = SdJwtVcFormat("urn:eudi:pid:de:1")

    private fun issuedDoc(id: String, format: DocumentFormat, credentials: Int): IssuedDocument =
        mock<IssuedDocument>().also {
            whenever(it.id).thenReturn(id)
            whenever(it.format).thenReturn(format)
            wheneverBlocking { it.credentialsCount() }.thenReturn(credentials)
        }

    private fun disclosed(id: String) =
        DisclosedDocumentDomain(documentId = id, disclosedClaims = emptySet())

    private fun runFlow() =
        reissueLowBatchDocumentsFlow(presentationController, documentsController, logController)

    @Test
    fun `refreshes both PID formats when the undisclosed sibling is down to its last credential`() =
        coroutineRule.runTest {
            val sdJwt = issuedDoc("pid-sdjwt", sdJwtPidFormat, credentials = 3)
            val mdoc = issuedDoc("pid-mdoc", mdocPidFormat, credentials = 1)
            whenever(presentationController.disclosedDocuments)
                .thenReturn(mutableListOf(disclosed("pid-sdjwt")))
            whenever(documentsController.getAllIssuedDocuments()).thenReturn(listOf(sdJwt, mdoc))
            whenever(documentsController.reissueDocument(any()))
                .thenReturn(flowOf(ReissueDocumentPartialState.InProgress))

            val states = runFlow().toList()

            verify(documentsController).reissueDocument("pid-mdoc")
            verify(documentsController).reissueDocument("pid-sdjwt")
            assertTrue(states.last() is PresentationLoadingReissuePartialState.Success)
        }

    @Test
    fun `refreshes at the threshold boundary (count equals minAvailableCredentials)`() =
        coroutineRule.runTest {
            val sdJwt = issuedDoc("pid-sdjwt", sdJwtPidFormat, credentials = 1) // == 1 -> refresh
            val mdoc = issuedDoc("pid-mdoc", mdocPidFormat, credentials = 5)
            whenever(presentationController.disclosedDocuments)
                .thenReturn(mutableListOf(disclosed("pid-sdjwt")))
            whenever(documentsController.getAllIssuedDocuments()).thenReturn(listOf(sdJwt, mdoc))
            whenever(documentsController.reissueDocument(any()))
                .thenReturn(flowOf(ReissueDocumentPartialState.InProgress))

            runFlow().toList()

            verify(documentsController).reissueDocument("pid-sdjwt")
            verify(documentsController).reissueDocument("pid-mdoc")
        }

    @Test
    fun `does not refresh while both PID formats still have more than one credential`() =
        coroutineRule.runTest {
            val sdJwt = issuedDoc("pid-sdjwt", sdJwtPidFormat, credentials = 2)
            val mdoc = issuedDoc("pid-mdoc", mdocPidFormat, credentials = 5)
            whenever(presentationController.disclosedDocuments)
                .thenReturn(mutableListOf(disclosed("pid-sdjwt")))
            whenever(documentsController.getAllIssuedDocuments()).thenReturn(listOf(sdJwt, mdoc))

            val states = runFlow().toList()

            assertEquals(listOf(PresentationLoadingReissuePartialState.NotNeeded), states)
            verify(documentsController, never()).reissueDocument(any())
        }

    @Test
    fun `a low PID batch does not drag in an unrelated credential presented alongside it`() =
        coroutineRule.runTest {
            val sdJwt = issuedDoc("pid-sdjwt", sdJwtPidFormat, credentials = 1)
            val mdoc = issuedDoc("pid-mdoc", mdocPidFormat, credentials = 5)
            val diploma = issuedDoc("diploma", MsoMdocFormat("org.example.diploma"), credentials = 4)
            whenever(presentationController.disclosedDocuments)
                .thenReturn(mutableListOf(disclosed("pid-sdjwt"), disclosed("diploma")))
            whenever(documentsController.getAllIssuedDocuments())
                .thenReturn(listOf(sdJwt, mdoc, diploma))
            whenever(documentsController.reissueDocument(any()))
                .thenReturn(flowOf(ReissueDocumentPartialState.InProgress))

            runFlow().toList()

            verify(documentsController).reissueDocument("pid-sdjwt")
            verify(documentsController).reissueDocument("pid-mdoc")
            verify(documentsController, never()).reissueDocument("diploma")
        }

    @Test
    fun `refreshes a non-PID credential on its own once it is down to its last credential`() =
        coroutineRule.runTest {
            val diploma = issuedDoc("diploma", MsoMdocFormat("org.example.diploma"), credentials = 1)
            whenever(presentationController.disclosedDocuments)
                .thenReturn(mutableListOf(disclosed("diploma")))
            whenever(documentsController.getAllIssuedDocuments()).thenReturn(listOf(diploma))
            whenever(documentsController.reissueDocument("diploma"))
                .thenReturn(flowOf(ReissueDocumentPartialState.InProgress))

            runFlow().toList()

            verify(documentsController).reissueDocument("diploma")
        }

    @Test
    fun `still refreshes the sibling PID format after the first one fails`() =
        coroutineRule.runTest {
            val sdJwt = issuedDoc("pid-sdjwt", sdJwtPidFormat, credentials = 1)
            val mdoc = issuedDoc("pid-mdoc", mdocPidFormat, credentials = 5)
            whenever(presentationController.disclosedDocuments)
                .thenReturn(mutableListOf(disclosed("pid-sdjwt")))
            whenever(documentsController.getAllIssuedDocuments()).thenReturn(listOf(sdJwt, mdoc))
            whenever(documentsController.reissueDocument("pid-sdjwt"))
                .thenReturn(flowOf(ReissueDocumentPartialState.Failure("boom")))
            whenever(documentsController.reissueDocument("pid-mdoc"))
                .thenReturn(flowOf(ReissueDocumentPartialState.Success(listOf("pid-mdoc-new"))))

            val states = runFlow().toList()

            verify(documentsController).reissueDocument("pid-sdjwt")
            verify(documentsController).reissueDocument("pid-mdoc")
            val failure = states.last() as PresentationLoadingReissuePartialState.Failure
            assertTrue(failure.error.contains("pid-sdjwt"), "expected the failing id, got: ${failure.error}")
            assertTrue(failure.error.contains("boom"), "expected the upstream message, got: ${failure.error}")
        }

    @Test
    fun `does not refresh when nothing was disclosed`() =
        coroutineRule.runTest {
            whenever(presentationController.disclosedDocuments).thenReturn(mutableListOf())

            val states = runFlow().toList()

            assertEquals(listOf(PresentationLoadingReissuePartialState.NotNeeded), states)
            verify(documentsController, never()).reissueDocument(any())
        }
}
