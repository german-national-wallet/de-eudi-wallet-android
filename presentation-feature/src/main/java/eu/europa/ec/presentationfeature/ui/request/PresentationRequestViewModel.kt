package eu.europa.ec.presentationfeature.ui.request

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.jwt.InstantProvider
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import eu.europa.ec.commonfeature.interactor.StartPinSessionResult
import eu.europa.ec.commonfeature.model.DocumentType
import eu.europa.ec.commonfeature.ui.request.Effect
import eu.europa.ec.commonfeature.ui.request.Event
import eu.europa.ec.commonfeature.ui.request.RequestScreenStep
import eu.europa.ec.commonfeature.ui.request.RequestScreenStep.TemporaryPinBlocked
import eu.europa.ec.commonfeature.ui.request.RequestViewModel
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentClaim
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.corelogic.di.AppCoroutineScope
import eu.europa.ec.corelogic.di.closePresentationScope
import eu.europa.ec.presentationfeature.interactor.PresentationDocumentSubmissionPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationRejectionPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestProcessPartialState
import eu.europa.ec.presentationfeature.interactor.RequestRejectionReason
import org.sprind.wallet.presentationfeature.error.BlockingErrorReason
import org.sprind.wallet.presentationfeature.error.PresentationErrorUiConfig
import org.sprind.wallet.presentationfeature.error.presentationErrorRoute
import org.sprind.wallet.presentationfeature.interactor.PidDeletionInteractor
import org.sprind.wallet.commonfeature.interactor.DocumentDeletionPartialState
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.businesslogic.model.UserPin
import org.sprind.wallet.businesslogic.util.SpanAttributes
import org.sprind.wallet.networklogic.connectivity.ConnectivityChecker
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType
import org.sprind.wallet.uilogic.component.cleared
import org.sprind.wallet.uilogic.component.dialog.offlineErrorDialogConfig
import org.sprind.wallet.uilogic.extension.isSafeRedirectLink
import org.sprind.wallet.corelogic.presentationerror.PresentationError
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@KoinViewModel
class PresentationRequestViewModel(
    private val presentationRequestInteractor: PresentationRequestInteractor,
    private val pidDeletionInteractor: PidDeletionInteractor,
    override val resourceProvider: ResourceProvider,
    override val uiSerializer: UiSerializer,
    private val rwscaPinHandler: RwscaPinHandler,
    private val instantProvider: InstantProvider,
    private val telemetry: Telemetry,
    private val appCoroutineScope: AppCoroutineScope,
    private val configLogic: ConfigLogic,
    private val connectivityChecker: ConnectivityChecker,
    @InjectedParam private val requestUriConfigRaw: String,
) : RequestViewModel(resourceProvider, uiSerializer, instantProvider) {

    private var unBlockTimerJob: Job? = null
    private var isNavigatingToPresentationContinuation = false
    private var isPresentationStopped = false

    private val requestUriConfig: RequestUriConfig by lazy {
        uiSerializer.fromBase64(
            requestUriConfigRaw,
            RequestUriConfig::class.java,
            RequestUriConfig.Parser
        ) ?: throw RuntimeException("RequestUriConfig:: is Missing or invalid")
    }

    override fun onStickyButtonPressed() {
        if (shouldEnterWalletPin()) {
            setEvent(Event.ShowPinView)
        } else {
            runWhenOnline(action = ::navigateToPresentationLoading)
        }
    }

    override fun onCredentialPreviewContinue() {
        if (shouldEnterWalletPin()) {
            setEvent(Event.ShowPinView)
        } else {
            runWhenOnline(action = ::navigateToPresentationLoading)
        }
    }

    /**
     * WD-4685: starting the PIN session needs the rWSCA backend, also for a BLE presentation.
     * Offline, the offline dialog explains why the PIN was not submitted.
     */
    override fun canSubmitPin(): Boolean {
        if (connectivityChecker.isOnline()) return true
        showPinOffline()
        return false
    }

    override fun getHeaderConfig(): ContentHeaderConfig {
        return ContentHeaderConfig(
            description = resourceProvider.getString(R.string.pid_presentation_rp_info_paragraph_1) + "\n\n" + resourceProvider.getString(
                R.string.pid_presentation_rp_info_paragraph_2
            ),
            importantInformationAction = {
                showBottomSheet(
                    getBottomSheetContentForStep(
                        RequestScreenStep.CredentialPreview
                    )
                )
            }
        )
    }

    override fun getNextScreen(): String {
        return PresentationScreens.PresentationSuccess.screenRoute
    }

    override fun doWork() {
        // WD-4685: fetching an OpenID4VP request needs the internet; closing leaves the screen.
        if (needsInternetForRequest() && !connectivityChecker.isOnline()) {
            showOfflineDialog(
                onRetry = ::doWork,
                onClose = {
                    setEvent(Event.DismissError)
                    setEvent(Event.Pop)
                },
            )
            return
        }

        setState {
            copy(
                isLoading = true,
                error = null,
                onContinueAction = { setEvent(Event.CredentialDetailsView) },
                onSecurityInfoClicked = {
                    showBottomSheet(
                        getBottomSheetContentForStep(
                            RequestScreenStep.EnterPin
                        )
                    )
                }
            )
        }

        telemetry.startSpan(
            spanName = TelemetryConstants.PRESENTATION,
            initialAttributes = SpanAttributes(mapOf("presentation_request" to requestUriConfig.toString()))
        )
        presentationRequestInteractor.setConfig(requestUriConfig)

        viewModelJob = viewModelScope.launch {
            presentationRequestInteractor.getRequestDocuments().collect { response ->
                when (response) {
                    is PresentationRequestInteractorPartialState.Failure -> {
                        showRequestLoadError(response.error)
                    }

                    is PresentationRequestInteractorPartialState.Success -> {
                        updateData(response.requestDocuments)

                        val updatedHeaderConfig = viewState.value.headerConfig.copy(
                            title = resourceProvider.getString(R.string.pid_presentation_rp_info_title),
                            subTitle = response.verifierName,
                        )

                        setState {
                            val claimItems = sortByPriority(response.requestDocuments)
                            val claimItemLabels = claimItems.map {
                                it.withoutDetailLabel
                            }.distinct()
                            copy(
                                isLoading = false,
                                error = null,
                                headerConfig = updatedHeaderConfig,
                                claimItems = claimItems,
                                claimItemLabels = claimItemLabels,
                                onContinueAction = getContinueActionForStep(RequestScreenStep.Initial),
                                verifierName = response.verifierName,
                                purpose = response.purpose,
                                reportUrl = response.reportUri,
                                supportUrl = response.supportUri,
                                serviceProviderUrl = response.serviceProviderUri,
                            )
                        }
                    }

                    is PresentationRequestInteractorPartialState.Disconnect -> {
                        setEvent(Event.Pop)
                    }

                    is PresentationRequestInteractorPartialState.NoData -> {
                        declineUnmatchedRequest { _, traceId ->
                            val reason = if (response.missingPid) {
                                BlockingErrorReason.MISSING_PID
                            } else {
                                BlockingErrorReason.MISSING_CREDENTIAL
                            }
                            doNavigation(
                                NavigationType.PushRoute(
                                    presentationErrorRoute(reason = reason, traceId = traceId)
                                )
                            )
                        }
                    }

                    is PresentationRequestInteractorPartialState.Unsatisfiable -> {
                        declineUnmatchedRequest(::showUnsatisfiableRequestError)
                    }

                    is PresentationRequestInteractorPartialState.Rejected -> {
                        rejectOnUsersBehalf(response)
                    }
                }
            }
        }
    }

    /** Answers `access_denied` unless already answered, then tears down and shows the screen. */
    private fun rejectOnUsersBehalf(rejection: PresentationRequestInteractorPartialState.Rejected) {
        viewModelScope.launch {
            val outcome = if (rejection.verifierAnswered) {
                null
            } else {
                presentationRequestInteractor.rejectPresentation()
            }

            telemetry.endSpan(TelemetryConstants.PRESENTATION)
            stopPresentation()

            doNavigation(
                NavigationType.PushRoute(
                    rejectedScreenRoute(
                        reason = rejection.reason,
                        reportUri = rejection.supervisoryAuthorityUri,
                        redirectUri = (outcome as? PresentationRejectionPartialState.Redirect)
                            ?.uri?.toString(),
                    )
                )
            )
        }
        unsubscribe()
    }

    // The URIs are encoded so their own query strings are not read as route arguments.
    private fun rejectedScreenRoute(
        reason: RequestRejectionReason,
        reportUri: String?,
        redirectUri: String?,
    ): String {
        val paramKey = PresentationScreens.PresentationRequestRejected.ParamKey
        return PresentationScreens.PresentationRequestRejected.screenRoute
            .replace("{${paramKey.REJECTION_REASON}}", reason.name)
            .replace("{${paramKey.REPORT_URI}}", Uri.encode(reportUri.orEmpty()))
            .replace("{${paramKey.REDIRECT_URI}}", Uri.encode(redirectUri.orEmpty()))
    }

    private fun sortByPriority(requestDocuments: List<RequestDocumentItemUi>): List<RequestDocumentClaim> {
        // Read the payload off the document row rather than its first claim row: a Credential
        // Query without `claims` (OpenID4VP §6.4.1) produces a document with no rows at all.
        val docs = requestDocuments.firstOrNull()?.domainPayload?.docClaimsDomain.orEmpty()
        //TODO to confirm this
        val priority = listOf(
            "family_name",
            "family_name_birth",
            "given_name",
            "nationality",
            "birth_date",
            "birth_city",
            "age_birth_year",
            "issuing_authority",
            "issuing_country",
            "issuance_date",
            "expiry_date"
        )
        val (important, others) = docs.partition { it.elementIdentifier in priority }

        val reordered = priority.mapNotNull { id ->
            important.find { it.elementIdentifier == id }
        } + others
        return reordered
    }

    /**
     * PID credentials use rWSCA and must start a wallet PIN session before presentation.
     */
    private fun shouldEnterWalletPin(): Boolean =
        viewState.value.items
            .any { it.domainPayload.documentType == DocumentType.PID }

    override fun updateData(
        updatedItems: List<RequestDocumentItemUi>,
    ) {
        super.updateData(updatedItems)
        presentationRequestInteractor.updateRequestedDocuments(updatedItems)
    }

    override fun getHeaderForStep(step: RequestScreenStep): ContentHeaderConfig {
        val current = viewState.value.headerConfig
        return when (step) {
            RequestScreenStep.Initial -> current.copy(
                title = resourceProvider.getString(R.string.pid_presentation_rp_info_title),
                subTitle = viewState.value.verifierName,
                description = resourceProvider.getString(R.string.pid_presentation_rp_info_paragraph_1) + "\n\n" + resourceProvider.getString(
                    R.string.pid_presentation_rp_info_paragraph_2
                ),
                purposeText = null,
            )

            RequestScreenStep.CredentialPreview -> current.copy(
                title = resourceProvider.getString(R.string.pid_presentation_data_consent_title),
                subTitle = null,
                purposeText = null,
                description = null,
                importantInformationAction = {
                    showBottomSheet(
                        getBottomSheetContentForStep(step)
                    )
                }
            )

            RequestScreenStep.EnterPin -> current.copy(
                title = resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_title),
                subTitle = null,
                purposeText = null,
                description = null,
            )

            is RequestScreenStep.TemporaryPinBlocked -> current.copy(
                title = resourceProvider.getString(R.string.pid_presentation_retry_counter_counter_title),
                subTitle = null,
                purposeText = null,
                description = null,
            )

            RequestScreenStep.DocumentDeletedConfirmation -> current.copy(
                title = resourceProvider.getString(R.string.pid_presentation_retry_counter_reset_success),
            )

            else -> current
        }
    }

    override suspend fun processRequest(pin: UserPin) {
        val pinSessionResult = rwscaPinHandler.startPinSession(pin)
        if (pinSessionResult is StartPinSessionResult.Failure) {
            handlePinSessionFailure(pinSessionResult)
            return
        }

        presentationRequestInteractor.processRequest().collect {
            when (it) {
                is PresentationRequestProcessPartialState.Failure -> {
                    rwscaPinHandler.clearPinSession()
                    telemetry.endSpan(TelemetryConstants.PRESENTATION)
                    // WD-4273: no retry after a failed dispatch to the verifier.
                    // The PIN session is gone and re-collecting the shared
                    // transfer events would replay this very failure, so the
                    // dialog only dismisses.
                    showPresentationError(
                        error = it.error,
                        onRetry = null,
                    )
                }

                is PresentationRequestProcessPartialState.Redirect -> {
                    if (isNavigatingToPresentationContinuation) return@collect
                    isNavigatingToPresentationContinuation = true
                    telemetry.endSpan(TelemetryConstants.PRESENTATION)

                    val initiator = presentationRequestInteractor.initiatorRoute
                    val isDynamicIssuance = initiator == IssuanceScreens.DocumentOffer.screenRoute
                        || initiator == IssuanceScreens.AddDocument.screenRoute

                    refreshBatchBeforeLeaving(isDynamicIssuance)

                    doNavigation(
                        NavigationType.Deeplink(
                            link = it.uri.toString(),
                            routeToPop = null
                        )
                    )
                    if (isDynamicIssuance) {
                        val initiatorScreen = when (initiator) {
                            IssuanceScreens.DocumentOffer.screenRoute -> IssuanceScreens.DocumentOffer
                            IssuanceScreens.AddDocument.screenRoute -> IssuanceScreens.AddDocument
                            else -> null
                        }
                        if (initiatorScreen != null) {
                            doNavigation(NavigationType.PopTo(initiatorScreen))
                        } else {
                            doNavigation(NavigationType.PushRoute(DashboardScreens.Dashboard.screenRoute))
                        }
                    } else {
                        doNavigation(NavigationType.PushRoute(DashboardScreens.Dashboard.screenRoute))
                    }
                }

                PresentationRequestProcessPartialState.RequestReadyToBeSent -> {
                    sendRequestedDocuments()
                }

                PresentationRequestProcessPartialState.Success -> {
                    if (isNavigatingToPresentationContinuation) return@collect
                    isNavigatingToPresentationContinuation = true
                    telemetry.endSpan(TelemetryConstants.PRESENTATION)
                    // See refreshBatchBeforeLeaving(). No teardown here: the success screen's
                    // ViewModel closes the presentation scope when it goes away.
                    presentationRequestInteractor.reissueLowBatchDocumentsIfNeeded()
                    //Navigate to Presentation Success
                    doNavigation(NavigationType.PushRoute(getNextScreen()))
                }

                is PresentationRequestProcessPartialState.UserAuthenticationRequired -> {
                    navigateToPresentationLoading()
                }
            }
        }
    }


    private fun handlePinSessionFailure(result: StartPinSessionResult.Failure) {
        // TODO(WD-2773): Improve error handling and recovery.
        rwscaPinHandler.clearPinSession()
        val error = result.error
        when {
            // WD-4685: no answer from rWSCA or MDVM, so the PIN must not be reported as wrong.
            error.isNoInternet -> showPinOffline()
            error is RwscaError.FromRwsca && error.type == RwscaErrorType.PIN_VERIFICATION_FAILED -> setState {
                copy(
                    isLoading = false,
                    error = null,
                    pinState = pinState.cleared(
                        supportingText = resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin),
                    ),
                )
            }
            error is RwscaError.FromRwsca && error.type == RwscaErrorType.ACCOUNT_LOCKED -> showPidLocked()
            error is RwscaError.FromRwsca && error.type == RwscaErrorType.PIN_RETRY_BLOCKED -> {
                val triesRemaining = error.serverResponse?.tryCounter ?: 0
                val unblockTime = error.serverResponse?.tryAllowedAfter ?: ""
                val step = TemporaryPinBlocked(isLastTry = triesRemaining == 1)
                setState {
                    copy(
                        error = null,
                        isLoading = false,
                        currentStep = step,
                        headerConfig = getHeaderForStep(step),
                        onContinueAction = getContinueActionForStep(step),
                        pinState = pinState.cleared(),
                        sheetContent = getBottomSheetContentForStep(step),
                        unBlockRemainingTime = formatTime(unblockTime),
                    )
                }
                startUnblockTimer(unblockTime)
            }
            else -> setState {
                copy(
                    isLoading = false,
                    errorDialog = GenericErrorDialogConfig(
                        titleRes = R.string.global_error_title,
                        bodyTextRes = R.string.global_error_paragraph,
                        errorCode = error.code,
                        traceId = error.traceId,
                        primaryButtonTextRes = R.string.global_error_prim_button,
                        onDismiss = { setEvent(Event.DismissError) },
                        onPrimaryButtonClick = { setEvent(Event.DismissError) },
                        onSecondaryButtonClick = {},
                    ),
                )
            }
        }
    }

    private fun retrySendRequestedDocuments() {
        // WD-4685: closing the offline dialog returns to this failure, with its error code,
        // trace ID and Retry, instead of leaving the user without a way forward.
        val failedSendDialog = viewState.value.errorDialog
        setState {
            copy(errorDialog = null)
        }
        runWhenOnline(onClose = { setState { copy(errorDialog = failedSendDialog) } }) {
            setState {
                copy(isLoading = true)
            }
            viewModelScope.launch {
                sendRequestedDocuments()
            }
        }
    }

    /**
     * WD-4685: the PIN cannot be checked without the backend, either because the OS reports no
     * internet before the PIN is submitted or because the PIN session got no answer (captive
     * portal, VPN without a route, DNS failure). The PIN never outlives this: the field is
     * cleared, and Retry, once online, lets the user enter it again.
     */
    private fun showPinOffline() {
        setState { copy(error = null, pinState = pinState.cleared()) }
        showOfflineDialog(
            onRetry = {
                if (!connectivityChecker.isOnline()) showPinOffline()
            },
        )
    }

    /** An OpenID4VP presentation talks to the verifier over the internet; a BLE one does not. */
    private fun needsInternetForRequest(): Boolean =
        requestUriConfig.mode is PresentationMode.OpenId4Vp

    /**
     * WD-4685: runs [action] when the OS reports internet, or there is no verifier to reach
     * over it. Otherwise shows the offline dialog, whose Retry checks again and whose Close
     * runs [onClose].
     */
    private fun runWhenOnline(
        onClose: () -> Unit = { setEvent(Event.DismissError) },
        action: () -> Unit,
    ) {
        if (!needsInternetForRequest() || connectivityChecker.isOnline()) {
            action()
            return
        }
        showOfflineDialog(onRetry = { runWhenOnline(onClose, action) }, onClose = onClose)
    }

    /**
     * Shows the "You're offline" dialog (WD-4685). Retry closes it before [onRetry] runs, so
     * [onRetry] may show it again; "Open internet settings" leaves it open for the return.
     */
    private fun showOfflineDialog(
        onRetry: () -> Unit,
        onClose: () -> Unit = { setEvent(Event.DismissError) },
    ) {
        setState {
            copy(
                isLoading = false,
                errorDialog = offlineErrorDialogConfig(
                    onRetry = {
                        setState { copy(errorDialog = null) }
                        onRetry()
                    },
                    onOpenSettings = { setEffect { Effect.OpenInternetSettings } },
                    onClose = onClose,
                ),
            )
        }
    }

    private suspend fun sendRequestedDocuments() {
        setState {
            copy(error = null)
        }

        when (val result = presentationRequestInteractor.sendRequestedDocuments()) {
            is PresentationDocumentSubmissionPartialState.Success -> Unit

            is PresentationDocumentSubmissionPartialState.Failure.Unknown -> {
                // WD-4273: retry re-submits the documents. The PIN session and
                // the processRequest() collector are still alive, so a
                // successful re-send continues to the success screen.
                showPresentationError(
                    error = result.error,
                    onRetry = ::retrySendRequestedDocuments,
                )
            }

            is PresentationDocumentSubmissionPartialState.Failure.PinVerificationBlocked -> {
                rwscaPinHandler.clearPinSession()
                val step = TemporaryPinBlocked(
                    isLastTry = result.triesRemaining == 1
                )

                setState {
                    copy(
                        error = null,
                        isLoading = false,
                        currentStep = step,
                        headerConfig = getHeaderForStep(step),
                        onContinueAction = getContinueActionForStep(step),
                        pinState = pinState.cleared(),
                        sheetContent = getBottomSheetContentForStep(step),
                        unBlockRemainingTime = formatTime(result.iso8601utcTime),
                    )
                }

                startUnblockTimer(result.iso8601utcTime)
            }

            is PresentationDocumentSubmissionPartialState.Failure.WrongPin -> {
                rwscaPinHandler.clearPinSession()
                setState {
                    copy(
                        isLoading = false,
                        error = null,
                        pinState = pinState.cleared(
                            supportingText = resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin),
                        ),
                    )
                }
            }

            is PresentationDocumentSubmissionPartialState.Failure.ServerError -> {
                val (titleRes, bodyTextRes, primaryButtonTextRes) = when (result.errorCode) {
                    PresentationDocumentSubmissionPartialState.Failure.ServerErrorCode.RWSCD_ACCOUNT_UNKNOWN -> Triple(
                        R.string.pid_presentation_rwscd_account_unknown_title,
                        R.string.pid_presentation_rwscd_account_unknown_paragraph,
                        R.string.pid_presentation_rwscd_account_unknown_prim_button
                    )

                    PresentationDocumentSubmissionPartialState.Failure.ServerErrorCode.RWSCD_AUTH_VERIFICATION_FAILED -> Triple(
                        R.string.pid_presentation_rwscd_auth_verification_failed_title,
                        R.string.pid_presentation_rwscd_auth_verification_failed_paragraph,
                        R.string.pid_presentation_rwscd_auth_verification_failed_prim_button
                    )

                    else -> Triple(
                        R.string.global_error_title,
                        R.string.global_error_paragraph,
                        R.string.global_error_prim_button
                    )
                }

                setState {
                    copy(
                        errorDialog = GenericErrorDialogConfig(
                            titleRes = titleRes,
                            bodyTextRes = bodyTextRes,
                            errorCode = result.backendErrorCode,
                            traceId = result.traceId,
                            primaryButtonTextRes = primaryButtonTextRes,
                            onDismiss = { setEvent( Event.DismissError) },
                            onPrimaryButtonClick = { setEvent(Event.DismissError) }
                        ),
                        isLoading = false,
                        pinState = pinState.cleared(),
                    )
                }
            }
        }
    }

    override fun deletePidDocuments() {
        setState {
            copy(
                isLoading = true,
                error = null,
                shouldDisplayPidDeletionConfirmDialog = false
            )
        }

        viewModelScope.launch {
            pidDeletionInteractor.deletePid().collect { response ->
                when (response) {
                    is DocumentDeletionPartialState.Success -> {
                        setState {
                            copy(
                                currentStep = RequestScreenStep.DocumentDeletedConfirmation,
                                headerConfig = getHeaderForStep(RequestScreenStep.DocumentDeletedConfirmation),
                                isLoading = false,
                                error = null
                            )
                        }
                        delay(2.toDuration(DurationUnit.SECONDS))

                        onRequestAbandoned()
                        setEffect {
                            Effect.Navigation.SwitchScreen(
                                screenRoute = DashboardScreens.Dashboard.screenRoute,
                            )
                        }
                    }

                    is DocumentDeletionPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun startUnblockTimer(endTimeIsoUtc: String) {
        val endTime = Instant.parse(endTimeIsoUtc)

        unBlockTimerJob?.cancel()
        unBlockTimerJob = viewModelScope.launch {
            fun now() = Instant.now()

            var remainingMs = ChronoUnit.MILLIS.between(now(), endTime)
            if (remainingMs <= 0) {
                setEvent(Event.ShowPinView)
                return@launch
            }
            val firstDelay = (remainingMs % 1000).let { if (it == 0L) 1000L else it }
            delay(firstDelay)

            while (true) {
                remainingMs = ChronoUnit.MILLIS.between(now(), endTime)
                if (remainingMs <= 0) {
                    setEvent(Event.ShowPinView)
                    break
                }

                setState {
                    copy(unBlockRemainingTime = formatAsMmSs(remainingMs))
                }
                delay(1000)
            }
        }
    }

    private fun formatAsMmSs(remainingMs: Long): String {
        val totalSeconds = (remainingMs + 999) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }


    override fun cleanUp() {
        if (isNavigatingToPresentationContinuation) {
            unBlockTimerJob?.cancel()
            return
        }
        super.cleanUp()
        stopPresentation()
        unBlockTimerJob?.cancel()
    }

    /**
     * Stop the active OpenID4VP session as soon as the user abandons this request, then close its
     * presentation scope after navigation dispatch. Closing the scope prevents the next in-process
     * PID issuance from reusing stale presentation-scoped state, but doing it synchronously can
     * prevent the one-shot navigation effect from being delivered.
     */
    override fun onRequestAbandoned() {
        resetWalletPin()
        stopPresentation()
        unBlockTimerJob?.cancel()
        appCoroutineScope.launch {
            delay(100)
            closePresentationScope()
        }
    }

    private fun stopPresentation() {
        if (!isPresentationStopped) {
            presentationRequestInteractor.stopPresentation()
            isPresentationStopped = true
        }
    }

    override fun onRequestDeclined() {
        unsubscribe()

        if (isPresentationStopped) {
            leaveForDashboard()
            return
        }

        setState { copy(isLoading = true) }
        viewModelScope.launch {
            val outcome = presentationRequestInteractor.rejectPresentation()
            setState { copy(isLoading = false) }

            telemetry.endSpan(TelemetryConstants.PRESENTATION)

            if (outcome is PresentationRejectionPartialState.Redirect) {
                doNavigation(NavigationType.Deeplink(outcome.uri.toString(), routeToPop = null))
            }
            leaveForDashboard()
        }
    }

    /**
     * The Digital ID code is locked for good (WD-4980): declines the request, tears the
     * presentation down and opens the blocking screen, which offers adding the Digital ID again.
     * The presentation scope is closed (right after the navigation is dispatched) because the PID
     * issuance started from there must not reuse it.
     */
    private fun showPidLocked() {
        setState { copy(error = null, isLoading = true, pinState = pinState.cleared()) }
        declineUnmatchedRequest { _, traceId ->
            doNavigation(
                NavigationType.PushRoute(
                    presentationErrorRoute(reason = BlockingErrorReason.PID_LOCKED, traceId = traceId)
                )
            )
            onRequestAbandoned()
        }
    }

    /**
     * Answers a request no credential in the wallet can satisfy (or that a locked Digital ID
     * cannot answer) with `access_denied`, so the verifier can close its session. The answer is
     * awaited before [then] runs, because leaving the flow stops the presentation and cancels it.
     */
    private fun declineUnmatchedRequest(then: (redirectUri: String?, traceId: String) -> Unit) {
        unsubscribe()
        viewModelScope.launch {
            val outcome = presentationRequestInteractor.rejectPresentation()
            val traceId = telemetry.currentTraceId()
            telemetry.endSpan(TelemetryConstants.PRESENTATION)
            then((outcome as? PresentationRejectionPartialState.Redirect)?.uri?.toString(), traceId)
        }
    }

    private fun showUnsatisfiableRequestError(redirectUri: String?, traceId: String) {
        val leave = {
            redirectUri?.let { doNavigation(NavigationType.Deeplink(it, routeToPop = null)) }
            setEvent(Event.Pop)
        }
        setState {
            copy(
                errorDialog = GenericErrorDialogConfig(
                    titleRes = R.string.pid_presentation_malformed_presentation_title,
                    bodyTextRes = R.string.pid_presentation_malformed_presentation_paragraph,
                    traceId = traceId,
                    primaryButtonTextRes = R.string.pid_presentation_malformed_presentation_prim_button,
                    onDismiss = leave,
                    onPrimaryButtonClick = leave,
                )
            )
        }
    }

    private fun navigateToPresentationLoading() {
        isNavigatingToPresentationContinuation = true
        doNavigation(NavigationType.PushRoute(PresentationScreens.PresentationLoading.screenRoute))
    }

    /**
     * Runs the post-presentation batch refresh (spec step 043), then tears the presentation down,
     * both before the verifier's redirect is opened.
     *
     * Awaited rather than backgrounded because the redirect puts the wallet in the background, and
     * Android 15+ then revokes its network access (`blocked=APP_BACKGROUND`) and destroys its
     * sockets, killing the refresh part-way through. It cannot move to WorkManager either:
     * re-issuing a PID creates rWSCA keys, which needs the in-memory PIN session that exists only
     * inside this presentation transaction. The screen already shows its progress state from PIN
     * submission, and REISSUE_TIMEOUT bounds the wait.
     *
     * A dynamic issuance keeps the presentation scope, because the issuance it interrupted still
     * needs it.
     */
    private suspend fun refreshBatchBeforeLeaving(isDynamicIssuance: Boolean) {
        try {
            presentationRequestInteractor.reissueLowBatchDocumentsIfNeeded()
        } finally {
            presentationRequestInteractor.stopPresentation()
            if (!isDynamicIssuance) {
                closePresentationScope()
            }
        }
    }

    override fun resetWalletPin() {
        rwscaPinHandler.clearPinSession()
    }

    override fun isWalletPinBlocked(): Boolean {
        val blockTime = presentationRequestInteractor.getWalletPinBlockTime()
        return if (blockTime.isEmpty()) {
            false
        } else {
            walletPinBlockEndTimeUtc = blockTime
            instantProvider.getCurrentInstant().isBefore(Instant.parse(blockTime))
        }
    }

    override fun isLastTry(): Boolean = presentationRequestInteractor.isLastPinTry()

    /**
     * Blocking errors open [PresentationScreens.PresentationError] (WD-4274); any other
     * [PresentationError] (WD-4272) is shown as the error dialog.
     *
     * @param onDismiss Invoked for dismiss actions and when the dialog is dismissed.
     * @param onRetry Re-runs the failed step, or `null` when it cannot be retried from this
     *   screen (WD-4273); a retry action is then downgraded to a "Retry later" dismiss.
     */
    private fun showPresentationError(
        error: PresentationError,
        onRetry: (() -> Unit)?,
        onDismiss: () -> Unit = { setEvent(Event.DismissError) },
    ) {
        // Links openUrl would drop (mailto:, tel:, ...) are not offered.
        val supportUrl = viewState.value.supportUrl?.takeIf { it.toUri().isSafeRedirectLink() }
        val serviceProviderUrl = viewState.value.serviceProviderUrl?.takeIf { it.toUri().isSafeRedirectLink() }
        val uiConfig = PresentationErrorUiConfig.from(error) { telemetry.currentTraceId() }
            .let { if (onRetry == null) it.withoutRetry() else it }
            .let { if (supportUrl == null) it.withoutContactService() else it }
            .let { if (serviceProviderUrl == null) it.withoutBackToServiceProvider() else it }
        if (navigateToBlockingErrorScreen(uiConfig)) return

        val errorDialog = uiConfig.toGenericErrorDialogConfig(
            // withoutRetry() above already removed any RETRY action when onRetry is null.
            onRetry = onRetry ?: onDismiss,
            onReportIssue = {
                setState {
                    copy(
                        errorDialog = null,
                        pendingRedirectUrl = configLogic.environmentConfig.reportIssueUrl,
                    )
                }
            },
            onDismiss = onDismiss,
            // The error dialog stays open behind the redirect confirmation.
            onContactService = { setState { copy(pendingRedirectUrl = supportUrl) } },
            onBackToServiceProvider = { setState { copy(pendingRedirectUrl = serviceProviderUrl) } },
            onToOverview = {
                setState { copy(errorDialog = null) }
                unsubscribe()
                leaveForDashboard()
            },
        )
        setState { copy(isLoading = false, errorDialog = errorDialog) }
    }

    /**
     * Like [showPresentationError], for a failure to fetch the request: there is nothing
     * to return to, so dismissing leaves the screen, and retry re-fetches the request
     * when the taxonomy permits it (WD-4273).
     */
    private fun showRequestLoadError(error: PresentationError) {
        showPresentationError(
            error = error,
            onRetry = {
                setEvent(Event.DismissError)
                setEvent(Event.DoWork)
            },
            onDismiss = {
                setEvent(Event.DismissError)
                setEvent(Event.Pop)
            },
        )
    }

    /**
     * Navigates to [PresentationScreens.PresentationError] when [uiConfig] is a
     * blocking state.
     *
     * @return Whether it navigated.
     */
    private fun navigateToBlockingErrorScreen(uiConfig: PresentationErrorUiConfig): Boolean {
        val blockingReason = uiConfig.blockingReason ?: return false

        setState { copy(isLoading = false, errorDialog = null) }
        doNavigation(
            NavigationType.PushRoute(
                presentationErrorRoute(reason = blockingReason, traceId = uiConfig.traceId)
            )
        )
        return true
    }
}
