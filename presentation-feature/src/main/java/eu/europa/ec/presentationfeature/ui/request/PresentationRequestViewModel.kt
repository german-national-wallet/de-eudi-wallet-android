package eu.europa.ec.presentationfeature.ui.request

import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.jwt.InstantProvider
import org.sprind.wallet.businesslogic.util.SpanAttributes
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.interactor.StartPinSessionResult
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.businesslogic.model.UserPin
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType
import eu.europa.ec.commonfeature.ui.request.Effect
import eu.europa.ec.commonfeature.ui.request.Event
import eu.europa.ec.commonfeature.ui.request.RequestScreenStep
import eu.europa.ec.commonfeature.ui.request.RequestScreenStep.TemporaryPinBlocked
import eu.europa.ec.commonfeature.ui.request.RequestViewModel
import eu.europa.ec.commonfeature.ui.request.model.DocumentType
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentClaim
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.corelogic.di.AppCoroutineScope
import eu.europa.ec.corelogic.di.closePresentationScope
import eu.europa.ec.presentationfeature.interactor.PresentationDocumentSubmissionPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestDeleteDocumentPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestProcessPartialState
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import org.sprind.wallet.uilogic.component.cleared
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@KoinViewModel
class PresentationRequestViewModel(
    private val presentationRequestInteractor: PresentationRequestInteractor,
    override val resourceProvider: ResourceProvider,
    override val uiSerializer: UiSerializer,
    private val rwscaPinHandler: RwscaPinHandler,
    private val instantProvider: InstantProvider,
    private val telemetry: Telemetry,
    private val appCoroutineScope: AppCoroutineScope,
    @InjectedParam private val requestUriConfigRaw: String,
) : RequestViewModel(resourceProvider, uiSerializer, instantProvider) {

    private var unBlockTimerJob: Job? = null
    private var isNavigatingToPresentationContinuation = false
    private var isPresentationStopped = false

    override fun onStickyButtonPressed() {
        if (shouldEnterWalletPin()) {
            setEvent(Event.ShowPinView)
        } else {
            navigateToPresentationLoading()
        }
    }

    override fun onCredentialPreviewContinue() {
        if (shouldEnterWalletPin()) {
            setEvent(Event.ShowPinView)
        } else {
            navigateToPresentationLoading()
        }
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

        val requestUriConfig = uiSerializer.fromBase64(
            requestUriConfigRaw,
            RequestUriConfig::class.java,
            RequestUriConfig.Parser
        ) ?: throw RuntimeException("RequestUriConfig:: is Missing or invalid")

        telemetry.startSpan(
            spanName = TelemetryConstants.PRESENTATION,
            initialAttributes = SpanAttributes(mapOf("presentation_request" to requestUriConfig.toString()))
        )
        presentationRequestInteractor.setConfig(requestUriConfig)

        viewModelJob = viewModelScope.launch {
            presentationRequestInteractor.getRequestDocuments().collect { response ->
                when (response) {
                    is PresentationRequestInteractorPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(Event.DoWork) },
                                    errorSubTitle = response.error,
                                    onCancel = { setEvent(Event.Pop) }
                                )
                            )
                        }
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
                                verifierName = response.verifierName
                            )
                        }
                    }

                    is PresentationRequestInteractorPartialState.Disconnect -> {
                        setEvent(Event.Pop)
                    }

                    is PresentationRequestInteractorPartialState.NoData -> {
                        doNavigation(
                            NavigationType.PushRoute(
                                PresentationScreens.PresentationNoDocument.screenRoute
                            )
                        )
                    }
                }
            }
        }
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

            RequestScreenStep.AccountLocked -> current.copy(
                title = resourceProvider.getString(R.string.pid_presentation_retry_counter_locked_title),
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
                    setState {
                        copy(
                            isLoading = false,
                            errorDialog = GenericErrorDialogConfig(
                                titleRes = R.string.global_error_title,
                                bodyTextRes = R.string.global_error_paragraph,
                                errorCode = "UNKNOWN",
                                traceId = telemetry.currentTraceId(),
                                primaryButtonTextRes = R.string.global_error_prim_button,
                                onDismiss = { setEvent( Event.DismissError) },
                                onPrimaryButtonClick = { setEvent(Event.DismissError) }
                            ),
                        )
                    }
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
            error is RwscaError.FromRwsca && error.type == RwscaErrorType.PIN_VERIFICATION_FAILED -> setState {
                copy(
                    isLoading = false,
                    error = null,
                    pinState = pinState.cleared(
                        supportingText = resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin),
                    ),
                )
            }
            error is RwscaError.FromRwsca && error.type == RwscaErrorType.ACCOUNT_LOCKED -> setState {
                copy(
                    error = null,
                    isLoading = false,
                    currentStep = RequestScreenStep.AccountLocked,
                    headerConfig = getHeaderForStep(RequestScreenStep.AccountLocked),
                    onContinueAction = getContinueActionForStep(RequestScreenStep.AccountLocked),
                    pinState = pinState.cleared(),
                    sheetContent = getBottomSheetContentForStep(RequestScreenStep.AccountLocked)
                )
            }
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
                        onPrimaryButtonClick = { setEvent(Event.DismissError) }
                    ),
                )
            }
        }
    }

    private suspend fun sendRequestedDocuments() {
        setState {
            copy(error = null)
        }

        when (val result = presentationRequestInteractor.sendRequestedDocuments()) {
            is PresentationDocumentSubmissionPartialState.Success -> Unit

            is PresentationDocumentSubmissionPartialState.Failure.Unknown -> {
                setState {
                    copy(
                        isLoading = false,
                        errorDialog = GenericErrorDialogConfig(
                            titleRes = R.string.global_error_title,
                            bodyTextRes = R.string.global_error_paragraph,
                            errorCode = "UNKNOWN",
                            traceId = telemetry.currentTraceId(),
                            primaryButtonTextRes = R.string.global_error_prim_button,
                            onDismiss = { setEvent( Event.DismissError) },
                            onPrimaryButtonClick = { setEvent(Event.DismissError) }
                        ),
                    )
                }
            }

            is PresentationDocumentSubmissionPartialState.Failure.AccountLocked -> {
                rwscaPinHandler.clearPinSession()
                setState {
                    copy(
                        error = null,
                        isLoading = false,
                        currentStep = RequestScreenStep.AccountLocked,
                        headerConfig = getHeaderForStep(RequestScreenStep.AccountLocked),
                        onContinueAction = getContinueActionForStep(RequestScreenStep.AccountLocked),
                        pinState = pinState.cleared(),
                        sheetContent = getBottomSheetContentForStep(RequestScreenStep.AccountLocked)
                    )
                }

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
            presentationRequestInteractor.deletePidDocuments().collect { response ->
                when (response) {
                    is PresentationRequestDeleteDocumentPartialState.Success -> {
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

                    is PresentationRequestDeleteDocumentPartialState.Failure -> {
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
}
