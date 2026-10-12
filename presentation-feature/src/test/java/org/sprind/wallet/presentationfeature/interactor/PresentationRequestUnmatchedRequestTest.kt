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

import eu.europa.ec.authenticationlogic.controller.storage.WalletPinUnBlockTimeStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.RequestedCredentialSet
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.DocumentFormat
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorImpl
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorPartialState
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.corelogic.model.RelyingPartyRegistrationStatus
import kotlin.test.assertIs

/**
 * A request nothing in the wallet can satisfy is told apart by whether the wallet holds the
 * requested credentials at all, as the iOS wallet does (WD-4430): a missing credential sends the
 * user to add one, a credential that lacks the requested claims is the request's fault. Unlike
 * iOS, the outcome honours `credential_sets` alternatives and does not depend on query order.
 */
class PresentationRequestUnmatchedRequestTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var walletCorePresentationController: WalletCorePresentationController

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    @Mock
    private lateinit var walletPinUnBlockTimeStorageController: WalletPinUnBlockTimeStorageController

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var rwscaInteractor: RwscaInteractor

    private lateinit var interactor: PresentationRequestInteractor
    private lateinit var closeable: AutoCloseable

    private val events = MutableSharedFlow<TransferEventPartialState>(replay = 1)

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.genericErrorMessage()).thenReturn(mockedGenericErrorMessage)
        whenever(walletCorePresentationController.events).thenReturn(events)
        interactor = PresentationRequestInteractorImpl(
            resourceProvider = resourceProvider,
            walletCorePresentationController = walletCorePresentationController,
            walletCoreDocumentsController = walletCoreDocumentsController,
            walletPinUnBlockTimeStorageController = walletPinUnBlockTimeStorageController,
            logController = logController,
            rwscaInteractor = rwscaInteractor,
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `a held credential that lacks the requested claims makes the request unsatisfiable`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(unmatched(allOf(setOf(PID_DOCTYPE))))

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    @Test
    fun `an sd-jwt credential is recognised by any of the accepted vcts`() =
        coroutineRule.runTest {
            holding(document(SdJwtVcFormat(PID_VCT)))
            events.emit(unmatched(allOf(setOf("urn:other", PID_VCT))))

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    @Test
    fun `a requested credential type the wallet does not hold is reported as missing`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(unmatched(allOf(setOf(MDL_DOCTYPE))))

            assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
        }

    @Test
    fun `a missing credential is reported as missing whatever the query order`() =
        // The iOS wallet reports whichever query fails first; here the outcome must not depend on it.
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))

            events.emit(unmatched(allOf(setOf(PID_DOCTYPE), setOf(MDL_DOCTYPE))))
            assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())

            events.emit(unmatched(allOf(setOf(MDL_DOCTYPE), setOf(PID_DOCTYPE))))
            assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
        }

    @Test
    fun `a held alternative makes the request unsatisfiable rather than missing a credential`() =
        // "PID as mdoc or as SD-JWT": holding the mdoc one means the user has a PID; it only lacks
        // the claims, so telling them to add a credential would be wrong.
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(
                unmatched(
                    listOf(
                        RequestedCredentialSet(
                            required = true,
                            options = listOf(listOf(setOf(PID_VCT)), listOf(setOf(PID_DOCTYPE))),
                        )
                    )
                )
            )

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    @Test
    fun `a required set with no held option is reported as missing`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(
                unmatched(
                    listOf(
                        RequestedCredentialSet(
                            required = true,
                            options = listOf(listOf(setOf(PID_DOCTYPE))),
                        ),
                        RequestedCredentialSet(
                            required = true,
                            options = listOf(listOf(setOf(MDL_DOCTYPE)), listOf(setOf(PID_VCT))),
                        ),
                    )
                )
            )

            assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
        }

    @Test
    fun `an optional set the wallet cannot serve does not count as a missing credential`() =
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(
                unmatched(
                    listOf(
                        RequestedCredentialSet(
                            required = true,
                            options = listOf(listOf(setOf(PID_DOCTYPE))),
                        ),
                        RequestedCredentialSet(
                            required = false,
                            options = listOf(listOf(setOf(MDL_DOCTYPE))),
                        ),
                    )
                )
            )

            assertIs<PresentationRequestInteractorPartialState.Unsatisfiable>(firstState())
        }

    @Test
    fun `a request without credential types keeps the missing-credential outcome`() =
        // Proximity requests are not DCQL, so nothing tells the wallet what type was asked for.
        coroutineRule.runTest {
            holding(document(MsoMdocFormat(PID_DOCTYPE)))
            events.emit(unmatched(requestedCredentialSets = null))

            assertIs<PresentationRequestInteractorPartialState.NoData>(firstState())
        }

    private suspend fun firstState() = interactor.getRequestDocuments().first()

    private fun holding(vararg documents: IssuedDocument) {
        whenever(walletCoreDocumentsController.getAllIssuedDocuments()).thenReturn(documents.toList())
    }

    private fun document(format: DocumentFormat) =
        mock<IssuedDocument> {
            on { this.format } doReturn format
        }

    /** A request without `credential_sets`: every query is required. */
    private fun allOf(vararg queryTypes: Set<String>) =
        listOf(RequestedCredentialSet(required = true, options = listOf(queryTypes.toList())))

    private fun unmatched(requestedCredentialSets: List<RequestedCredentialSet>?) =
        TransferEventPartialState.RequestReceived(
            requestData = emptyList(),
            verifierName = "Verifier",
            verifierIsTrusted = true,
            relyingPartyRegistration = RelyingPartyRegistrationStatus.NotEvaluated,
            requestedCredentialSets = requestedCredentialSets,
        )

    private companion object {
        const val PID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
        const val PID_VCT = "urn:eudi:pid:1"
        const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
    }
}
