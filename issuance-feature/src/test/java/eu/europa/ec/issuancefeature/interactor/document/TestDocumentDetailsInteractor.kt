/*
 * Copyright (c) 2023 European Commission
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

import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.corelogic.controller.DeleteAllDocumentsPartialState
import eu.europa.ec.corelogic.controller.DeleteDocumentPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.getMockedMdlWithBasicFields
import eu.europa.ec.testfeature.getMockedOldestPidWithBasicFields
import eu.europa.ec.testfeature.getMockedPidWithBasicFields
import eu.europa.ec.testfeature.mockedBookmarkId
import eu.europa.ec.testfeature.mockedExceptionWithMessage
import eu.europa.ec.testfeature.mockedExceptionWithNoMessage
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testfeature.mockedMdlId
import eu.europa.ec.testfeature.mockedOldestPidId
import eu.europa.ec.testfeature.mockedPidId
import eu.europa.ec.testfeature.mockedPlainFailureMessage
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.extension.toFlow
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import java.util.Locale

@Ignore("To be updated after integrating our changes")
class TestDocumentDetailsInteractor {

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
        whenever(resourceProvider.getLocale()).thenReturn(mockedDefaultLocale)
    }

    @After
    fun after() {
        closeable.close()
    }

    //region getDocumentDetails

    // Case 1:
    // 1. walletCoreDocumentsController.getDocumentById() throws an exception with a message.

    // Case 1 Expected Result:
    // DocumentDetailsInteractorPartialState.Failed state,
    // with the exception's localized message.
    @Test
    fun `Given Case 1, When getDocumentDetails is called, Then Case 1 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            whenever(walletCoreDocumentsController.getDocumentById(mockedPidId))
                .thenThrow(mockedExceptionWithMessage)

            // When
            interactor.getDocumentDetails(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Failure(
                        error = mockedExceptionWithMessage.localizedMessage!!
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 2:
    // 1. walletCoreDocumentsController.getDocumentById() throws an exception with no message.

    // Case 2 Expected Result:
    // DocumentDetailsInteractorPartialState.Failed state,
    // with the generic error message.
    @Test
    fun `Given Case 2, When getDocumentDetails is called, Then Case 2 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            whenever(walletCoreDocumentsController.getDocumentById(mockedPidId))
                .thenThrow(mockedExceptionWithNoMessage)

            // When
            interactor.getDocumentDetails(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Failure(
                        error = mockedGenericErrorMessage
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 3:
    // 1. walletCoreDocumentsController.getDocumentById() returns null.

    // Case 3 Expected Result:
    // DocumentDetailsInteractorPartialState.Failed state,
    // with the generic error message.
    @Test
    fun `Given Case 3, When getDocumentDetails is called, Then Case 3 Expected Result is returned`() {
        coroutineRule.runTest {
            // Given
            mockGetDocumentByIdCall(response = null)

            // When
            interactor.getDocumentDetails(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorPartialState.Failure(
                        error = mockedGenericErrorMessage
                    ),
                    awaitItem()
                )
            }
        }
    }
    //endregion

    //region deleteDocument

    // Case 1:

    // 1. A documentId and document is PID.
    // 2. walletCoreDocumentsController.getAllDocuments() returns 1 Document and it is PID.
    // 3. walletCoreDocumentsController.getDocumentById returns that PID Document.
    // 4. walletCoreDocumentsController.deleteAllDocuments() returns Failed.
    @Test
    fun `Given Case 1, When deleteDocument is called, Then it returns Failure with failure's error message`() {
        coroutineRule.runTest {
            // Given
            mockGetAllDocumentsCall(
                response = listOf(
                    getMockedPidWithBasicFields()
                )
            )
            mockDeleteAllDocumentsCall(
                response = DeleteAllDocumentsPartialState.Failure(
                    errorMessage = mockedPlainFailureMessage
                )
            )
            mockGetDocumentByIdCall(
                response = getMockedPidWithBasicFields()
            )

            // When
            interactor.deleteDocument(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                        errorMessage = mockedPlainFailureMessage
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 2:

    // 1. A documentId and document is PID.
    // 2. walletCoreDocumentsController.getAllDocuments() returns 1 Document and it is PID.
    // 3. walletCoreDocumentsController.getDocumentById returns that PID Document.
    // 4. walletCoreDocumentsController.deleteAllDocuments() returns Success.
    @Test
    fun `Given Case 2, When deleteDocument is called, Then it returns SingleDocumentDeleted`() {
        coroutineRule.runTest {
            // Given
            mockGetAllDocumentsCall(
                response = listOf(
                    getMockedPidWithBasicFields()
                )
            )
            mockDeleteAllDocumentsCall(response = DeleteAllDocumentsPartialState.Success)
            mockGetDocumentByIdCall(
                response = getMockedPidWithBasicFields()
            )

            // When
            interactor.deleteDocument(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted,
                    awaitItem()
                )
            }
        }
    }

    // Case 3:

    // 1. A documentId and document is PID.
    // 2. walletCoreDocumentsController.getAllDocuments() returns more than 1 PIDs
    // 3. walletCoreDocumentsController.getDocumentById returns the oldest Document.
    // 4. walletCoreDocumentsController.deleteAllDocuments() returns Success.
    @Test
    fun `Given Case 3, When deleteDocument is called, Then it returns SingleDocumentDeleted`() {
        coroutineRule.runTest {
            // Given
            mockGetAllDocumentsCall(
                response = listOf(
                    getMockedMdlWithBasicFields(),
                    getMockedPidWithBasicFields(),
                    getMockedOldestPidWithBasicFields()
                )
            )
            mockDeleteAllDocumentsCall(response = DeleteAllDocumentsPartialState.Success)
            mockGetDocumentByIdCall(
                response = getMockedOldestPidWithBasicFields()
            )

            // When
            interactor.deleteDocument(
                documentId = mockedOldestPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted,
                    awaitItem()
                )
            }
        }
    }

    // Case 4:

    // 1. A documentId and document is PID.
    // 2. walletCoreDocumentsController.getAllDocuments(documentIdentifier: DocumentIdentifier) returns more than 1 PIDs
    //      AND the documentId we are about to delete is NOT the one of the oldest PID.
    // 3. walletCoreDocumentsController.deleteDocument() returns Success.
    @Test
    fun `Given Case 4, When deleteDocument is called, Then it returns SingleDocumentDeleted`() {
        coroutineRule.runTest {
            // Given
            mockGetAllDocumentsWithTypeCall(
                response = listOf(
                    getMockedPidWithBasicFields(),
                    getMockedOldestPidWithBasicFields()
                )
            )
            mockDeleteDocumentCall(response = DeleteDocumentPartialState.Success)
            mockGetMainPidDocument(getMockedOldestPidWithBasicFields())

            // When
            interactor.deleteDocument(
                documentId = mockedPidId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted,
                    awaitItem()
                )
            }
        }
    }

    // Case 5:

    // 1. A documentId and document is mDL.
    // 2. walletCoreDocumentsController.deleteDocument() returns Failed.
    @Test
    fun `Given Case 5, When deleteDocument is called, Then it returns Failure with failure's error message`() {
        coroutineRule.runTest {
            // Given
            mockDeleteDocumentCall(
                response = DeleteDocumentPartialState.Failure(
                    errorMessage = mockedPlainFailureMessage
                )
            )

            // When
            interactor.deleteDocument(
                documentId = mockedMdlId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                        errorMessage = mockedPlainFailureMessage
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 6:

    // 1. A documentId and document is mDL.
    // 2. walletCoreDocumentsController.deleteDocument() returns Success.
    @Test
    fun `Given Case 6, When deleteDocument is called, Then it returns SingleDocumentDeleted`() {
        coroutineRule.runTest {
            // Given
            mockDeleteDocumentCall(response = DeleteDocumentPartialState.Success)

            // When
            interactor.deleteDocument(
                documentId = mockedMdlId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted,
                    awaitItem()
                )
            }
        }
    }

    // Case 7:

    // 1. A documentId and document is mDL.
    // 2. walletCoreDocumentsController.deleteDocument() throws an exception with a message.
    @Test
    fun `Given Case 7, When deleteDocument is called, Then it returns Failure with the exception's localized message`() {
        coroutineRule.runTest {
            // Given
            whenever(walletCoreDocumentsController.deleteDocument(mockedMdlId))
                .thenThrow(mockedExceptionWithMessage)

            // When
            interactor.deleteDocument(
                documentId = mockedMdlId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                        errorMessage = mockedExceptionWithMessage.localizedMessage!!
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 8:

    // 1. A documentId and document is mDL.
    // 2. walletCoreDocumentsController.deleteDocument() throws an exception with no message.
    @Test
    fun `Given Case 8, When deleteDocument is called, Then it returns Failure with the generic error message`() {
        coroutineRule.runTest {
            // Given
            whenever(walletCoreDocumentsController.deleteDocument(mockedMdlId))
                .thenThrow(mockedExceptionWithNoMessage)

            // When
            interactor.deleteDocument(
                documentId = mockedMdlId,
            ).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                        errorMessage = mockedGenericErrorMessage
                    ),
                    awaitItem()
                )
            }
        }
    }
    //endregion

    //region storeBookmark()
    // Case 1:
    // 1. A valid bookmarkId is provided.
    // 2. bookmarkStorageController.store() succeeds.
    // Expected result:
    // DocumentDetailsInteractorStoreBookmarkPartialState.Success state is returned with the bookmarkId.
    @Test
    fun `Given Case 1, When storeBookmark is called, Then the expected result is returned`() {
        coroutineRule.runTest {
            // Arrange

            // Act
            interactor.storeBookmark(bookmarkId = mockedBookmarkId).runFlowTest {
                // Assert
                assertEquals(
                    DocumentDetailsInteractorStoreBookmarkPartialState.Success(
                        bookmarkId = mockedBookmarkId
                    ),
                    awaitItem()
                )
            }
        }
    }

    // Case 2:
    // 1. A valid bookmarkId is provided.
    // 2. When bookmarkStorageController.store() is called, an exception is thrown.
    // Expected result:
    // DocumentDetailsInteractorStoreBookmarkPartialState.Failure state is returned.
    @Test
    fun `Given Case 2, When storeBookmark is called, Then the expected result is returned`() {
        coroutineRule.runTest {
            // Arrange

            // Act
            interactor.storeBookmark(bookmarkId = mockedBookmarkId).runFlowTest {
                // Assert
                assertEquals(
                    DocumentDetailsInteractorStoreBookmarkPartialState.Failure,
                    awaitItem()
                )
            }
        }
    }
    //endregion

    //region deleteBookmark()
    // Case 1:
    // 1. A valid bookmarkId is provided.
    // 2. bookmarkStorageController.delete() succeeds.
    // Expected result:
    // DocumentDetailsInteractorDeleteBookmarkPartialState.Success state is returned.
    @Test
    fun `Given Case 1, When deleteBookmark is called, Then the expected result is returned`() {
        coroutineRule.runTest {
            // Arrange

            // Act
            interactor.deleteBookmark(bookmarkId = mockedBookmarkId).runFlowTest {
                // Assert
                assertEquals(
                    DocumentDetailsInteractorDeleteBookmarkPartialState.Success,
                    awaitItem()
                )
            }
        }
    }

    // Case 2:
    // 1. A valid bookmarkId is provided for the bookmark to be deleted.
    // 2. When bookmarkStorageController.delete() is called, an exception is thrown.
    // Expected result:
    // DocumentDetailsInteractorDeleteBookmarkPartialState.Failure state is returned.
    @Test
    fun `Given Case 2, When deleteBookmark is called, Then the expected result is returned`() {
        coroutineRule.runTest {
            // Given

            // When
            interactor.deleteBookmark(bookmarkId = mockedBookmarkId).runFlowTest {
                // Then
                assertEquals(
                    DocumentDetailsInteractorDeleteBookmarkPartialState.Failure,
                    awaitItem()
                )
            }
        }
    }
    //endregion

    //region helper functions
    private fun mockGetAllDocumentsCall(response: List<IssuedDocument>) {
        whenever(walletCoreDocumentsController.getAllDocuments())
            .thenReturn(response)
    }

    private fun mockGetAllDocumentsWithTypeCall(response: List<IssuedDocument>) {
        whenever(walletCoreDocumentsController.getAllDocumentsByType(documentIdentifiers = any()))
            .thenReturn(response)
    }

    private fun mockGetDocumentByIdCall(response: IssuedDocument?) {
        whenever(walletCoreDocumentsController.getDocumentById(anyString()))
            .thenReturn(response)
    }

    private fun mockGetMainPidDocument(response: IssuedDocument?) {
        whenever(walletCoreDocumentsController.getMainPidDocument())
            .thenReturn(response)
    }

    private fun mockDeleteAllDocumentsCall(response: DeleteAllDocumentsPartialState) {
        whenever(walletCoreDocumentsController.deleteAllDocuments())
            .thenReturn(response.toFlow())
    }

    private fun mockDeleteDocumentCall(response: DeleteDocumentPartialState) {
        whenever(walletCoreDocumentsController.deleteDocument(anyString()))
            .thenReturn(response.toFlow())
    }

    //region mocked locale data
    private val mockedDefaultLocale = Locale.getDefault()
    //endregion
}
