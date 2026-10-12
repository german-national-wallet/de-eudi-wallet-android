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
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.corelogic.di.WalletPresentationScope
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.corelogic.model.DisclosedDocumentDomain
import eu.europa.ec.corelogic.securearea.exception.RwscaException
import eu.europa.ec.corelogic.util.EudiWalletListenerWrapper
import eu.europa.ec.eudi.iso18013.transfer.TransferEvent
import eu.europa.ec.eudi.iso18013.transfer.response.RequestProcessor
import eu.europa.ec.eudi.iso18013.transfer.toKotlinResult
import eu.europa.ec.eudi.openid4vp.Format
import eu.europa.ec.eudi.openid4vp.dcql.DCQL
import eu.europa.ec.eudi.openid4vp.dcql.metaMsoMdoc
import eu.europa.ec.eudi.openid4vp.dcql.metaSdJwtVc
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultKeyUnlockData
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.transfer.openId4vp.dcql.ProcessedDcqlRequest
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.sprind.wallet.corelogic.presentationerror.PresentationError
import org.sprind.wallet.corelogic.presentationerror.mapToPresentationError
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped
import org.multipaz.credential.Credential
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.crypto.javaX509Certificates
import org.multipaz.presentment.CredentialPresentmentSelection
import org.multipaz.presentment.CredentialPresentmentSetOptionMemberMatch
import org.multipaz.request.RequestedClaim
import org.multipaz.request.Requester
import org.multipaz.securearea.AndroidKeystoreKeyUnlockData
import org.multipaz.securearea.AndroidKeystoreSecureArea
import org.multipaz.securearea.KeyUnlockData
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.corelogic.model.RelyingPartyRegistrationStatus
import org.sprind.wallet.corelogic.model.toNotRegisteredOrNull
import org.sprind.wallet.corelogic.model.toRelyingPartyRegistrationStatus
import java.net.URI
import kotlin.time.Duration.Companion.seconds

sealed class PresentationControllerConfig(val initiatorRoute: String) {
    data class OpenId4VP(val uri: String, val initiator: String) :
        PresentationControllerConfig(initiator)

    data class Ble(val initiator: String) : PresentationControllerConfig(initiator)
}

sealed class TransferEventPartialState {
    data object Connected : TransferEventPartialState()
    data object Connecting : TransferEventPartialState()
    data object Disconnected : TransferEventPartialState()
    data class Error(val error: PresentationError) : TransferEventPartialState()

    /** openid4vp refused the request over its registration certificate. */
    data class RelyingPartyNotRegistered(
        val relyingPartyRegistration: RelyingPartyRegistrationStatus.NotRegistered,
    ) : TransferEventPartialState()

    data class QrEngagementReady(val qrCode: String) : TransferEventPartialState()
    /**
     * @property requestedCredentialSets the DCQL `credential_sets` in terms of credential types;
     * without `credential_sets`, one required set whose single option needs every query. Null for
     * a request that is not DCQL.
     */
    data class RequestReceived(
        val requestData: List<CredentialPresentmentSetOptionMemberMatch>,
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
        val relyingPartyRegistration: RelyingPartyRegistrationStatus,
        val requestedCredentialSets: List<RequestedCredentialSet>? = null,
    ) : TransferEventPartialState()

    data object ResponseSent : TransferEventPartialState()
    data class Redirect(val uri: URI) : TransferEventPartialState()
    data object IntentToSend : TransferEventPartialState()
}

/**
 * One DCQL `credential_sets` entry. Each option lists, per credential query it needs, the doctypes
 * or vcts that query accepts.
 */
data class RequestedCredentialSet(
    val required: Boolean,
    val options: List<List<Set<String>>>,
)

sealed class CheckKeyUnlockPartialState {
    data class Failure(val error: PresentationError) : CheckKeyUnlockPartialState()
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : CheckKeyUnlockPartialState()

    data object RequestIsReadyToBeSent : CheckKeyUnlockPartialState()
}

sealed class SendRequestedDocumentsPartialState {
    data class Failure(
        val exception: RwscaException?,
        val throwable: Throwable? = exception,
        val traceId: String? = null,
    ) : SendRequestedDocumentsPartialState()

    data object RequestSent : SendRequestedDocumentsPartialState()
}

sealed class ResponseReceivedPartialState {
    data object Success : ResponseReceivedPartialState()
    data class Redirect(val uri: URI) : ResponseReceivedPartialState()
    data class Failure(val error: PresentationError) : ResponseReceivedPartialState()
}

sealed class RejectPresentationPartialState {

    data class Redirect(val uri: URI) : RejectPresentationPartialState()

    data object Delivered : RejectPresentationPartialState()

    data class Failure(val error: String) : RejectPresentationPartialState()
}

sealed class WalletCorePartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : WalletCorePartialState()

    data class Failure(val error: PresentationError) : WalletCorePartialState()
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
     * What the presentation will disclose: the claims the user consented to, per document.
     *
     * Starts as everything the request asks for and is replaced by the consent UI's selection
     * through [updateRequestedDocuments].
     * */
    val disclosedDocuments: MutableList<DisclosedDocumentDomain>?

    /**
     * Verifier name so it can be retrieve across screens
     * */
    val verifierName: String?

    val verifierIsTrusted: Boolean?

    val relyingPartyRegistration: RelyingPartyRegistrationStatus

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
     * Tells the relying party that the user refused its request, and waits for what it makes of
     * that.
     * */
    suspend fun rejectPresentation(): RejectPresentationPartialState

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

    suspend fun sendRequestedDocuments(): SendRequestedDocumentsPartialState

    /**
     * Applies the consent UI's selection: the presentment selection that will be sent is narrowed
     * to exactly these claims, so an unticked row withholds that claim and a document with
     * nothing ticked drops out of the response.
     *
     * @param disclosedDocuments User updated data through UI Events
     * */
    fun updateRequestedDocuments(disclosedDocuments: MutableList<DisclosedDocumentDomain>?)

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

/**
 * Drives one presentation, from engagement to response.
 *
 * Per-claim consent works differently since wallet-core v0.30.0. Up to v0.29.0 the wallet built
 * the response from a `DisclosedDocuments` list it assembled itself; now
 * `RequestProcessor.ProcessedRequest.Success` offers pre-computed
 * [CredentialPresentmentSelection]s and `generateResponse` takes one of them. A partial disclosure
 * is therefore expressed by narrowing a selection rather than by filtering a claim list:
 * [fullPresentmentSelection] holds everything the request can be satisfied with,
 * [updateRequestedDocuments] narrows it to what the user ticked, and [sendRequestedDocuments]
 * sends the narrowed [presentmentSelection].
 */
@Scope(WalletPresentationScope::class)
@Scoped
class WalletCorePresentationControllerImpl(
    private val eudiWallet: EudiWallet,
    private val resourceProvider: ResourceProvider,
    private val logController: LogController,
    private val telemetry: Telemetry,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WalletCorePresentationController {
    private val logTag = "WaCorePresContrImpl" // tags are limited to 23 characters

    private val genericErrorMessage = resourceProvider.genericErrorMessage()

    private val coroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var _config: PresentationControllerConfig

    override var disclosedDocuments: MutableList<DisclosedDocumentDomain>? = null

    private var processedRequest: RequestProcessor.ProcessedRequest.Success? = null

    /** Everything the request can be satisfied with, as wallet-core computed it. */
    private var fullPresentmentSelection: CredentialPresentmentSelection? = null

    /**
     * [fullPresentmentSelection] narrowed to the user's consent; this is what gets sent. Stays
     * null until the consent UI reports a selection, so nothing is disclosed without it.
     */
    private var presentmentSelection: CredentialPresentmentSelection? = null

    /** Unlock data gathered by [checkForKeyUnlock], keyed by `match.credential.identifier`. */
    private val keyUnlockData: MutableMap<String, KeyUnlockData> = mutableMapOf()

    override var verifierName: String? = null

    override var verifierIsTrusted: Boolean? = null

    override var relyingPartyRegistration: RelyingPartyRegistrationStatus =
        RelyingPartyRegistrationStatus.NotEvaluated

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
            onError = { throwable ->
                val notRegistered = throwable.toNotRegisteredOrNull()
                if (notRegistered != null) {
                    relyingPartyRegistration = notRegistered
                    logRegistrationStatus(notRegistered)
                }
                trySendBlocking(
                    notRegistered?.let { TransferEventPartialState.RelyingPartyNotRegistered(it) }
                        ?: TransferEventPartialState.Error(
                            error = mapToPresentationError(
                                throwable = throwable,
                                traceId = telemetry.currentTraceId(),
                                genericErrorMessage = genericErrorMessage,
                            )
                        )
                )
            },
            onRequestReceived = { requestedDocumentData, _ ->
                trySendBlocking(
                    requestedDocumentData.getOrNull()?.let { request ->
                        processedRequest = request

                        // The first option holds every credential the wallet can present for this
                        // request; updateRequestedDocuments() narrows it to the user's consent.
                        fullPresentmentSelection = request.presentmentSelections.firstOrNull()
                        presentmentSelection = null
                        disclosedDocuments = null
                        keyUnlockData.clear()

                        // trustMetadata is populated only for readers that validated against the
                        // reader trust store; its displayName is the verifier's legal name. For
                        // untrusted readers fall back to the certificate's Common Name, which is
                        // what RequestedDocument.readerAuth.readerCommonName used to carry.
                        val isTrusted = request.trustMetadata != null
                        verifierName = request.trustMetadata?.displayName
                            ?: request.requester.readerCommonNameOrNull()
                        verifierIsTrusted = isTrusted

                        val registration =
                            request.wrpRegistration.toRelyingPartyRegistrationStatus()
                        relyingPartyRegistration = registration
                        logRegistrationStatus(registration)

                        TransferEventPartialState.RequestReceived(
                            requestData = fullPresentmentSelection?.matches.orEmpty(),
                            verifierName = verifierName,
                            verifierIsTrusted = isTrusted,
                            relyingPartyRegistration = registration,
                            requestedCredentialSets = request.requestedCredentialSets(),
                        )
                    } ?: TransferEventPartialState.Error(
                        error = PresentationError.Unclassified(
                            traceId = telemetry.currentTraceId(),
                            cause = null,
                            displayMessage = genericErrorMessage,
                        )
                    )
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
            error = PresentationError.Unclassified(
                traceId = telemetry.currentTraceId(),
                cause = it,
                displayMessage = it.localizedMessage ?: resourceProvider.genericErrorMessage(),
            )
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
        presentmentSelection?.let { selection ->
            val authenticationData = selection.matches
                .distinctBy { it.credential.identifier }
                .mapNotNull { match ->
                    val credential = match.credential
                    val unlockData = getAndroidKeyUnlockDataIfRequired(credential)
                        ?: return@mapNotNull null
                    AuthenticationData(
                        crypto = BiometricCrypto(unlockData.getCryptoObjectForSigning()),
                        onAuthenticationSuccess = {
                            keyUnlockData[credential.identifier] = unlockData
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
            error = mapToPresentationError(
                throwable = it,
                traceId = telemetry.currentTraceId(),
                genericErrorMessage = genericErrorMessage,
            )
        )
    }

    /**
     * Checks whether presenting [credential] needs Android device authentication for its Android
     * Keystore key.
     *
     * @return [AndroidKeystoreKeyUnlockData] when the credential is backed by an Android Keystore
     * key that requires user authentication, or `null` when no Android/device credential prompt is
     * needed for it.
     */
    private suspend fun getAndroidKeyUnlockDataIfRequired(
        credential: Credential,
    ): AndroidKeystoreKeyUnlockData? {
        val secureAreaBound = credential as? SecureAreaBoundCredential
        // Credentials not bound to a secure area have no key to unlock.
            ?: return null
        val secureArea = secureAreaBound.secureArea as? AndroidKeystoreSecureArea
        // Non-Android secure areas, such as rWSCA, have their own unlock flow.
            ?: return null
        val keyInfo = secureArea.getKeyInfo(secureAreaBound.alias)

        if (!keyInfo.isUserAuthenticationRequired) {
            // Android Keystore key exists, but its settings do not require user authentication.
            return null
        }

        return getDefaultKeyUnlockData(secureArea, secureAreaBound.alias)
            ?: throw IllegalStateException(
                "Key data missing for credential ${credential.identifier}"
            )
    }

    override suspend fun sendRequestedDocuments(): SendRequestedDocumentsPartialState {
        val request = processedRequest
        val selection = presentmentSelection
        return if (request != null && selection != null) {

            var result: SendRequestedDocumentsPartialState =
                SendRequestedDocumentsPartialState.RequestSent

            request.generateResponse(selection, keyUnlockData.toMap())
                .toKotlinResult()
                .onFailure {
                    logController.e(logTag) {
                        "Error while sending the requested documents: ${it.localizedMessage}"
                    }

                    // The RwscaException that we're looking for may have been wrapped, so we
                    // have to walk along the cause chain.
                    val rwscaException = generateSequence(it) { t -> t.cause }
                        .filterIsInstance<RwscaException>()
                        .firstOrNull()

                    result = SendRequestedDocumentsPartialState.Failure(
                        exception = rwscaException,
                        throwable = it,
                        traceId = telemetry.currentTraceId(),
                    )

                }.onSuccess {
                    eudiWallet.sendResponse(it.response)
                    result = SendRequestedDocumentsPartialState.RequestSent
                }
            result
        } else {
            SendRequestedDocumentsPartialState.Failure(
                exception = null,
                traceId = telemetry.currentTraceId(),
            )
        }
    }

    override fun updateRequestedDocuments(
        disclosedDocuments: MutableList<DisclosedDocumentDomain>?,
    ) {
        this.disclosedDocuments = disclosedDocuments
        presentmentSelection = disclosedDocuments?.let { fullPresentmentSelection?.narrowedTo(it) }
        // A narrowed selection can drop whole credentials, so unlock data gathered for a previous
        // one no longer applies.
        keyUnlockData.keys.retainAll(
            presentmentSelection?.matches?.mapTo(mutableSetOf()) { it.credential.identifier }
                ?: emptySet()
        )
    }

    /**
     * This selection with every match reduced to the claims [consent] allows for that match's
     * document. Documents absent from [consent], and matches the consent leaves without any of
     * their requested claims, are dropped.
     *
     * A match that asks for no claims to begin with is kept as is: per OpenID4VP §6.4.1 a
     * Credential Query without `claims` requests a presentation with no selectively disclosable
     * claim, so there is nothing to narrow and dropping it would silently break the verifier's
     * `credential_sets`.
     */
    private fun CredentialPresentmentSelection.narrowedTo(
        consent: List<DisclosedDocumentDomain>,
    ): CredentialPresentmentSelection {
        val allowedByDocument: Map<DocumentId, Set<RequestedClaim>> =
            consent.associate { it.documentId to it.disclosedClaims }
        return CredentialPresentmentSelection(
            matches = matches.mapNotNull { match ->
                val allowed = allowedByDocument[match.credential.document.identifier]
                    ?: return@mapNotNull null
                if (match.claims.isEmpty()) return@mapNotNull match
                val kept = match.claims.filterKeys { it in allowed }
                if (kept.isEmpty()) null else match.copy(claims = kept)
            }
        )
    }

    /**
     * The Common Name of the reader's leaf certificate, shown for verifiers that did not validate
     * against the reader trust store. Up to wallet-core v0.29.0 this arrived ready-made as
     * `RequestedDocument.readerAuth.readerCommonName`.
     */
    private fun Requester.readerCommonNameOrNull(): String? =
        certChain?.javaX509Certificates?.firstOrNull()
            ?.subjectX500Principal?.name
            ?.split(",")
            ?.map { it.split("=", limit = 2) }
            ?.firstOrNull { it.size == 2 && it[0].trim() == "CN" }
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /** The rejection screen shows no details, so they are only kept here. */
    private fun logRegistrationStatus(status: RelyingPartyRegistrationStatus) {
        when (status) {
            is RelyingPartyRegistrationStatus.OverAsking -> logController.d(logTag) {
                "Relying party over-asked: " +
                        status.overAskedClaims.joinToString { "${it.format}:${it.path}" }
            }

            is RelyingPartyRegistrationStatus.NotRegistered -> logController.d(logTag) {
                "Relying party registration could not be validated: ${status.reason}"
            }

            RelyingPartyRegistrationStatus.NotEvaluated,
            is RelyingPartyRegistrationStatus.WithinRegisteredScope,
                -> Unit
        }
    }

    override fun mappedCallbackStateFlow(): Flow<ResponseReceivedPartialState> {
        return events.mapNotNull { response ->
            when (response) {

                // Fix: Wallet core should return Success state here
                is TransferEventPartialState.Error -> {
                    if (response.error.displayMessage == "Peer disconnected without proper session termination") {
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
                error = mapToPresentationError(
                    throwable = it,
                    traceId = telemetry.currentTraceId(),
                    genericErrorMessage = genericErrorMessage,
                )
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
                error = mapToPresentationError(
                    throwable = it,
                    traceId = telemetry.currentTraceId(),
                    genericErrorMessage = genericErrorMessage,
                )
            )
        }

    override fun stopPresentation() {
        coroutineScope.cancel()
        CoroutineScope(dispatcher).launch {
            stopActivePresentation()
        }
    }

    override suspend fun rejectPresentation(): RejectPresentationPartialState {
        if (!::_config.isInitialized || _config !is PresentationControllerConfig.OpenId4VP) {
            return RejectPresentationPartialState.Delivered
        }

        val outcome = CompletableDeferred<RejectPresentationPartialState>()

        val listener = TransferEvent.Listener { event ->
            when (event) {
                is TransferEvent.Redirect ->
                    outcome.complete(RejectPresentationPartialState.Redirect(event.redirectUri))

                is TransferEvent.ResponseSent ->
                    outcome.complete(RejectPresentationPartialState.Delivered)

                is TransferEvent.Error -> outcome.complete(
                    RejectPresentationPartialState.Failure(
                        error = event.error.message ?: genericErrorMessage
                    )
                )

                // Not part of the rejection flow.
                is TransferEvent.QrEngagementReady,
                TransferEvent.Connecting,
                TransferEvent.Connected,
                is TransferEvent.RequestReceived,
                is TransferEvent.IntentToSend,
                TransferEvent.Disconnected -> Unit
            }
        }

        eudiWallet.addTransferEventListener(listener)
        val result = try {
            eudiWallet.rejectRemotePresentation()
            withTimeoutOrNull(REJECTION_TIMEOUT) { outcome.await() }
                ?: RejectPresentationPartialState.Failure(
                    error = "Verifier did not answer the rejection within $REJECTION_TIMEOUT"
                )
        } catch (e: Exception) {
            RejectPresentationPartialState.Failure(
                error = e.localizedMessage ?: genericErrorMessage
            )
        } finally {
            eudiWallet.removeTransferEventListener(listener)
        }

        if (result is RejectPresentationPartialState.Failure) {
            logController.d(logTag) { "Rejection not delivered: ${result.error}" }
        }
        return result
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

private val REJECTION_TIMEOUT = 30.seconds

private fun RequestProcessor.ProcessedRequest.Success.requestedCredentialSets(): List<RequestedCredentialSet>? =
    (this as? ProcessedDcqlRequest)?.resolvedRequestObject?.query?.toRequestedCredentialSets()

internal fun DCQL.toRequestedCredentialSets(): List<RequestedCredentialSet> {
    // The meta accessors decode for one format and throw on the other, so pick by format first.
    val typesByQueryId = credentials.value.associate { query ->
        query.id to when (query.format) {
            Format.MsoMdoc -> setOfNotNull(query.metaMsoMdoc?.doctypeValue?.value)
            Format.SdJwtVc -> query.metaSdJwtVc?.vctValues.orEmpty().toSet()
            else -> emptySet()
        }
    }
    val sets = credentialSets?.value
        ?: return listOf(RequestedCredentialSet(required = true, options = listOf(typesByQueryId.values.toList())))
    return sets.map { set ->
        RequestedCredentialSet(
            required = set.requiredOrDefault,
            options = set.options.map { option -> option.value.map { typesByQueryId[it].orEmpty() } },
        )
    }
}
