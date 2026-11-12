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
import eu.europa.ec.corelogic.controller.RejectPresentationPartialState
import eu.europa.ec.corelogic.controller.RequestedCredentialSet
import eu.europa.ec.corelogic.controller.SendRequestedDocumentsPartialState
import eu.europa.ec.corelogic.controller.TransferEventPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePartialState
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.corelogic.securearea.exception.RwscaServerException
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.sprind.wallet.corelogic.presentationerror.PresentationError
import org.sprind.wallet.corelogic.presentationerror.mapToPresentationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import org.sprind.wallet.corelogic.model.RelyingPartyRegistrationStatus
import org.sprind.wallet.corelogic.model.forLocale
import org.sprind.wallet.corelogic.model.rejectsRequest
import org.sprind.wallet.corelogic.model.reportUri
import org.sprind.wallet.corelogic.model.serviceProviderUri
import org.sprind.wallet.corelogic.model.supportUri
import java.net.URI
import kotlin.time.Clock

sealed class PresentationRequestInteractorPartialState {
    data class Success(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
        val requestDocuments: List<RequestDocumentItemUi>,
        val purpose: String? = null,
        val reportUri: String? = null,
        val supportUri: String? = null,
        val serviceProviderUri: String? = null,
    ) : PresentationRequestInteractorPartialState()

    /**
     * Nothing in the wallet satisfies the request.
     *
     * @property missingPid The request cannot be satisfied without a PID and the wallet holds
     *   no valid one, so the user has to add their Digital ID first (WD-4759).
     */
    data class NoData(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
        val missingPid: Boolean = false,
    ) : PresentationRequestInteractorPartialState()

    /** The wallet holds the requested credentials, but not the claims the request needs. */
    data class Unsatisfiable(
        val verifierName: String?,
        val verifierIsTrusted: Boolean,
    ) : PresentationRequestInteractorPartialState()

    data class Rejected(
        val verifierName: String?,
        val reason: RequestRejectionReason,
        val supervisoryAuthorityUri: String? = null,
        val verifierAnswered: Boolean = false,
    ) : PresentationRequestInteractorPartialState()

    data class Failure(val error: PresentationError) : PresentationRequestInteractorPartialState()
    data object Disconnect : PresentationRequestInteractorPartialState()
}

sealed class PresentationRejectionPartialState {

    data class Redirect(val uri: URI) : PresentationRejectionPartialState()

    data object Delivered : PresentationRejectionPartialState()

    data class Failure(val error: String) : PresentationRejectionPartialState()
}

enum class RequestRejectionReason {
    OVER_ASKING,

    /** Any certificate failure alike, so the screen does not reveal which check failed. */
    NOT_REGISTERED,
}

sealed class PresentationRequestProcessPartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : PresentationRequestProcessPartialState()

    data class Failure(val error: PresentationError) : PresentationRequestProcessPartialState()
    data object Success : PresentationRequestProcessPartialState()
    data class Redirect(val uri: URI) : PresentationRequestProcessPartialState()
    data object RequestReadyToBeSent : PresentationRequestProcessPartialState()
}

sealed class PresentationDocumentSubmissionPartialState {
    sealed class Failure : PresentationDocumentSubmissionPartialState() {
        data class PinVerificationBlocked(val iso8601utcTime: String, val triesRemaining: Int) :
            Failure()

        data class WrongPin(val triesRemaining: Int, val iso8601utcTime: String) : Failure()

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

        data class Unknown(val error: PresentationError) : Failure()

        enum class ServerErrorCode {
            RWSCD_ACCOUNT_UNKNOWN,
            RWSCD_AUTH_VERIFICATION_FAILED,
            UNKNOWN
        }

    }

    data object Success : PresentationDocumentSubmissionPartialState()
}

interface PresentationRequestInteractor {
    val initiatorRoute: String
    fun getRequestDocuments(): Flow<PresentationRequestInteractorPartialState>
    fun stopPresentation()

    suspend fun rejectPresentation(): PresentationRejectionPartialState

    fun updateRequestedDocuments(items: List<RequestDocumentItemUi>)
    fun setConfig(config: RequestUriConfig)
    fun processRequest(): Flow<PresentationRequestProcessPartialState>
    suspend fun sendRequestedDocuments(): PresentationDocumentSubmissionPartialState
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
                is TransferEventPartialState.RequestReceived -> response.toPartialState()

                is TransferEventPartialState.RelyingPartyNotRegistered -> {
                    rejectRequest(
                        verifierName = null,
                        registration = response.relyingPartyRegistration,
                    ).copy(verifierAnswered = true)
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
                error = PresentationError.Unclassified(
                    traceId = null,
                    cause = it,
                    displayMessage = it.localizedMessage ?: genericErrorMsg,
                )
            )
        }

    private suspend fun TransferEventPartialState.RequestReceived.toPartialState() = when {
        // First, so a rejected request never reaches the consent screen.
        relyingPartyRegistration.rejectsRequest -> {
            rejectRequest(
                verifierName = verifierName,
                registration = relyingPartyRegistration,
            )
        }

        // Nothing in the wallet can satisfy the request. Note this is not the
        // same as "the verifier named no claims": per OpenID4VP §6.4.1 a
        // Credential Query without `claims` asks for a presentation that
        // discloses nothing beyond holding the credential, which is a request
        // the user still has to consent to.
        // An expired PID is never matched, so it lands here too; it is checked
        // first because holdsRequestedCredentials still counts it as held.
        requestData.isEmpty() && isMissingRequiredPid(requestedCredentialSets) ->
            PresentationRequestInteractorPartialState.NoData(
                verifierName = verifierName,
                verifierIsTrusted = verifierIsTrusted,
                missingPid = true,
            )

        requestData.isEmpty() && holdsRequestedCredentials(requestedCredentialSets) ->
            PresentationRequestInteractorPartialState.Unsatisfiable(
                verifierName = verifierName,
                verifierIsTrusted = verifierIsTrusted,
            )

        requestData.isEmpty() ->
            PresentationRequestInteractorPartialState.NoData(
                verifierName = verifierName,
                verifierIsTrusted = verifierIsTrusted,
            )

        else -> {
            // request data contains n number of files can be mDL, PID, this might change in the future
            val transformer = RequestTransformer()
            val requestDataUi = transformer.transformToDomainItems(
                storageDocuments = walletCoreDocumentsController.getAllIssuedDocuments(),
                requestMatches = requestData,
                resourceProvider = resourceProvider,
                logController = logController,
            )

            val documentsDomain = requestDataUi.getOrThrow()

            if (documentsDomain.isNotEmpty()) {
                PresentationRequestInteractorPartialState.Success(
                    verifierName = verifierName,
                    verifierIsTrusted = verifierIsTrusted,
                    requestDocuments = transformer.transformToUiItems(
                        documentsDomain = documentsDomain,
                        resourceProvider = resourceProvider,
                    ),
                    purpose = (relyingPartyRegistration
                            as? RelyingPartyRegistrationStatus.WithinRegisteredScope)
                        ?.purpose
                        ?.forLocale(resourceProvider.getLocale()),
                    reportUri = relyingPartyRegistration.reportUri,
                    supportUri = relyingPartyRegistration.supportUri,
                    serviceProviderUri = relyingPartyRegistration.serviceProviderUri,
                )
            } else {
                PresentationRequestInteractorPartialState.NoData(
                    verifierName = verifierName,
                    verifierIsTrusted = verifierIsTrusted,
                    missingPid = isMissingRequiredPid(requestedCredentialSets),
                )
            }
        }
    }

    /**
     * Whether a valid PID is all the wallet lacks: the wallet holds no PID or only expired ones,
     * the request cannot be satisfied without one (some required credential set has no option
     * that does without it), and adding one would satisfy every required set. When something else
     * is missing too, adding the Digital ID alone would not help, so it is not reported as a
     * missing PID.
     */
    private suspend fun isMissingRequiredPid(requestedSets: List<RequestedCredentialSet>?): Boolean {
        if (requestedSets.isNullOrEmpty()) return false
        val heldTypes = walletCoreDocumentsController.getAllIssuedDocuments()
            .mapNotNull { document ->
                document.toDocumentIdentifier()
                    .takeUnless { it.isPid && document.isExpired() }
            }
            .toSet()
        if (heldTypes.any { it.isPid }) return false

        val requiresPid = requestedSets.filter { it.required }.any { set ->
            set.options.isNotEmpty() && set.options.all { option ->
                option.any { acceptedTypes ->
                    acceptedTypes.isNotEmpty() && acceptedTypes.all { it.toDocumentIdentifier().isPid }
                }
            }
        }
        return requiresPid && requestedSets.areSatisfiedBy(heldTypes + PID_IDENTIFIERS)
    }

    /**
     * Whether the wallet holds, for every required credential set, all credential types of at
     * least one option, so that only the requested claims stand in the way.
     */
    private fun holdsRequestedCredentials(requestedSets: List<RequestedCredentialSet>?): Boolean {
        if (requestedSets.isNullOrEmpty()) return false
        val heldTypes = heldCredentialTypes()
        return requestedSets.areSatisfiedBy(heldTypes)
    }

    /**
     * Whether every credential of the document has expired. Not [IssuedDocument.getValidUntil]:
     * it only reads a credential that is valid now, so it fails for exactly these documents. A
     * document without credentials does not count as expired.
     */
    private suspend fun IssuedDocument.isExpired(): Boolean {
        val credentials = getCredentials()
        val now = Clock.System.now()
        return credentials.isNotEmpty() && credentials.all { it.validUntil < now }
    }

    /** The identifiers of the credentials the wallet holds. */
    private fun heldCredentialTypes(): Set<DocumentIdentifier> =
        walletCoreDocumentsController.getAllIssuedDocuments()
            .map { it.toDocumentIdentifier() }
            .toSet()

    /**
     * Whether credentials of [heldTypes] cover, for every required set, all credential types of
     * at least one option.
     */
    private fun List<RequestedCredentialSet>.areSatisfiedBy(
        heldTypes: Set<DocumentIdentifier>,
    ): Boolean =
        filter { it.required }.all { set ->
            set.options.any { option ->
                option.all { acceptedTypes ->
                    acceptedTypes.any { it.toDocumentIdentifier() in heldTypes }
                }
            }
        }

    /** Must not stop the presentation, that would cancel the flow still delivering this state. */
    private fun rejectRequest(
        verifierName: String?,
        registration: RelyingPartyRegistrationStatus,
    ): PresentationRequestInteractorPartialState.Rejected =
        PresentationRequestInteractorPartialState.Rejected(
            verifierName = verifierName,
            reason = when (registration) {
                is RelyingPartyRegistrationStatus.OverAsking -> RequestRejectionReason.OVER_ASKING
                else -> RequestRejectionReason.NOT_REGISTERED
            },
            supervisoryAuthorityUri = registration.reportUri,
        )

    override fun stopPresentation() {
        walletCorePresentationController.stopPresentation()
    }

    override suspend fun rejectPresentation(): PresentationRejectionPartialState =
        when (val result = walletCorePresentationController.rejectPresentation()) {
            is RejectPresentationPartialState.Redirect ->
                PresentationRejectionPartialState.Redirect(uri = result.uri)

            is RejectPresentationPartialState.Delivered ->
                PresentationRejectionPartialState.Delivered

            is RejectPresentationPartialState.Failure -> {
                logController.d(logTag) { "rejectPresentation: ${result.error}" }
                PresentationRejectionPartialState.Failure(error = result.error)
            }
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


    override suspend fun sendRequestedDocuments(): PresentationDocumentSubmissionPartialState {
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

                    else -> PresentationDocumentSubmissionPartialState.Failure.Unknown(
                        error = mapToPresentationError(
                            throwable = result.throwable,
                            traceId = result.traceId,
                            genericErrorMessage = genericErrorMsg,
                        ),
                    )
                }
            }
        }
    }

    override fun getWalletPinBlockTime(): String =
        walletPinUnBlockTimeStorageController.retrievePinTime()

    override fun isLastPinTry(): Boolean =
        walletPinUnBlockTimeStorageController.retrieveRemainingTries() == 1

    override suspend fun reissueLowBatchDocumentsIfNeeded(): String? {
        return try {
            var failureMessage: String? = null
            val completed = withTimeoutOrNull(REISSUE_TIMEOUT) {
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
            }

            if (completed == null) {
                "Batch reissue timed out after $REISSUE_TIMEOUT".also {
                    logController.d(logTag) { it }
                }
            } else {
                failureMessage?.also { logController.d(logTag) { "Batch reissue failed: $it" } }
            }
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

/** The formats a PID comes in; adding the Digital ID issues it in both. */
private val PID_IDENTIFIERS: Set<DocumentIdentifier> = setOf(
    DocumentIdentifier.MdocPid,
    DocumentIdentifier.SdJwtPid,
)
