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

package eu.europa.ec.presentationfeature.interactor

import eu.europa.ec.authenticationlogic.controller.storage.WalletPinUnBlockTimeStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.toDomainConfig
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.commonfeature.ui.request.transformer.RequestTransformer
import eu.europa.ec.corelogic.controller.SendRequestedDocumentsPartialState
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePartialState
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.corelogic.securearea.exception.RwscaServerException
import eu.europa.ec.eudi.iso18013.transfer.response.ReaderAuth
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.sprind.wallet.commonfeature.interactor.DocumentDeletionPartialState
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.commonfeature.interactor.deletePidDocumentsWithRwscaCleanup
import java.net.URI

sealed class PresentationRequestInteractorPartialState {
    data class Success(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
        val requestDocuments: List<RequestDocumentItemUi>,
        val readerAuth: ReaderAuth?,
    ) : PresentationRequestInteractorPartialState()

    data class NoData(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
    ) : PresentationRequestInteractorPartialState()

    data class Failure(val error: String) : PresentationRequestInteractorPartialState()
    data object Disconnect : PresentationRequestInteractorPartialState()
}

sealed class PresentationRequestProcessPartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : PresentationRequestProcessPartialState()

    data class Failure(val error: String) : PresentationRequestProcessPartialState()
    data object Success : PresentationRequestProcessPartialState()
    data class Redirect(val uri: URI) : PresentationRequestProcessPartialState()
    data object RequestReadyToBeSent : PresentationRequestProcessPartialState()
}

sealed class PresentationDocumentSubmissionPartialState {
    sealed class Failure : PresentationDocumentSubmissionPartialState() {
        data class PinVerificationBlocked(val iso8601utcTime: String, val triesRemaining: Int) :
            Failure()

        data class WrongPin(val triesRemaining: Int, val iso8601utcTime: String) : Failure()
        data object AccountLocked : Failure()
        /**
         * @property errorCode The recognised code, used to pick the message shown to the user.
         * @property backendErrorCode The code exactly as the backend reported it, so that a code
         * this app does not recognise is still shown to the user instead of being flattened to
         * [ServerErrorCode.UNKNOWN].
         */
        data class ServerError(
            val errorCode: ServerErrorCode,
            val traceId: String? = null,
            val backendErrorCode: String = errorCode.name,
        ) : Failure()
        data object Unknown : Failure()

        enum class ServerErrorCode {
            RWSCD_ACCOUNT_UNKNOWN,
            RWSCD_AUTH_VERIFICATION_FAILED,
            UNKNOWN
        }

    }

    data object Success : PresentationDocumentSubmissionPartialState()
}

sealed class PresentationRequestDeleteDocumentPartialState {
    data object Success : PresentationRequestDeleteDocumentPartialState()
    data class Failure(val error: String) : PresentationRequestDeleteDocumentPartialState()
}

interface PresentationRequestInteractor {
    val initiatorRoute: String
    fun getRequestDocuments(): Flow<PresentationRequestInteractorPartialState>
    fun stopPresentation()
    fun updateRequestedDocuments(items: List<RequestDocumentItemUi>)
    fun setConfig(config: RequestUriConfig)
    fun processRequest(): Flow<PresentationRequestProcessPartialState>
    fun sendRequestedDocuments(): PresentationDocumentSubmissionPartialState
    fun deletePidDocuments(): Flow<PresentationRequestDeleteDocumentPartialState>
    fun getWalletPinBlockTime(): String
    fun isLastPinTry(): Boolean

    /**
     * Refills the one-time-use credential batch (spec step 043) after a presentation that completes
     * on the request screen (PID/RWSCA). Must be called while the rWSCA PIN session is still open.
     *
     * @return an error message if the refresh failed, or `null` if it succeeded or was not needed.
     * A failure is non-disruptive: the caller should log it but still complete the presentation.
     */
    suspend fun reissueLowBatchDocumentsIfNeeded(): String?
}

class PresentationRequestInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val walletCorePresentationController: WalletCorePresentationController,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val walletPinUnBlockTimeStorageController: WalletPinUnBlockTimeStorageController,
    private val logController: LogController,
    private val rwscaInteractor: RwscaInteractor,
) : PresentationRequestInteractor {

    private val logTag = this::class.java.simpleName

    override val initiatorRoute: String
        get() = walletCorePresentationController.initiatorRoute

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun setConfig(config: RequestUriConfig) {
        walletCorePresentationController.setConfig(config.toDomainConfig())
    }

    override fun getRequestDocuments(): Flow<PresentationRequestInteractorPartialState> =
        walletCorePresentationController.events.mapNotNull { response ->
            when (response) {
                is TransferEventPartialState.RequestReceived -> {
                    when {
                        response.requestData.all { it.requestedItems.isEmpty() } -> {
                            PresentationRequestInteractorPartialState.NoData(
                                verifierName = response.verifierName,
                                verifierIsTrusted = response.verifierIsTrusted,
                            )
                        }

                        else -> {
                            // request data contains n number of files can be mDL, PID, this might change in the future
                            val transformer = RequestTransformer()
                            val requestDataUi = transformer.transformToDomainItems(
                                storageDocuments = walletCoreDocumentsController.getAllIssuedDocuments(),
                                requestDocuments = response.requestData,
                                resourceProvider = resourceProvider,
                                logController = logController,
                            )

                            val documentsDomain = requestDataUi.getOrThrow()

                            if (documentsDomain.isNotEmpty()) {
                                PresentationRequestInteractorPartialState.Success(
                                    verifierName = response.verifierName,
                                    verifierIsTrusted = response.verifierIsTrusted,
                                    readerAuth = response.requestData.first().readerAuth,
                                    requestDocuments = transformer.transformToUiItems(
                                        documentsDomain = documentsDomain,
                                        resourceProvider = resourceProvider,
                                    )
                                )
                            } else {
                                PresentationRequestInteractorPartialState.NoData(
                                    verifierName = response.verifierName,
                                    verifierIsTrusted = response.verifierIsTrusted,
                                )
                            }
                        }
                    }
                }

                is TransferEventPartialState.Error -> {
                    PresentationRequestInteractorPartialState.Failure(error = response.error)
                }

                is TransferEventPartialState.Disconnected -> {
                    PresentationRequestInteractorPartialState.Disconnect
                }

                else -> null
            }
        }.safeAsync {
            PresentationRequestInteractorPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun stopPresentation() {
        walletCorePresentationController.stopPresentation()
    }

    override fun updateRequestedDocuments(items: List<RequestDocumentItemUi>) {
        val disclosedDocuments = RequestTransformer().createDisclosedDocuments(items)
        walletCorePresentationController.updateRequestedDocuments(disclosedDocuments.toMutableList())
    }

    override fun processRequest(): Flow<PresentationRequestProcessPartialState> =
        walletCorePresentationController.observeSentDocumentsRequest().mapNotNull { response ->
            when (response) {
                is WalletCorePartialState.Failure -> PresentationRequestProcessPartialState.Failure(
                    error = response.error
                )

                is WalletCorePartialState.Redirect -> PresentationRequestProcessPartialState.Redirect(
                    uri = response.uri
                )

                is WalletCorePartialState.Success -> {
                    PresentationRequestProcessPartialState.Success
                }

                is WalletCorePartialState.UserAuthenticationRequired -> {
                    PresentationRequestProcessPartialState.UserAuthenticationRequired(
                        response.authenticationData
                    )
                }

                is WalletCorePartialState.RequestIsReadyToBeSent -> PresentationRequestProcessPartialState.RequestReadyToBeSent
            }
        }


    override fun sendRequestedDocuments(): PresentationDocumentSubmissionPartialState {
        return when (val result = walletCorePresentationController.sendRequestedDocuments()) {
            is SendRequestedDocumentsPartialState.RequestSent -> {
                PresentationDocumentSubmissionPartialState.Success
            }

            is SendRequestedDocumentsPartialState.Failure -> {

                when (val exception = result.exception) {
                    is RwscaServerException -> PresentationDocumentSubmissionPartialState.Failure.ServerError(
                        errorCode = runCatching {
                            PresentationDocumentSubmissionPartialState.Failure.ServerErrorCode.valueOf(
                                exception.errorCode
                            )
                        }.getOrDefault(PresentationDocumentSubmissionPartialState.Failure.ServerErrorCode.UNKNOWN),

                        traceId = exception.traceId,
                        backendErrorCode = exception.errorCode,
                    )

                    else -> PresentationDocumentSubmissionPartialState.Failure.Unknown
                }
            }
        }
    }

    override fun deletePidDocuments(): Flow<PresentationRequestDeleteDocumentPartialState> =
        flow {
            when (
                val response = deletePidDocumentsWithRwscaCleanup(
                    rwscaInteractor = rwscaInteractor,
                    walletCoreDocumentsController = walletCoreDocumentsController,
                )
            ) {
                is DocumentDeletionPartialState.Failure ->
                    emit(PresentationRequestDeleteDocumentPartialState.Failure(error = response.error))

                is DocumentDeletionPartialState.Success -> {
                    walletPinUnBlockTimeStorageController.clear()
                    emit(PresentationRequestDeleteDocumentPartialState.Success)
                }
            }
        }.safeAsync {
            logController.d(logTag) {
                "deletePidDocuments: ${it.localizedMessage}"
            }
            PresentationRequestDeleteDocumentPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun getWalletPinBlockTime(): String =
        walletPinUnBlockTimeStorageController.retrievePinTime()

    override fun isLastPinTry(): Boolean =
        walletPinUnBlockTimeStorageController.retrieveRemainingTries() == 1

    override suspend fun reissueLowBatchDocumentsIfNeeded(): String? {
        return try {
            var failureMessage: String? = null
            reissueLowBatchDocumentsFlow(
                walletCorePresentationController = walletCorePresentationController,
                walletCoreDocumentsController = walletCoreDocumentsController,
                logController = logController,
            ).collect { state ->
                when (state) {
                    is PresentationLoadingReissuePartialState.Failure -> {
                        failureMessage = state.error
                    }

                    is PresentationLoadingReissuePartialState.UserAuthenticationRequired -> {
                        // PID reissue is authorized by the still-open rWSCA PIN session, so this
                        // device-biometric branch is not expected on the request path. Cancel it
                        // rather than hang waiting for a prompt that will never be shown.
                        logController.d(logTag) { "Unexpected device auth during reissue; cancelling" }
                        state.resultHandler.onAuthenticationError()
                    }

                    PresentationLoadingReissuePartialState.NotNeeded,
                    PresentationLoadingReissuePartialState.Success -> Unit
                }
            }
            failureMessage?.also { logController.d(logTag) { "Batch reissue failed: $it" } }
        } catch (e: CancellationException) {
            // Preserve structured concurrency: never swallow cancellation of the caller's scope.
            throw e
        } catch (e: Exception) {
            (e.localizedMessage ?: genericErrorMsg).also {
                logController.d(logTag) { "Batch reissue threw: $it" }
            }
        }
    }
}
