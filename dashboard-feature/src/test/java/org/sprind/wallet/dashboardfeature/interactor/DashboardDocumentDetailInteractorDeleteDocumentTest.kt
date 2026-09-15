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

package org.sprind.wallet.dashboardfeature.interactor

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.DeleteDocumentPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.extension.toFlow
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType

class DashboardDocumentDetailInteractorDeleteDocumentTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var rwscaInteractor: RwscaInteractor

    private lateinit var closeable: AutoCloseable
    private lateinit var interactor: DashboardDocumentDetailInteractor

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.genericErrorMessage()).thenReturn(mockedGenericErrorMessage)

        interactor = DashboardDocumentDetailInteractorImpl(
            walletCoreDocumentsController = walletCoreDocumentsController,
            resourceProvider = resourceProvider,
            logController = logController,
            rwscaInteractor = rwscaInteractor,
        )
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `Given PID has mdoc and sdjwt formats and EAA exists, When PID is deleted from dashboard details, Then only PID documents are deleted`() {
        coroutineRule.runTest {
            val mdocPidDocumentId = "mdoc-pid-document-id"
            val sdJwtPidDocumentId = "sdjwt-pid-document-id"
            val mdocPidDocument = issuedDocument(
                documentId = mdocPidDocumentId,
                format = MsoMdocFormat(DocumentIdentifier.MdocPid.formatType)
            )
            val sdJwtPidDocument = issuedDocument(
                documentId = sdJwtPidDocumentId,
                format = SdJwtVcFormat(DocumentIdentifier.SdJwtPid.formatType)
            )

            whenever(walletCoreDocumentsController.getDocumentById(mdocPidDocumentId))
                .thenReturn(mdocPidDocument)
            whenever(
                walletCoreDocumentsController.getAllDocumentsByType(
                    listOf(DocumentIdentifier.MdocPid, DocumentIdentifier.SdJwtPid)
                )
            ).thenReturn(listOf(mdocPidDocument, sdJwtPidDocument))
            whenever(walletCoreDocumentsController.deleteDocument(mdocPidDocumentId))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(walletCoreDocumentsController.deleteDocument(sdJwtPidDocumentId))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(rwscaInteractor.deleteAccount()).thenReturn(ApiResult.Success(Unit))

            interactor.deleteDocument(mdocPidDocumentId).runFlowTest {
                assertEquals(
                    DashboardDocumentDetailDeleteDocumentPartialState.Success,
                    awaitItem()
                )
            }

            verify(rwscaInteractor).deleteAccount()
            verify(walletCoreDocumentsController).deleteDocument(mdocPidDocumentId)
            verify(walletCoreDocumentsController).deleteDocument(sdJwtPidDocumentId)
            verify(walletCoreDocumentsController, never()).deleteAllDocuments()
        }
    }

    @Test
    fun `Given RWSCA delete fails, When PID is deleted from dashboard details, Then local PID documents are not deleted`() {
        coroutineRule.runTest {
            val mdocPidDocumentId = "mdoc-pid-document-id"
            val mdocPidDocument = issuedDocument(
                documentId = mdocPidDocumentId,
                format = MsoMdocFormat(DocumentIdentifier.MdocPid.formatType)
            )

            whenever(walletCoreDocumentsController.getDocumentById(mdocPidDocumentId))
                .thenReturn(mdocPidDocument)
            whenever(rwscaInteractor.deleteAccount()).thenReturn(rwscaServerFailure())

            interactor.deleteDocument(mdocPidDocumentId).runFlowTest {
                assertEquals(
                    DashboardDocumentDetailDeleteDocumentPartialState.Failure(
                        error = RwscaErrorType.INTERNAL_SERVER_ERROR.code
                    ),
                    awaitItem()
                )
            }

            verify(rwscaInteractor).deleteAccount()
            verify(walletCoreDocumentsController, never()).deleteDocument(mdocPidDocumentId)
        }
    }

    @Test
    fun `Given second PID document deletion fails, When PID is deleted from dashboard details, Then failure is emitted and remaining PID documents are not deleted`() {
        coroutineRule.runTest {
            val mdocPidDocumentId = "mdoc-pid-document-id"
            val sdJwtPidDocumentId = "sdjwt-pid-document-id"
            val remainingPidDocumentId = "remaining-pid-document-id"
            val mdocPidDocument = issuedDocument(
                documentId = mdocPidDocumentId,
                format = MsoMdocFormat(DocumentIdentifier.MdocPid.formatType)
            )
            val sdJwtPidDocument = issuedDocument(
                documentId = sdJwtPidDocumentId,
                format = SdJwtVcFormat(DocumentIdentifier.SdJwtPid.formatType)
            )
            val remainingPidDocument = issuedDocument(
                documentId = remainingPidDocumentId,
                format = MsoMdocFormat(DocumentIdentifier.MdocPid.formatType)
            )
            val failureMessage = "delete failed"

            whenever(walletCoreDocumentsController.getDocumentById(mdocPidDocumentId))
                .thenReturn(mdocPidDocument)
            whenever(
                walletCoreDocumentsController.getAllDocumentsByType(
                    listOf(DocumentIdentifier.MdocPid, DocumentIdentifier.SdJwtPid)
                )
            ).thenReturn(listOf(mdocPidDocument, sdJwtPidDocument, remainingPidDocument))
            whenever(walletCoreDocumentsController.deleteDocument(mdocPidDocumentId))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(walletCoreDocumentsController.deleteDocument(sdJwtPidDocumentId))
                .thenReturn(DeleteDocumentPartialState.Failure(failureMessage).toFlow())
            whenever(rwscaInteractor.deleteAccount()).thenReturn(ApiResult.Success(Unit))

            interactor.deleteDocument(mdocPidDocumentId).runFlowTest {
                assertEquals(
                    DashboardDocumentDetailDeleteDocumentPartialState.Failure(
                        error = failureMessage
                    ),
                    awaitItem()
                )
            }

            verify(walletCoreDocumentsController).deleteDocument(mdocPidDocumentId)
            verify(walletCoreDocumentsController).deleteDocument(sdJwtPidDocumentId)
            verify(walletCoreDocumentsController, never()).deleteDocument(remainingPidDocumentId)
        }
    }

    @Test
    fun `Given EAA document, When EAA is deleted from dashboard details, Then only selected document is deleted without RWSCA cleanup`() {
        coroutineRule.runTest {
            val eaaDocumentId = "eaa-document-id"
            val eaaDocument = issuedDocument(
                documentId = eaaDocumentId,
                format = MsoMdocFormat("eaa-doc-type")
            )

            whenever(walletCoreDocumentsController.getDocumentById(eaaDocumentId))
                .thenReturn(eaaDocument)
            whenever(walletCoreDocumentsController.deleteDocument(eaaDocumentId))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())

            interactor.deleteDocument(eaaDocumentId).runFlowTest {
                assertEquals(
                    DashboardDocumentDetailDeleteDocumentPartialState.Success,
                    awaitItem()
                )
            }

            verify(rwscaInteractor, never()).deleteAccount()
            verify(walletCoreDocumentsController).deleteDocument(eaaDocumentId)
            verify(walletCoreDocumentsController, never()).deleteAllDocuments()
        }
    }

    @Test
    fun `Given PID exists without local RWSCA account, When PID is deleted from dashboard details, Then PID documents are still deleted`() {
        coroutineRule.runTest {
            val mdocPidDocumentId = "mdoc-pid-document-id"
            val mdocPidDocument = issuedDocument(
                documentId = mdocPidDocumentId,
                format = MsoMdocFormat(DocumentIdentifier.MdocPid.formatType)
            )

            whenever(walletCoreDocumentsController.getDocumentById(mdocPidDocumentId))
                .thenReturn(mdocPidDocument)
            whenever(
                walletCoreDocumentsController.getAllDocumentsByType(
                    listOf(DocumentIdentifier.MdocPid, DocumentIdentifier.SdJwtPid)
                )
            ).thenReturn(listOf(mdocPidDocument))
            whenever(walletCoreDocumentsController.deleteDocument(mdocPidDocumentId))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(rwscaInteractor.deleteAccount()).thenReturn(accountNotFoundLocallyFailure())

            interactor.deleteDocument(mdocPidDocumentId).runFlowTest {
                assertEquals(
                    DashboardDocumentDetailDeleteDocumentPartialState.Success,
                    awaitItem()
                )
            }

            verify(rwscaInteractor).deleteAccount()
            verify(walletCoreDocumentsController).deleteDocument(mdocPidDocumentId)
        }
    }

    private fun issuedDocument(
        documentId: String,
        format: eu.europa.ec.eudi.wallet.document.format.DocumentFormat,
    ): IssuedDocument =
        mock {
            whenever(it.id).thenReturn(documentId)
            whenever(it.format).thenReturn(format)
        }

    private fun accountNotFoundLocallyFailure() = ApiResult.Failure(
        RwscaError.FromRwsca(
            type = RwscaErrorType.ACCOUNT_NOT_FOUND_LOCALLY,
            serverResponse = null,
        )
    )

    private fun rwscaServerFailure() = ApiResult.Failure(
        RwscaError.FromRwsca(
            type = RwscaErrorType.INTERNAL_SERVER_ERROR,
            serverResponse = RwscaErrorResponse(code = RwscaErrorType.INTERNAL_SERVER_ERROR.code),
        )
    )
}
