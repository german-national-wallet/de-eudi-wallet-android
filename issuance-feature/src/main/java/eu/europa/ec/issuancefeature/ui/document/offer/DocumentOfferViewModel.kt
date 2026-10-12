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

package eu.europa.ec.issuancefeature.ui.document.offer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.commonfeature.config.IssuanceSuccessUiConfig
import eu.europa.ec.commonfeature.config.OfferCodeUiConfig
import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.commonfeature.config.PresentationMode.OpenId4Vp
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.model.DocumentType
import eu.europa.ec.commonfeature.ui.issuer_details.model.IssuerInfo
import eu.europa.ec.corelogic.di.getOrCreatePresentationScope
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.issuancefeature.interactor.document.DocumentOfferInteractor
import eu.europa.ec.issuancefeature.interactor.document.IssueDocumentsInteractorPartialState
import eu.europa.ec.issuancefeature.interactor.document.ResolveDocumentOfferInteractorPartialState
import eu.europa.ec.issuancefeature.interactor.document.ResolvedOffer
import eu.europa.ec.issuancefeature.ui.document.offer.transformer.DocumentOfferTransformer.toListItemDataList
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.RelyingPartyData
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.navigation.helper.hasDeepLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

data class State(
    val offerUiConfig: OfferUiConfig,

    val isLoading: Boolean = true,
    val loadingText: Int = R.string.eaa_presentation_loading_credential_title,
    val headerConfig: ContentHeaderConfig,
    val error: ContentErrorConfig? = null,
    val isInitialised: Boolean = false,
    val notifyOnAuthenticationFailure: Boolean = false,

    val documents: List<ListItemData> = emptyList(),
    /** The claim labels each offered document would add, keyed by document id. */
    val documentClaimLabels: Map<String, List<String>> = emptyMap(),
    val eaaCardDataMap: Map<String, EaaCardData> = emptyMap(),
    val noDocument: Boolean = false,
    val txCodeLength: Int? = null,

    /** The document whose claims are shown in the details bottom sheet; `null` while it is closed. */
    val detailsSheetDocumentId: String? = null,

    /**
     * The resolved offer, populated on successful [Event.Init].
     *
     * Held in state so the "Add" tap can re-use it instead of re-fetching
     * the offer URI. Cleared on failure. `null` until the first successful
     * resolve.
     */
    val resolvedOffer: ResolvedOffer? = null,
    val showRejectConfirmationDialog: Boolean = false,

    /**
     * `true` once the authorization-code flow's browser round-trip has completed
     * and issuance is paused waiting for the user's consent tap. Distinguishes a
     * genuine "ready for consent" state from one where authorization failed or
     * was abandoned — in the latter case the "Add" tap must restart the whole
     * flow (including reopening the browser) rather than try to resume nothing.
     */
    val awaitingConsent: Boolean = false,

    /**
     * `true` only while the authorization-code flow is waiting on the browser
     * (from the moment it is launched until it returns, successfully or not).
     * Overrides [Event.OnPause]'s usual "backgrounded -> stop loading" handling,
     * since backgrounding here just means the browser is on top — flipping
     * [isLoading] off would surface a stale, non-functional consent screen if
     * the user returns without finishing (or abandons) the browser session.
     */
    val browserAuthInProgress: Boolean = false,
) : ViewState

sealed class Event : ViewEvent {
    data class Init(val deepLink: Uri?) : Event()
    data object OnReject : Event()
    data object OnPause : Event()
    data class OnResumeIssuance(val uri: String) : Event()
    data class OnDynamicPresentation(val uri: String) : Event()
    data object DismissError : Event()

    data class StickyButtonPressed(val context: Context) : Event()
    data class ShowDocumentDetails(val documentId: String) : Event()
    data object DismissDocumentDetails : Event()
    data object ViewIssuerDetails : Event()
    data object DismissRejectConfirmationDialog : Event()
    data object RejectConfirmed : Event()
    data object HelpPressed : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(
            val screenRoute: String,
            val shouldPopToSelf: Boolean = true
        ) : Navigation()

        data class PopBackStackUpTo(
            val screenRoute: String,
            val inclusive: Boolean
        ) : Navigation()

        data object Pop : Navigation()

        data class DeepLink(
            val link: Uri,
            val routeToPop: String? = null
        ) : Navigation()

        data class NavigateToIssuerDetails(
            val issuerInfo: IssuerInfo
        ) : Navigation()
    }
}

@KoinViewModel
class DocumentOfferViewModel(
    private val documentOfferInteractor: DocumentOfferInteractor,
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer,
    @InjectedParam private val offerSerializedConfig: String,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
        val deserializedOfferUiConfig = uiSerializer.fromBase64(
            offerSerializedConfig,
            OfferUiConfig::class.java,
            OfferUiConfig.Parser
        ) ?: throw RuntimeException("OfferUiConfig:: is Missing or invalid")

        return State(
            offerUiConfig = deserializedOfferUiConfig,
            headerConfig = getInitialHeaderConfig(),
        )
    }

    override fun onCleared() {
        viewState.value.resolvedOffer?.let {
            documentOfferInteractor.clearCachedOffer(it.offerUri)
        }
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> {
                if (viewState.value.documents.isEmpty()) {
                    resolveDocumentOffer(
                        offerUri = viewState.value.offerUiConfig.offerURI,
                        deepLink = event.deepLink
                    )
                } else {
                    handleDeepLink(event.deepLink)
                    if (viewState.value.browserAuthInProgress) {
                        // The screen resumed while still waiting on the browser. This can
                        // only happen if the app came back to the foreground — either a
                        // genuine redirect (handled separately via VCI_RESUME_ACTION /
                        // Event.OnResumeIssuance) or the user navigating back out of the
                        // browser without finishing. Give the former a brief grace period
                        // before concluding it's the latter, so we don't race a real
                        // redirect that's still in flight.
                        recoverFromPossibleBrowserAbandonment()
                    }
                }
            }

            is Event.OnReject -> {
                setState { copy(error = null, showRejectConfirmationDialog = true) }
            }

            is Event.DismissError -> {
                setState { copy(error = null) }
            }

            is Event.StickyButtonPressed -> {
                val resolved = viewState.value.resolvedOffer
                if (resolved == null) {
                    // Nothing to issue — offer was never (successfully)
                    // resolved. Bail out to avoid a NPE or a stale fetch.
                    return
                }
                issueDocuments(
                    context = event.context,
                    resolvedOffer = resolved,
                    onSuccessNavigation = viewState.value.offerUiConfig.onSuccessNavigation,
                    txCodeLength = viewState.value.txCodeLength
                )
            }

            is Event.OnPause -> {
                if (viewState.value.isInitialised && !viewState.value.browserAuthInProgress) {
                    setState { copy(isLoading = false) }
                }
            }

            is Event.OnResumeIssuance -> {
                resumeIssuance(event.uri)
            }

            is Event.OnDynamicPresentation -> {
                getOrCreatePresentationScope()
                setEffect {
                    Effect.Navigation.SwitchScreen(
                        generateComposableNavigationLink(
                            PresentationScreens.PresentationRequest,
                            generateComposableArguments(
                                mapOf(
                                    RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                                        RequestUriConfig(
                                            OpenId4Vp(
                                                event.uri,
                                                IssuanceScreens.DocumentOffer.screenRoute
                                            )
                                        ),
                                        RequestUriConfig
                                    )
                                )
                            )
                        ),
                        shouldPopToSelf = false
                    )
                }
            }

            is Event.ShowDocumentDetails -> {
                setState { copy(detailsSheetDocumentId = event.documentId) }
            }

            is Event.DismissDocumentDetails -> {
                setState { copy(detailsSheetDocumentId = null) }
            }

            is Event.HelpPressed -> {
                // TODO: Will open the offer's help sheet
            }

            is Event.ViewIssuerDetails -> {
                navigateToIssuerDetails()
            }

            Event.DismissRejectConfirmationDialog -> {
                setState { copy(showRejectConfirmationDialog = false) }
            }

            Event.RejectConfirmed -> {
                setState { copy(showRejectConfirmationDialog = false, error = null) }
                doNavigation(viewState.value.offerUiConfig.onCancelNavigation)
            }
        }
    }

    private fun resolveDocumentOffer(offerUri: String, deepLink: Uri? = null) {
        setState {
            copy(
                isLoading = documents.isEmpty(),
                error = null
            )
        }
        viewModelScope.launch {
            documentOfferInteractor.resolveDocumentOffer(
                offerUri = offerUri
            ).collect { response ->
                when (response) {
                    is ResolveDocumentOfferInteractorPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                isInitialised = false,
                                resolvedOffer = null,
                                error = ContentErrorConfig(
                                    errorSubTitle = response.errorMessage,
                                    onCancel = {
                                        setEvent(Event.DismissError)
                                        doNavigation(viewState.value.offerUiConfig.onCancelNavigation)
                                    }
                                )
                            )
                        }
                    }

                    is ResolveDocumentOfferInteractorPartialState.Success -> {
                        val documentsList = response.resolvedOffer.documents.toListItemDataList()
                        val claimLabelsMap = response.resolvedOffer.documents.associate { item ->
                            item.id to item.details.map { (label, _) -> label }
                        }
                        val cardDataMap = response.resolvedOffer.documents
                            .mapNotNull { it.eaaCardData?.let { cd -> it.id to cd } }
                            .toMap()

                        setState {
                            copy(
                                // Authorization-code-flow offers stay in the loading state:
                                // the browser is about to open automatically, and consent is
                                // shown only after it returns (startAuthCodeFlowIssuance).
                                isLoading = response.resolvedOffer.isAuthorizationCodeFlow,
                                error = null,
                                documents = documentsList,
                                documentClaimLabels = claimLabelsMap,
                                eaaCardDataMap = cardDataMap,
                                isInitialised = true,
                                noDocument = false,
                                txCodeLength = response.resolvedOffer.txCodeLength,
                                resolvedOffer = response.resolvedOffer,
                                headerConfig = headerConfig.copy(
                                    relyingPartyData = getHeaderConfigIssuerData(
                                        issuerName = response.resolvedOffer.issuerName,
                                        issuerLogo = response.resolvedOffer.issuerLogo,
                                    )
                                ),
                            )
                        }

                        handleDeepLink(deepLink)

                        if (response.resolvedOffer.isAuthorizationCodeFlow) {
                            startAuthCodeFlowIssuance(response.resolvedOffer)
                        }
                    }

                    is ResolveDocumentOfferInteractorPartialState.NoDocument -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = null,
                                documents = emptyList(),
                                isInitialised = true,
                                noDocument = true,
                                headerConfig = headerConfig.copy(
                                    relyingPartyData = getHeaderConfigIssuerData(
                                        issuerName = response.issuerName,
                                        issuerLogo = response.issuerLogo,
                                    )
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getInitialHeaderConfig(): ContentHeaderConfig {
        return ContentHeaderConfig(
            title = resourceProvider.getString(R.string.eaa_issuance_eaa_info_title),
        )
    }

    private fun getHeaderConfigIssuerData(
        issuerName: String,
        issuerLogo: URI?,
    ): RelyingPartyData {
        return RelyingPartyData(
            logo = issuerLogo,
            isVerified = false,
            name = issuerName,
            description = resourceProvider.getString(R.string.issuance_document_offer_relying_party_description)
        )
    }

    private fun issueDocuments(
        context: Context,
        resolvedOffer: ResolvedOffer,
        onSuccessNavigation: ConfigNavigation,
        txCodeLength: Int?
    ) {
        viewModelScope.launch {

            if (resolvedOffer.isAuthorizationCodeFlow) {
                if (viewState.value.awaitingConsent) {
                    // Issuance was already started (and authorized) automatically by
                    // startAuthCodeFlowIssuance right after resolve; this tap is the
                    // user's consent confirmation, not a fresh issuance request.
                    continueAfterConsentConfirmed(context, resolvedOffer)
                } else {
                    // Authorization never reached the consent gate (it failed, or the
                    // browser session was abandoned) — there is nothing to continue,
                    // so restart the whole authorization-code flow from scratch.
                    startAuthCodeFlowIssuance(resolvedOffer)
                }
                return@launch
            }

            txCodeLength?.let {
                navigateToAdditionalStepScreen(
                    resolvedOffer.offerUri,
                    resolvedOffer.issuerName,
                    txCodeLength,
                    onSuccessNavigation
                )
                return@launch
            }

            setState {
                copy(
                    isLoading = true,
                    loadingText = R.string.eaa_issuance_loading_title,
                    error = null
                )
            }

            documentOfferInteractor.issueDocuments(
                offerUri = resolvedOffer.offerUri,
                issuerName = resolvedOffer.issuerName,
                navigation = onSuccessNavigation,
                txCode = null,
            ).collect { response ->
                when (response) {
                    is IssueDocumentsInteractorPartialState.Failure -> {
                        documentOfferInteractor.clearCachedOffer(resolvedOffer.offerUri)
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    errorSubTitle = response.errorMessage,
                                    onCancel = { setEvent(Event.DismissError) }
                                )
                            )
                        }
                    }

                    is IssueDocumentsInteractorPartialState.Success -> {
                        documentOfferInteractor.clearCachedOffer(resolvedOffer.offerUri)
                        setState {
                            copy(
                                isLoading = false,
                                error = null,
                            )
                        }

                        goToDocumentIssuanceSuccessScreen(
                            documentIds = response.documentIds,
                            onSuccessNavigation = ConfigNavigation(
                                navigationType = NavigationType.PushRoute(
                                    route = DashboardScreens.Dashboard.screenRoute,
                                    popUpToRoute = IssuanceScreens.DocumentIssuanceSuccess.screenRoute
                                )
                            ),
                            successTitle = resourceProvider.getString(R.string.eaa_issuance_success_title),
                            autoNavigateAfterMillis = 2_000L,
                        )
                    }

                    is IssueDocumentsInteractorPartialState.DeferredSuccess -> {
                        documentOfferInteractor.clearCachedOffer(resolvedOffer.offerUri)
                        setState {
                            copy(
                                isLoading = false,
                                error = null,
                            )
                        }

                        onNavigation(route = response.successRoute)
                    }

                    is IssueDocumentsInteractorPartialState.UserAuthRequired -> {
                        documentOfferInteractor.handleUserAuthentication(
                            context = context,
                            crypto = response.crypto,
                            notifyOnAuthenticationFailure = viewState.value.notifyOnAuthenticationFailure,
                            resultHandler = response.resultHandler
                        )
                    }

                    is IssueDocumentsInteractorPartialState.OnRefreshTokenReceived -> {
                        /* nothing to do here */
                    }

                    is IssueDocumentsInteractorPartialState.OnCNonce -> {
                        /* nothing to do here */
                    }

                    is IssueDocumentsInteractorPartialState.AwaitingConsent -> {
                        /* Not expected here: this collect only runs for offers where
                           isAuthorizationCodeFlow is false, which never gate on consent. */
                    }
                }
            }
        }
    }

    /**
     * Starts (or restarts) issuance for the authorization-code flow, without
     * waiting for a consent tap — so the browser opens right away. Called both
     * automatically on offer resolve, and as a retry after a failed/abandoned
     * attempt, so it sets its own loading/error state rather than relying on
     * the caller to have done so.
     *
     * Stops as soon as authorization completes and issuance is paused for
     * consent (or fails before reaching that point); the terminal outcome
     * (Success/Failure/DeferredSuccess) is handled later, once the user
     * confirms consent, by [issueDocuments]'s authorization-code-flow branch.
     */
    private fun startAuthCodeFlowIssuance(resolvedOffer: ResolvedOffer) {
        setState {
            copy(
                isLoading = true,
                error = null,
                awaitingConsent = false,
                browserAuthInProgress = true,
            )
        }
        viewModelScope.launch {
            documentOfferInteractor.issueDocuments(
                offerUri = resolvedOffer.offerUri,
                issuerName = resolvedOffer.issuerName,
                navigation = viewState.value.offerUiConfig.onSuccessNavigation,
                txCode = null,
            ).first { response ->
                when (response) {
                    is IssueDocumentsInteractorPartialState.AwaitingConsent -> {
                        setAwaitingConsent()
                        true
                    }

                    is IssueDocumentsInteractorPartialState.Failure -> {
                        documentOfferInteractor.clearCachedOffer(resolvedOffer.offerUri)
                        setState {
                            copy(
                                isLoading = false,
                                awaitingConsent = false,
                                browserAuthInProgress = false,
                                error = authCodeFlowRetryableError(
                                    message = response.errorMessage,
                                    resolvedOffer = resolvedOffer,
                                    leaveScreenOnCancel = true,
                                )
                            )
                        }
                        true
                    }

                    // Success/DeferredSuccess/UserAuthRequired cannot occur before the
                    // consent gate — DocumentRequiresCreateSettings always fires first.
                    else -> false
                }
            }
        }
    }

    /**
     * Waits [BROWSER_ABANDON_GRACE_PERIOD_MS] and, if no redirect has been
     * confirmed by then, concludes the user backed out of the browser (e.g. via
     * device back) without finishing — no redirect is ever coming, so nothing
     * would otherwise unblock the loading screen. Surfaces a retryable error
     * instead of leaving the user stuck.
     *
     * This is purely a "was there ever a redirect" heuristic, not a bound on
     * how long authorization may legitimately take: [resumeIssuance] clears
     * [State.browserAuthInProgress] the moment a redirect is confirmed (rather
     * than waiting for the authorization-code/token exchange it kicks off to
     * finish), so this check — whether already scheduled or one armed later,
     * while that exchange is still running — reliably observes it as `false`
     * and no-ops, no matter how long the exchange takes.
     */
    private fun recoverFromPossibleBrowserAbandonment() {
        val resolvedOffer = viewState.value.resolvedOffer ?: return
        viewModelScope.launch {
            delay(BROWSER_ABANDON_GRACE_PERIOD_MS.milliseconds)
            if (viewState.value.browserAuthInProgress) {
                setState {
                    copy(
                        isLoading = false,
                        awaitingConsent = false,
                        browserAuthInProgress = false,
                        error = authCodeFlowRetryableError(
                            message = resourceProvider.getString(R.string.issuance_interrupted_error),
                            resolvedOffer = resolvedOffer,
                            leaveScreenOnCancel = true,
                        )
                    )
                }
            }
        }
    }

    /**
     * Confirms consent for an already-authorized authorization-code-flow offer:
     * resumes the issuance that [startAuthCodeFlowIssuance] paused at the
     * consent gate, and awaits its terminal outcome.
     *
     * Bounded by [SUBMIT_ISSUANCE_TIMEOUT_MS] — without it, a stalled credential
     * submission (network hang, lost event) would leave the screen loading
     * forever with no retry.
     *
     * Credential keys of EAAs require user authentication, so the issuance
     * typically pauses with [IssueDocumentsInteractorPartialState.UserAuthRequired]
     * before the outcome arrives. The biometric prompt is shown for it, and the
     * time the user spends on the prompt is excluded from the timeout: the
     * timeout restarts once the prompt has been answered.
     */
    private suspend fun continueAfterConsentConfirmed(
        context: Context,
        resolvedOffer: ResolvedOffer,
    ) {
        setState {
            copy(
                isLoading = true,
                loadingText = R.string.eaa_issuance_loading_title,
                error = null,
                awaitingConsent = false,
            )
        }
        documentOfferInteractor.continueIssuanceAfterConsent()

        // issuanceState replays its latest value, so after the prompt the same
        // UserAuthRequired may be seen again until the next state is published.
        // Remember the one already handled to avoid prompting twice for it.
        var handledAuthRequest: IssueDocumentsInteractorPartialState.UserAuthRequired? = null
        while (true) {
            val update = withTimeoutOrNull(SUBMIT_ISSUANCE_TIMEOUT_MS.milliseconds) {
                documentOfferInteractor.issuanceState.first { response ->
                    if (response is IssueDocumentsInteractorPartialState.UserAuthRequired) {
                        response != handledAuthRequest
                    } else {
                        applyIssuanceUpdate(response, resolvedOffer)
                    }
                }
            }

            when (update) {
                null -> {
                    setState {
                        copy(
                            isLoading = false,
                            error = authCodeFlowRetryableError(
                                message = resourceProvider.getString(R.string.issuance_interrupted_error),
                                resolvedOffer = resolvedOffer,
                            )
                        )
                    }
                    return
                }

                is IssueDocumentsInteractorPartialState.UserAuthRequired -> {
                    handledAuthRequest = update
                    authenticateForIssuance(context, update)
                }

                // applyIssuanceUpdate already handled the terminal state.
                else -> return
            }
        }
    }

    /**
     * Shows the device authentication prompt for [request] and suspends until
     * the user has answered it.
     *
     * A cancelled prompt, or biometrics being unavailable, cancels the
     * issuance; wallet-core then publishes a Failure, which the caller turns
     * into a retryable error.
     */
    private suspend fun authenticateForIssuance(
        context: Context,
        request: IssueDocumentsInteractorPartialState.UserAuthRequired,
    ) {
        val answered = CompletableDeferred<Unit>()
        val handler = request.resultHandler
        documentOfferInteractor.handleUserAuthentication(
            context = context,
            crypto = request.crypto,
            // With false, onAuthenticationFailure is only invoked when biometrics
            // are unavailable, never for a single failed attempt on the prompt.
            notifyOnAuthenticationFailure = false,
            resultHandler = DeviceAuthenticationResult(
                onAuthenticationSuccess = {
                    try {
                        handler.onAuthenticationSuccess()
                    } finally {
                        answered.complete(Unit)
                    }
                },
                onAuthenticationError = {
                    try {
                        handler.onAuthenticationError()
                    } finally {
                        answered.complete(Unit)
                    }
                },
                onAuthenticationFailure = {
                    try {
                        handler.onAuthenticationError()
                    } finally {
                        answered.complete(Unit)
                    }
                },
            )
        )
        answered.await()
    }

    /** Marks the authorization-code flow as ready for the user's consent. */
    private fun setAwaitingConsent() {
        setState {
            copy(
                isLoading = false,
                error = null,
                awaitingConsent = true,
                browserAuthInProgress = false,
            )
        }
    }

    /**
     * Builds the error shown when the authorization-code flow can't proceed.
     * Retrying always restarts the whole flow (including reopening the browser)
     * — once authorization has been attempted there is no partial state left to
     * resume, only [resolvedOffer] is needed to try again. `resolvedOffer` is
     * `null` only for the resume-timeout path when the offer isn't known yet,
     * which simply omits the retry action.
     *
     * [leaveScreenOnCancel] additionally backs out of the offer entirely on
     * cancel, for failures the user hasn't seen any consent UI for yet.
     */
    private fun authCodeFlowRetryableError(
        message: String,
        resolvedOffer: ResolvedOffer?,
        leaveScreenOnCancel: Boolean = false,
    ): ContentErrorConfig = ContentErrorConfig(
        errorSubTitle = message,
        onRetry = resolvedOffer?.let { offer -> { startAuthCodeFlowIssuance(offer) } },
        onCancel = {
            setEvent(Event.DismissError)
            if (leaveScreenOnCancel) {
                doNavigation(viewState.value.offerUiConfig.onCancelNavigation)
            }
        }
    )

    /**
     * Resumes an in-flight authorization-code-flow issuance after the browser
     * redirect returns (or a warm-resumed screen re-discovers it via the
     * persistent [DocumentOfferInteractor.issuanceState]). Times out if nothing
     * arrives, since the redirect may have been lost (e.g. process death mid-flow).
     *
     * Being called at all is definitive proof a redirect arrived (this is only
     * ever invoked from the VCI_RESUME_ACTION broadcast), so it clears
     * [State.browserAuthInProgress] up front rather than waiting for the
     * authorization-code/token exchange it kicks off to finish.
     * That exchange can take longer than [BROWSER_ABANDON_GRACE_PERIOD_MS] on a slow network
     * without [recoverFromPossibleBrowserAbandonment] ever mistaking it for the
     * user having abandoned the browser.
     */
    private fun resumeIssuance(uri: String) {
        setState {
            copy(
                isLoading = true,
                loadingText = R.string.eaa_issuance_loading_title,
                browserAuthInProgress = false,
            )
        }
        documentOfferInteractor.resumeOpenId4VciWithAuthorization(uri)
        val resolvedOffer = viewState.value.resolvedOffer
        viewModelScope.launch {
            try {
                withTimeout(RESUME_ISSUANCE_TIMEOUT_MS.milliseconds) {
                    documentOfferInteractor.issuanceState.first { response ->
                        resolvedOffer?.let { applyIssuanceUpdate(response, it) } ?: false
                    }
                }
            } catch (e: TimeoutCancellationException) {
                setState {
                    copy(
                        isLoading = false,
                        browserAuthInProgress = false,
                        error = authCodeFlowRetryableError(
                            message = resourceProvider.getString(R.string.issuance_interrupted_error),
                            resolvedOffer = resolvedOffer,
                        )
                    )
                }
            }
        }
    }

    /**
     * Applies a terminal (or consent-gate) issuance update, shared by
     * [Event.OnResumeIssuance] and the post-consent branch of [issueDocuments].
     * Returns `true` once [response] represents a stopping point for a
     * `.first {}` await, `false` to keep waiting.
     */
    private fun applyIssuanceUpdate(
        response: IssueDocumentsInteractorPartialState,
        resolvedOffer: ResolvedOffer,
    ): Boolean {
        return when (response) {
            is IssueDocumentsInteractorPartialState.AwaitingConsent -> {
                setAwaitingConsent()
                true
            }

            is IssueDocumentsInteractorPartialState.Success -> {
                setState { copy(isLoading = false, error = null, awaitingConsent = false) }
                goToDocumentIssuanceSuccessScreen(
                    documentIds = response.documentIds,
                    onSuccessNavigation = ConfigNavigation(
                        navigationType = NavigationType.PushRoute(
                            route = DashboardScreens.Dashboard.screenRoute,
                            popUpToRoute = IssuanceScreens.DocumentIssuanceSuccess.screenRoute
                        )
                    ),
                    successTitle = resourceProvider.getString(R.string.eaa_issuance_success_title),
                    autoNavigateAfterMillis = 2_000L,
                )
                true
            }

            is IssueDocumentsInteractorPartialState.Failure -> {
                // continueIssuanceAfterConsent already consumed the pending resume, so
                // authCodeFlowRetryableError's retry restarts the whole flow — there is
                // nothing left to resume.
                setState {
                    copy(
                        isLoading = false,
                        awaitingConsent = false,
                        error = authCodeFlowRetryableError(
                            message = response.errorMessage,
                            resolvedOffer = resolvedOffer,
                        )
                    )
                }
                true
            }

            is IssueDocumentsInteractorPartialState.DeferredSuccess -> {
                setState { copy(isLoading = false, error = null, awaitingConsent = false) }
                onNavigation(route = response.successRoute)
                true
            }

            else -> false
        }
    }

    private fun goToDocumentIssuanceSuccessScreen(
        documentIds: List<DocumentId>,
        onSuccessNavigation: ConfigNavigation,
        successTitle: String? = null,
        autoNavigateAfterMillis: Long = 0L,
    ) {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = IssuanceScreens.DocumentIssuanceSuccess,
                    arguments = generateComposableArguments(
                        mapOf(
                            IssuanceSuccessUiConfig.serializedKeyName to uiSerializer.toBase64(
                                model = IssuanceSuccessUiConfig(
                                    documentIds = documentIds,
                                    onSuccessNavigation = onSuccessNavigation,
                                    successTitle = successTitle,
                                    autoNavigateAfterMillis = autoNavigateAfterMillis,
                                ),
                                parser = IssuanceSuccessUiConfig.Parser
                            ).orEmpty()
                        )
                    )
                )
            )
        }
    }

    private fun onNavigation(route: String) {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = route
            )
        }
    }

    private fun doNavigation(navigation: ConfigNavigation) {
        val navigationEffect: Effect.Navigation = when (val nav = navigation.navigationType) {
            is NavigationType.PopTo -> {
                Effect.Navigation.PopBackStackUpTo(
                    screenRoute = nav.screen.screenRoute,
                    inclusive = false
                )
            }

            is NavigationType.PushScreen -> {
                Effect.Navigation.SwitchScreen(
                    generateComposableNavigationLink(
                        screen = nav.screen,
                        arguments = generateComposableArguments(nav.arguments),
                    )
                )
            }

            is NavigationType.Deeplink -> Effect.Navigation.DeepLink(
                nav.link.toUri(),
                nav.routeToPop
            )

            is NavigationType.Pop, NavigationType.Finish -> Effect.Navigation.Pop

            is NavigationType.PushRoute -> Effect.Navigation.SwitchScreen(nav.route)
        }

        setEffect {
            navigationEffect
        }
    }

// EUDI-Removed
/*
    private fun navigateToOfferCodeScreen(
        offerUri: String,
        issuerName: String,
        txCodeLength: Int,
        onSuccessNavigation: ConfigNavigation
    ) {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    IssuanceScreens.DocumentOfferCode,
                    getNavigateOfferCodeScreenArguments(
                        offerUri,
                        issuerName,
                        txCodeLength,
                        onSuccessNavigation
                    )
                ),
                shouldPopToSelf = false
            )
        }
    }
*/

    private fun navigateToAdditionalStepScreen(
        offerUri: String,
        issuerName: String,
        txCodeLength: Int,
        onSuccessNavigation: ConfigNavigation
    ) {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    IssuanceScreens.AdditionalStep,
                    getNavigateOfferCodeScreenArguments(
                        offerUri,
                        issuerName,
                        txCodeLength,
                        onSuccessNavigation
                    )
                ),
                shouldPopToSelf = false,
            )
        }
    }

    private fun navigateToIssuerDetails() {
        val relyingPartyData = viewState.value.headerConfig.relyingPartyData ?: return

        val issuerInfo = IssuerInfo(
            issuerName = relyingPartyData.name,
            logoUri = relyingPartyData.logo?.toString(),
            documentType = DocumentType.EAA,
        )

        setEffect {
            Effect.Navigation.NavigateToIssuerDetails(issuerInfo)
        }
    }

    private fun getNavigateOfferCodeScreenArguments(
        offerUri: String,
        issuerName: String,
        txCodeLength: Int,
        onSuccessNavigation: ConfigNavigation
    ): String {
        return generateComposableArguments(
            mapOf(
                OfferCodeUiConfig.serializedKeyName to uiSerializer.toBase64(
                    OfferCodeUiConfig(
                        offerURI = offerUri,
                        txCodeLength = txCodeLength,
                        issuerName = issuerName,
                        onSuccessNavigation = onSuccessNavigation
                    ),
                    OfferCodeUiConfig.Parser
                ).orEmpty()
            )
        )
    }

    private fun handleDeepLink(deepLinkUri: Uri?) {
        deepLinkUri?.let { uri ->
            hasDeepLink(uri)?.let {
                when (it.type) {

                    DeepLinkType.EXTERNAL -> {
                        setEffect {
                            Effect.Navigation.DeepLink(uri)
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

private const val RESUME_ISSUANCE_TIMEOUT_MS = 5_000L
private const val BROWSER_ABANDON_GRACE_PERIOD_MS = 800L

// Larger than RESUME_ISSUANCE_TIMEOUT_MS: this bounds real credential creation
// and submission work (key generation, network calls to the issuer), not just
// waiting for a browser-redirect message to arrive.
private const val SUBMIT_ISSUANCE_TIMEOUT_MS = 20_000L
