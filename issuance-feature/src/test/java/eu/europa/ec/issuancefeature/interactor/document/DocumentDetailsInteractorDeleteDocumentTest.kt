/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.issuancefeature.interactor.document

import eu.europa.ec.corelogic.controller.DeleteDocumentPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.getMockedMdlWithBasicFields
import eu.europa.ec.testfeature.getMockedOldestPidWithBasicFields
import eu.europa.ec.testfeature.getMockedPidWithBasicFields
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testfeature.mockedMdlId
import eu.europa.ec.testfeature.mockedOldestPidId
import eu.europa.ec.testfeature.mockedPidId
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
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType
import java.util.Locale

class DocumentDetailsInteractorDeleteDocumentTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var rwscaInteractor: RwscaInteractor

    private lateinit var interactor: DocumentDetailsInteractor
    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        interactor = DocumentDetailsInteractorImpl(
            walletCoreDocumentsController = walletCoreDocumentsController,
            resourceProvider = resourceProvider,
            rwscaInteractor = rwscaInteractor,
        )

        whenever(resourceProvider.genericErrorMessage()).thenReturn(mockedGenericErrorMessage)
        whenever(resourceProvider.getLocale()).thenReturn(Locale.getDefault())
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given mdoc and sdjwt PID documents, When deleteDocument is called for PID, Then all PID documents are deleted`() {
        coroutineRule.runTest {
            val mainPidDocument = getMockedOldestPidWithBasicFields()
            val otherPidDocument = getMockedPidWithBasicFields()

            whenever(walletCoreDocumentsController.getDocumentById(mockedOldestPidId))
                .thenReturn(mainPidDocument)
            whenever(
                walletCoreDocumentsController.getAllDocumentsByType(
                    listOf(DocumentIdentifier.MdocPid, DocumentIdentifier.SdJwtPid)
                )
            ).thenReturn(
                listOf(
                    otherPidDocument,
                    mainPidDocument
                )
            )
            whenever(walletCoreDocumentsController.getMainPidDocument()).thenReturn(mainPidDocument)
            whenever(walletCoreDocumentsController.deleteDocument(mockedOldestPidId))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(walletCoreDocumentsController.deleteDocument(mockedPidId))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(rwscaInteractor.deleteAccount()).thenReturn(ApiResult.Success(Unit))

            interactor.deleteDocument(documentId = mockedOldestPidId).runFlowTest {
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted,
                    awaitItem()
                )
            }

            verify(rwscaInteractor).deleteAccount()
            verify(walletCoreDocumentsController).deleteDocument(mockedOldestPidId)
            verify(walletCoreDocumentsController).deleteDocument(mockedPidId)
            verify(walletCoreDocumentsController, never()).deleteAllDocuments()
        }
    }

    @Test
    fun `Given RWSCA delete fails, When deleteDocument is called for PID, Then local PID documents are not deleted`() {
        coroutineRule.runTest {
            val mainPidDocument = getMockedOldestPidWithBasicFields()

            whenever(walletCoreDocumentsController.getDocumentById(mockedOldestPidId))
                .thenReturn(mainPidDocument)
            whenever(rwscaInteractor.deleteAccount()).thenReturn(rwscaServerFailure())

            interactor.deleteDocument(documentId = mockedOldestPidId).runFlowTest {
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                        errorMessage = RwscaErrorType.INTERNAL_SERVER_ERROR.code
                    ),
                    awaitItem()
                )
            }

            verify(rwscaInteractor).deleteAccount()
            verify(walletCoreDocumentsController, never()).deleteDocument(mockedOldestPidId)
        }
    }

    @Test
    fun `Given second PID document deletion fails, When deleteDocument is called for PID, Then failure is emitted and remaining PID documents are not deleted`() {
        coroutineRule.runTest {
            val mainPidDocument = getMockedOldestPidWithBasicFields()
            val otherPidDocument = getMockedPidWithBasicFields()
            val remainingPidDocumentId = "remaining-pid-document-id"
            val remainingPidDocument = org.mockito.kotlin.mock<eu.europa.ec.eudi.wallet.document.IssuedDocument> {
                whenever(it.id).thenReturn(remainingPidDocumentId)
            }
            val failureMessage = "delete failed"

            whenever(walletCoreDocumentsController.getDocumentById(mockedOldestPidId))
                .thenReturn(mainPidDocument)
            whenever(
                walletCoreDocumentsController.getAllDocumentsByType(
                    listOf(DocumentIdentifier.MdocPid, DocumentIdentifier.SdJwtPid)
                )
            ).thenReturn(listOf(mainPidDocument, otherPidDocument, remainingPidDocument))
            whenever(walletCoreDocumentsController.deleteDocument(mockedOldestPidId))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(walletCoreDocumentsController.deleteDocument(mockedPidId))
                .thenReturn(DeleteDocumentPartialState.Failure(failureMessage).toFlow())
            whenever(rwscaInteractor.deleteAccount()).thenReturn(ApiResult.Success(Unit))

            interactor.deleteDocument(documentId = mockedOldestPidId).runFlowTest {
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                        errorMessage = failureMessage
                    ),
                    awaitItem()
                )
            }

            verify(walletCoreDocumentsController).deleteDocument(mockedOldestPidId)
            verify(walletCoreDocumentsController).deleteDocument(mockedPidId)
            verify(walletCoreDocumentsController, never()).deleteDocument(remainingPidDocumentId)
        }
    }

    @Test
    fun `Given EAA document, When deleteDocument is called for EAA, Then only selected document is deleted without RWSCA cleanup`() {
        coroutineRule.runTest {
            val eaaDocument = getMockedMdlWithBasicFields()

            whenever(walletCoreDocumentsController.getDocumentById(mockedMdlId))
                .thenReturn(eaaDocument)
            whenever(walletCoreDocumentsController.deleteDocument(mockedMdlId))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())

            interactor.deleteDocument(documentId = mockedMdlId).runFlowTest {
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted,
                    awaitItem()
                )
            }

            verify(rwscaInteractor, never()).deleteAccount()
            verify(walletCoreDocumentsController).deleteDocument(mockedMdlId)
            verify(walletCoreDocumentsController, never()).deleteAllDocuments()
        }
    }

    @Test
    fun `Given PID exists without local RWSCA account, When deleteDocument is called for PID, Then PID documents are still deleted`() {
        coroutineRule.runTest {
            val mainPidDocument = getMockedOldestPidWithBasicFields()

            whenever(walletCoreDocumentsController.getDocumentById(mockedOldestPidId))
                .thenReturn(mainPidDocument)
            whenever(
                walletCoreDocumentsController.getAllDocumentsByType(
                    listOf(DocumentIdentifier.MdocPid, DocumentIdentifier.SdJwtPid)
                )
            ).thenReturn(listOf(mainPidDocument))
            whenever(walletCoreDocumentsController.deleteDocument(mockedOldestPidId))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(rwscaInteractor.deleteAccount()).thenReturn(accountNotFoundLocallyFailure())

            interactor.deleteDocument(documentId = mockedOldestPidId).runFlowTest {
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted,
                    awaitItem()
                )
            }

            verify(rwscaInteractor).deleteAccount()
            verify(walletCoreDocumentsController).deleteDocument(mockedOldestPidId)
        }
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
