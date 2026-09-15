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

package org.sprind.wallet.presentationfeature.interactor

import eu.europa.ec.authenticationlogic.controller.storage.WalletPinUnBlockTimeStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.DeleteDocumentPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.presentationfeature.interactor.PresentationRequestDeleteDocumentPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorImpl
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
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType

class PresentationRequestDeleteDocumentsTest {

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

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        interactor = PresentationRequestInteractorImpl(
            resourceProvider = resourceProvider,
            walletCorePresentationController = walletCorePresentationController,
            walletCoreDocumentsController = walletCoreDocumentsController,
            walletPinUnBlockTimeStorageController = walletPinUnBlockTimeStorageController,
            logController = logController,
            rwscaInteractor = rwscaInteractor,
        )
        whenever(resourceProvider.genericErrorMessage()).thenReturn(mockedGenericErrorMessage)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given PID and EAA documents, When deleting from PID presentation flow, Then only PID documents are deleted`() {
        coroutineRule.runTest {
            val mdocPid = issuedDocument("mdoc-pid")
            val sdJwtPid = issuedDocument("sdjwt-pid")

            whenever(
                walletCoreDocumentsController.getAllDocumentsByType(
                    listOf(DocumentIdentifier.MdocPid, DocumentIdentifier.SdJwtPid)
                )
            ).thenReturn(listOf(mdocPid, sdJwtPid))
            whenever(walletCoreDocumentsController.deleteDocument("mdoc-pid"))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(walletCoreDocumentsController.deleteDocument("sdjwt-pid"))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(rwscaInteractor.deleteAccount()).thenReturn(ApiResult.Success(Unit))

            interactor.deletePidDocuments().runFlowTest {
                assertEquals(
                    PresentationRequestDeleteDocumentPartialState.Success,
                    awaitItem()
                )
            }

            verify(rwscaInteractor).deleteAccount()
            verify(walletCoreDocumentsController).deleteDocument("mdoc-pid")
            verify(walletCoreDocumentsController).deleteDocument("sdjwt-pid")
            verify(walletCoreDocumentsController).resetPidIssuanceState()
            verify(walletCoreDocumentsController, never()).deleteAllDocuments()
            verify(walletPinUnBlockTimeStorageController).clear()
        }
    }

    @Test
    fun `Given RWSCA delete fails, When deleting from PID presentation flow, Then local PID documents are not deleted and unblock time is not cleared`() {
        coroutineRule.runTest {
            whenever(rwscaInteractor.deleteAccount()).thenReturn(rwscaServerFailure())

            interactor.deletePidDocuments().runFlowTest {
                assertEquals(
                    PresentationRequestDeleteDocumentPartialState.Failure(
                        error = RwscaErrorType.INTERNAL_SERVER_ERROR.code
                    ),
                    awaitItem()
                )
            }

            verify(rwscaInteractor).deleteAccount()
            verify(walletCoreDocumentsController, never()).deleteDocument("mdoc-pid")
            verify(walletCoreDocumentsController, never()).resetPidIssuanceState()
            verify(walletPinUnBlockTimeStorageController, never()).clear()
        }
    }

    @Test
    fun `Given second PID document deletion fails, When deleting from PID presentation flow, Then failure is emitted and remaining PID documents are not deleted`() {
        coroutineRule.runTest {
            val mdocPid = issuedDocument("mdoc-pid")
            val sdJwtPid = issuedDocument("sdjwt-pid")
            val remainingPid = issuedDocument("remaining-pid")
            val failureMessage = "delete failed"

            whenever(
                walletCoreDocumentsController.getAllDocumentsByType(
                    listOf(DocumentIdentifier.MdocPid, DocumentIdentifier.SdJwtPid)
                )
            ).thenReturn(listOf(mdocPid, sdJwtPid, remainingPid))
            whenever(walletCoreDocumentsController.deleteDocument("mdoc-pid"))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(walletCoreDocumentsController.deleteDocument("sdjwt-pid"))
                .thenReturn(DeleteDocumentPartialState.Failure(failureMessage).toFlow())
            whenever(rwscaInteractor.deleteAccount()).thenReturn(ApiResult.Success(Unit))

            interactor.deletePidDocuments().runFlowTest {
                assertEquals(
                    PresentationRequestDeleteDocumentPartialState.Failure(
                        error = failureMessage
                    ),
                    awaitItem()
                )
            }

            verify(walletCoreDocumentsController).deleteDocument("mdoc-pid")
            verify(walletCoreDocumentsController).deleteDocument("sdjwt-pid")
            verify(walletCoreDocumentsController, never()).deleteDocument("remaining-pid")
            verify(walletCoreDocumentsController, never()).resetPidIssuanceState()
            verify(walletPinUnBlockTimeStorageController, never()).clear()
        }
    }

    @Test
    fun `Given PID exists without local RWSCA account, When deleting from PID presentation flow, Then PID documents are still deleted`() {
        coroutineRule.runTest {
            val mdocPid = issuedDocument("mdoc-pid")

            whenever(
                walletCoreDocumentsController.getAllDocumentsByType(
                    listOf(DocumentIdentifier.MdocPid, DocumentIdentifier.SdJwtPid)
                )
            ).thenReturn(listOf(mdocPid))
            whenever(walletCoreDocumentsController.deleteDocument("mdoc-pid"))
                .thenReturn(DeleteDocumentPartialState.Success.toFlow())
            whenever(rwscaInteractor.deleteAccount()).thenReturn(accountNotFoundLocallyFailure())

            interactor.deletePidDocuments().runFlowTest {
                assertEquals(
                    PresentationRequestDeleteDocumentPartialState.Success,
                    awaitItem()
                )
            }

            verify(rwscaInteractor).deleteAccount()
            verify(walletCoreDocumentsController).deleteDocument("mdoc-pid")
            verify(walletCoreDocumentsController).resetPidIssuanceState()
            verify(walletPinUnBlockTimeStorageController).clear()
        }
    }

    private fun issuedDocument(documentId: String): IssuedDocument =
        mock {
            whenever(it.id).thenReturn(documentId)
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
