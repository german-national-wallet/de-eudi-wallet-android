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

package eu.europa.ec.corelogic.controller

import androidx.activity.ComponentActivity
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocument
import eu.europa.ec.eudi.iso18013.transfer.response.DisclosedDocuments
import eu.europa.ec.eudi.iso18013.transfer.response.RequestProcessor
import eu.europa.ec.eudi.iso18013.transfer.response.RequestedDocument
import eu.europa.ec.eudi.iso18013.transfer.toKotlinResult
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultKeyUnlockData
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.extension.addOrReplace
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.corelogic.di.WalletPresentationScope
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.corelogic.securearea.exception.RwscaException
import eu.europa.ec.corelogic.util.EudiWalletListenerWrapper
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import org.multipaz.securearea.AndroidKeystoreKeyUnlockData
import org.multipaz.securearea.AndroidKeystoreSecureArea
import java.net.URI

sealed class PresentationControllerConfig(val initiatorRoute: String) {
    data class OpenId4VP(val uri: String, val initiator: String) :
        PresentationControllerConfig(initiator)

    data class Ble(val initiator: String) : PresentationControllerConfig(initiator)
}

sealed class TransferEventPartialState {
    data object Connected : TransferEventPartialState()
    data object Connecting : TransferEventPartialState()
    data object Disconnected : TransferEventPartialState()
    data class Error(val error: String) : TransferEventPartialState()
    data class QrEngagementReady(val qrCode: String) : TransferEventPartialState()
    data class RequestReceived(
        val requestData: List<RequestedDocument>,
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
    ) : TransferEventPartialState()

    data object ResponseSent : TransferEventPartialState()
    data class Redirect(val uri: URI) : TransferEventPartialState()
    data object IntentToSend : TransferEventPartialState()
}

sealed class CheckKeyUnlockPartialState {
    data class Failure(val error: String) : CheckKeyUnlockPartialState()
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : CheckKeyUnlockPartialState()

    data object RequestIsReadyToBeSent : CheckKeyUnlockPartialState()
}

sealed class SendRequestedDocumentsPartialState {
    data class Failure(val exception: RwscaException?) :
        SendRequestedDocumentsPartialState()

    data object RequestSent : SendRequestedDocumentsPartialState()
}

sealed class ResponseReceivedPartialState {
    data object Success : ResponseReceivedPartialState()
    data class Redirect(val uri: URI) : ResponseReceivedPartialState()
    data class Failure(val error: String) : ResponseReceivedPartialState()
}

sealed class WalletCorePartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : WalletCorePartialState()

    data class Failure(val error: String) : WalletCorePartialState()
    data object Success : WalletCorePartialState()
    data class Redirect(val uri: URI) : WalletCorePartialState()
    data object RequestIsReadyToBeSent : WalletCorePartialState()
}

/**
 * Common scoped interactor that has all the complexity and required interaction with the EudiWallet Core.
 * */
interface WalletCorePresentationController {
    /**
     * On initialization, it adds the core listener and remove it when scope is canceled.
     * When the scope is canceled so does the presentation
     *
     * @return Hot Flow that emits the Core's status callback.
     * */
    val events: SharedFlow<TransferEventPartialState>

    /**
     * User selection data for request step
     * */
    val disclosedDocuments: MutableList<DisclosedDocument>?

    /**
     * Verifier name so it can be retrieve across screens
     * */
    val verifierName: String?

    val verifierIsTrusted: Boolean?

    /**
     * Who started the presentation
     * */
    val initiatorRoute: String

    val redirectUri: URI?

    /**
     * Set [PresentationControllerConfig]
     * */
    fun setConfig(config: PresentationControllerConfig)

    /**
     * Terminates the presentation and kills the coroutine scope that [events] live in
     * */
    fun stopPresentation()

    /**
     * Starts QR engagement. This will trigger [events] emission.
     *
     * [TransferEventPartialState.QrEngagementReady] -> QR String to show QR
     *
     * [TransferEventPartialState.Connecting] -> Connecting
     *
     * [TransferEventPartialState.Connected] -> Connected. We can proceed to the next screen
     * */
    fun startQrEngagement()

    /**
     * Enable/Disable NFC service
     * */
    fun toggleNfcEngagement(componentActivity: ComponentActivity, toggle: Boolean)

    /**
     * Transform UI models to Domain and create -> sent the request.
     *
     * @return Flow that emits the creation state. On Success send the request.
     * The response of that request is emitted through [events]
     *  */
    fun checkForKeyUnlock(): Flow<CheckKeyUnlockPartialState>

    fun sendRequestedDocuments(): SendRequestedDocumentsPartialState

    /**
     * Updates the UI model
     * @param disclosedDocuments User updated data through UI Events
     * */
    fun updateRequestedDocuments(disclosedDocuments: MutableList<DisclosedDocument>?)

    /**
     * @return flow that maps the state from [events] emission to what we consider as success state
     * */
    fun mappedCallbackStateFlow(): Flow<ResponseReceivedPartialState>

    /**
     * The main observation point for collecting state for the Request flow.
     * Exposes a single flow for two operations([checkForKeyUnlock] - [mappedCallbackStateFlow])
     * and a single state
     * @return flow that emits the create, sent, receive states
     * */
    fun observeSentDocumentsRequest(): Flow<WalletCorePartialState>
}

@Scope(WalletPresentationScope::class)
@Scoped
class WalletCorePresentationControllerImpl(
    private val eudiWallet: EudiWallet,
    private val resourceProvider: ResourceProvider,
    private val logController: LogController,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WalletCorePresentationController {
    private val logTag = "WaCorePresContrImpl" // tags are limited to 23 characters

    private val genericErrorMessage = resourceProvider.genericErrorMessage()

    private val coroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var _config: PresentationControllerConfig

    override var disclosedDocuments: MutableList<DisclosedDocument>? = null

    private var processedRequest: RequestProcessor.ProcessedRequest.Success? = null

    override var verifierName: String? = null

    override var verifierIsTrusted: Boolean? = null

    override val initiatorRoute: String
        get() {
            val config = requireInit { _config }
            return config.initiatorRoute
        }

    override var redirectUri: URI? = null

    override fun setConfig(config: PresentationControllerConfig) {
        _config = config
    }

    override val events: SharedFlow<TransferEventPartialState> = callbackFlow {
        val eventListenerWrapper = EudiWalletListenerWrapper(
            onQrEngagementReady = { qrCode ->
                trySendBlocking(
                    TransferEventPartialState.QrEngagementReady(qrCode = qrCode)
                )
            },
            onConnected = {
                trySendBlocking(
                    TransferEventPartialState.Connected
                )
            },
            onConnecting = {
                trySendBlocking(
                    TransferEventPartialState.Connecting
                )
            },
            onDisconnected = {
                trySendBlocking(
                    TransferEventPartialState.Disconnected
                )
            },
            onError = { errorMessage ->
                trySendBlocking(
                    TransferEventPartialState.Error(
                        error = errorMessage.ifEmpty { genericErrorMessage }
                    )
                )
            },
            onRequestReceived = { requestedDocumentData, _ ->
                trySendBlocking(
                    requestedDocumentData.getOrNull()?.let { requestedDocuments ->
                        processedRequest = requestedDocuments
                        verifierName = requestedDocuments.requestedDocuments
                            .firstOrNull()?.readerAuth?.readerCommonName

                        val isTrusted = requestedDocuments.requestedDocuments
                            .firstOrNull()?.readerAuth?.isVerified == true
                        verifierIsTrusted = isTrusted

                        TransferEventPartialState.RequestReceived(
                            requestData = requestedDocuments.requestedDocuments,
                            verifierName = verifierName,
                            verifierIsTrusted = isTrusted
                        )
                    } ?: TransferEventPartialState.Error(error = genericErrorMessage)
                )
            },
            onResponseSent = {
                trySendBlocking(
                    TransferEventPartialState.ResponseSent
                )
            },
            onRedirect = { uri ->
                redirectUri = uri

                trySendBlocking(
                    TransferEventPartialState.Redirect(
                        uri = uri
                    )
                )
            },

            intentToSend = {
                trySendBlocking(
                    TransferEventPartialState.IntentToSend
                )
            }
        )

        addListener(eventListenerWrapper)
        awaitClose {
            removeListener(eventListenerWrapper)
            stopActivePresentation()
        }
    }.safeAsync {
        TransferEventPartialState.Error(
            error = it.localizedMessage ?: resourceProvider.genericErrorMessage()
        )
    }.shareIn(coroutineScope, SharingStarted.Lazily, 2)

    override fun startQrEngagement() {
        eudiWallet.startProximityPresentation()
    }

    override fun toggleNfcEngagement(componentActivity: ComponentActivity, toggle: Boolean) {
        try {
            if (toggle) {
                eudiWallet.enableNFCEngagement(componentActivity)
            } else {
                eudiWallet.disableNFCEngagement(componentActivity)
            }
        } catch (_: Exception) {
        }
    }

    override fun checkForKeyUnlock() = flow {
        disclosedDocuments?.let { documents ->
            val authenticationData = documents
                .mapNotNull { doc ->
                    val unlockData = getAndroidKeyUnlockDataIfRequired(doc.documentId)
                        ?: return@mapNotNull null
                    AuthenticationData(
                        crypto = BiometricCrypto(unlockData.getCryptoObjectForSigning()),
                        onAuthenticationSuccess = {
                            documents.addOrReplace(
                                value = doc.copy(keyUnlockData = unlockData),
                                replaceCondition = { disclosedDocument ->
                                    disclosedDocument.documentId == doc.documentId
                                }
                            )
                        }
                    )
                }

            if (authenticationData.isNotEmpty()) {
                emit(
                    CheckKeyUnlockPartialState.UserAuthenticationRequired(
                        authenticationData
                    )
                )
            } else {
                emit(
                    CheckKeyUnlockPartialState.RequestIsReadyToBeSent
                )
            }
        }
    }.safeAsync {
        CheckKeyUnlockPartialState.Failure(
            error = it.localizedMessage ?: genericErrorMessage
        )
    }

    /**
     * Looks up the requested document's active credential and checks whether presentation needs
     * Android device authentication for its Android Keystore key.
     *
     * @return [AndroidKeystoreKeyUnlockData] when the document is backed by an Android Keystore
     * credential whose key requires user authentication, or `null` when no Android/device
     * credential prompt is needed for this document.
     */
    private suspend fun getAndroidKeyUnlockDataIfRequired(
        documentId: DocumentId,
    ): AndroidKeystoreKeyUnlockData? {
        val document = eudiWallet.getDocumentById(documentId) as? IssuedDocument
            // The presentation request references no issued document we can unlock here.
            ?: return null
        val credential = document.findCredential()
            // No active credential is available for this document.
            ?: return null
        val secureArea = credential.secureArea as? AndroidKeystoreSecureArea
            // Non-Android secure areas, such as rWSCA, have their own unlock flow.
            ?: return null
        val keyInfo = secureArea.getKeyInfo(credential.alias)

        if (!keyInfo.isUserAuthenticationRequired) {
            // Android Keystore key exists, but its settings do not require user authentication.
            return null
        }

        return getDefaultKeyUnlockData(secureArea, credential.alias)
            ?: throw IllegalStateException("Key data missing for $documentId")
    }

    override fun sendRequestedDocuments(): SendRequestedDocumentsPartialState {
        return disclosedDocuments?.let { safeDisclosedDocuments ->

            var result: SendRequestedDocumentsPartialState =
                SendRequestedDocumentsPartialState.RequestSent

            processedRequest?.generateResponse(DisclosedDocuments(safeDisclosedDocuments.toList()))
                ?.toKotlinResult()
                ?.onFailure {
                    logController.e(logTag) {
                        "Error while sending the requested documents: ${it.localizedMessage}"
                    }

                    // The RwscaException that we're looking for may have been wrapped, so we
                    // have to walk along the cause chain.
                    val rwscaException = generateSequence(it) { t -> t.cause }
                        .filterIsInstance<RwscaException>()
                        .firstOrNull()

                    result = SendRequestedDocumentsPartialState.Failure(
                        exception = rwscaException
                    )

                }?.onSuccess {
                    eudiWallet.sendResponse(it.response)
                    result = SendRequestedDocumentsPartialState.RequestSent
                }
            result
        } ?: SendRequestedDocumentsPartialState.Failure(null)
    }

    override fun mappedCallbackStateFlow(): Flow<ResponseReceivedPartialState> {
        return events.mapNotNull { response ->
            when (response) {

                // Fix: Wallet core should return Success state here
                is TransferEventPartialState.Error -> {
                    if (response.error == "Peer disconnected without proper session termination") {
                        ResponseReceivedPartialState.Success
                    } else {
                        ResponseReceivedPartialState.Failure(error = response.error)
                    }
                }

                is TransferEventPartialState.Redirect -> {
                    ResponseReceivedPartialState.Redirect(uri = response.uri)
                }

                is TransferEventPartialState.Disconnected -> {
                    when {
                        events.replayCache.firstOrNull() is TransferEventPartialState.Redirect -> null
                        else -> ResponseReceivedPartialState.Success
                    }
                }

                is TransferEventPartialState.ResponseSent -> ResponseReceivedPartialState.Success

                else -> null
            }
        }.safeAsync {
            ResponseReceivedPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMessage
            )
        }
    }

    override fun observeSentDocumentsRequest(): Flow<WalletCorePartialState> =
        merge(checkForKeyUnlock(), mappedCallbackStateFlow()).mapNotNull {
            when (it) {
                is CheckKeyUnlockPartialState.Failure -> {
                    WalletCorePartialState.Failure(it.error)
                }

                is CheckKeyUnlockPartialState.UserAuthenticationRequired -> {
                    WalletCorePartialState.UserAuthenticationRequired(it.authenticationData)
                }

                is ResponseReceivedPartialState.Failure -> {
                    WalletCorePartialState.Failure(it.error)
                }

                is ResponseReceivedPartialState.Redirect -> {
                    WalletCorePartialState.Redirect(
                        uri = it.uri
                    )
                }

                is CheckKeyUnlockPartialState.RequestIsReadyToBeSent -> {
                    WalletCorePartialState.RequestIsReadyToBeSent
                }

                else -> {
                    WalletCorePartialState.Success
                }
            }
        }.safeAsync {
            WalletCorePartialState.Failure(
                error = it.localizedMessage ?: genericErrorMessage
            )
        }

    override fun updateRequestedDocuments(disclosedDocuments: MutableList<DisclosedDocument>?) {
        this.disclosedDocuments = disclosedDocuments
    }

    override fun stopPresentation() {
        coroutineScope.cancel()
        CoroutineScope(dispatcher).launch {
            stopActivePresentation()
        }
    }

    /**
     * Stops the underlying presentation using the transport that matches the active config.
     *
     * A remote (OpenID4VP) presentation must be stopped via [stopRemotePresentation]; calling only
     * [stopProximityPresentation] leaves the OpenID4VP manager holding its previous session, which
     * wedges subsequent remote presentations.
     */
    private fun stopActivePresentation() {
        if (::_config.isInitialized && _config is PresentationControllerConfig.OpenId4VP) {
            eudiWallet.stopRemotePresentation()
        } else {
            eudiWallet.stopProximityPresentation()
        }
    }

    private fun addListener(listener: EudiWalletListenerWrapper) {
        val config = requireInit { _config }
        eudiWallet.addTransferEventListener(listener)
        if (config is PresentationControllerConfig.OpenId4VP) {
            eudiWallet.startRemotePresentation(config.uri.toUri())
        }
    }

    private fun removeListener(listener: EudiWalletListenerWrapper) {
        requireInit { _config }
        eudiWallet.removeTransferEventListener(listener)
    }

    private fun <T> requireInit(block: () -> T): T {
        if (!::_config.isInitialized) {
            throw IllegalStateException("setConfig() must be called before using the WalletCorePresentationController")
        }
        return block()
    }
}
