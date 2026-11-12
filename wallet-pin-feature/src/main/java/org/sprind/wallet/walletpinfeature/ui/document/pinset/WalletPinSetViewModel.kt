package org.sprind.wallet.walletpinfeature.ui.document.pinset

import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.commonfeature.interactor.IssuanceEvent
import eu.europa.ec.commonfeature.interactor.StartPinSessionResult
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import org.sprind.wallet.commonfeature.ui.error.authorizationExpiredDialogConfig
import org.sprind.wallet.commonfeature.ui.error.toPidIssuanceErrorDialogConfig
import org.sprind.wallet.corelogic.issuance.isExpiredAuthorization
import org.sprind.wallet.networklogic.common.NetworkFailure
import org.sprind.wallet.networklogic.common.toNetworkFailure
import org.sprind.wallet.networklogic.trace.traceId
import org.sprind.wallet.uilogic.component.CodeEntryBuffer
import org.sprind.wallet.uilogic.component.CodeEntryState
import org.sprind.wallet.uilogic.component.CodeLength
import org.sprind.wallet.uilogic.component.cleared
import org.sprind.wallet.walletpinfeature.interactor.wscd.WscaRegistrationInteractor
import org.sprind.wallet.walletpinfeature.interactor.wscd.WscaRegistrationResult
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/** Reported when issuance could not be completed after the wallet PIN was set. */
private const val ISSUANCE_FAILED_ERROR_CODE = "ISSUANCE_FAILED"

/**
 * Bounds the wait for the issuance outcome once the authorization has resumed. The interactor does
 * not publish an [IssuanceEvent] for every way an issuance can end (a deferred issuance, for one),
 * and system back is held while the loader shows, so without a bound the user would be stuck.
 * Covers PID key creation in the remote HSM and the calls to the issuer, not user interaction.
 */
private val ISSUANCE_OUTCOME_TIMEOUT = 60.seconds

sealed class WalletPinStep {
    data object Info : WalletPinStep()
    data object Set : WalletPinStep()
    data object Confirm : WalletPinStep()
    data object Success : WalletPinStep()
}

data class State(
    val isLoading: Boolean = false,
    val pinState: CodeEntryState = CodeEntryState(CodeEntryBuffer(CodeLength.WALLET_PIN)),
    val currentStep: WalletPinStep = WalletPinStep.Info,
    val errorDialog: GenericErrorDialogConfig? = null,
) : ViewState

sealed class Event : ViewEvent {
    data object OnContinueAction : Event()
    data object OnCloseClicked : Event()
    data object OnDismissRequested : Event()
    data object OnPinUpdate : Event()
    data object OnPinConfirmUpdate : Event()
    data object OnWalletPinSet : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(
            val screenRoute: String,
            val inclusive: Boolean,
        ) : Navigation()
    }
}

@KoinViewModel
class WalletPinSetViewModel(
    private val rwscaPinHandler: RwscaPinHandler,
    private val wscaRegistrationInteractor: WscaRegistrationInteractor,
    private val addDocumentInteractor: AddDocumentInteractor,
    private val telemetry: Telemetry,
    @InjectedParam private val flowType: IssuanceFlowUiConfig,
    @InjectedParam private val redirectUrl: String,
) : MviViewModel<Event, State, Effect>() {

    /**
     * The PIN from the first step, kept only so the second step can be compared against it. Erased
     * as soon as the comparison is no longer needed, and never copied into a [String].
     */
    private val firstEntry = CodeEntryBuffer(CodeLength.WALLET_PIN)

    override fun setInitialState(): State = State(isLoading = false)

    override fun onCleared() {
        // Abandoning the flow — backing out, or the process tearing the screen down — has to erase
        // the digits as surely as completing it does.
        firstEntry.wipe()
        viewState.value.pinState.buffer.wipe()
        super.onCleared()
    }

    /**
     * For errors before the redirect is resumed, which the user can retry. Failures after it end the
     * Issuance span instead, see [showIssuanceFailed].
     */
    private fun handleError(
        errorCode: String,
        traceId: String?,
        onDismiss: () -> Unit = { setEvent(Event.OnDismissRequested) },
    ) =
        setState {
            copy(
                isLoading = false,
                errorDialog = GenericErrorDialogConfig(
                    titleRes = R.string.global_error_title,
                    bodyTextRes = R.string.global_error_paragraph,
                    errorCode = errorCode,
                    traceId = traceId,
                    primaryButtonTextRes = R.string.global_error_prim_button,
                    onDismiss = onDismiss,
                    onPrimaryButtonClick = onDismiss
                )
            )
        }

    override fun handleEvents(event: Event) {
        when (event) {
            Event.OnWalletPinSet -> {
                setState {
                    copy(
                        isLoading = true
                    )
                }

                viewModelScope.launch {
                    // The digits leave the screen here and nowhere else: both buffers are erased as
                    // the pin is built, so from now on it exists only inside the UserPin until
                    // whoever derives keys from it closes that too. It is the first entry that is
                    // submitted; the confirmation existed only to be compared against it.
                    val pin = firstEntry.consumeAsPin()
                    viewState.value.pinState.buffer.wipe()
                    if (wscaRegistrationInteractor.isAlreadyRegistered()) {
                        val startPinSessionResult = rwscaPinHandler.startPinSession(pin)
                        if (startPinSessionResult is StartPinSessionResult.Failure) {
                            handleError(
                                startPinSessionResult.error.code,
                                startPinSessionResult.error.traceId
                            )
                        } else {
                            continueIssuance()
                        }
                    } else {
                        wscaRegistrationInteractor.register(pin).collect { registrationResult ->
                            when (registrationResult) {
                                is WscaRegistrationResult.Failure -> {
                                    handleError(registrationResult.errorCode, registrationResult.traceId)
                                }

                                is WscaRegistrationResult.Success -> {
                                    continueIssuance()
                                }
                            }
                        }
                    }
                }
            }

            Event.OnContinueAction -> {
                when (viewState.value.currentStep) {
                    WalletPinStep.Info -> setState {
                        copy(
                            currentStep = WalletPinStep.Set,
                            pinState = pinState.cleared()
                        )
                    }

                    WalletPinStep.Set -> {
                        firstEntry.copyFrom(viewState.value.pinState.buffer)
                        setState {
                            copy(
                                currentStep = WalletPinStep.Confirm,
                                pinState = pinState.cleared()
                            )
                        }
                    }

                    else -> {
                        // Do nothing
                    }
                }
            }

            Event.OnCloseClicked -> {
                navigateToDashboard()
            }

            Event.OnDismissRequested -> {
                // registration and issuance cannot be abandoned half-way; the toolbar hides its
                // back action while loading, and system back must not step around that
                if (viewState.value.isLoading) return
                when (viewState.value.currentStep) {
                    WalletPinStep.Confirm -> setState {
                        firstEntry.wipe()
                        copy(
                            currentStep = WalletPinStep.Set,
                            pinState = pinState.cleared(),
                            errorDialog = null
                        )
                    }

                    WalletPinStep.Set -> setState {
                        copy(
                            currentStep = WalletPinStep.Info,
                            pinState = pinState.cleared(),
                            errorDialog = null
                        )
                    }

                    else -> {
                        setState {
                            copy(
                                errorDialog = null
                            )
                        }
                    }
                }
            }

            Event.OnPinConfirmUpdate -> {
                val entered = viewState.value.pinState.buffer
                val isValid = entered.isComplete && entered.contentEquals(firstEntry)
                setState {
                    copy(pinState = pinState.copy(isValid = isValid))
                }
            }

            Event.OnPinUpdate -> {
                val isValid = viewState.value.pinState.buffer.isComplete
                setState {
                    copy(pinState = pinState.copy(isValid = isValid))
                }
            }
        }
    }

    /**
     * Finishes the issuance the wallet PIN was set for. The loader stays over the Confirm step until
     * the issuance ends: the Success step is only shown once the document is issued, so a failure
     * surfaces as an error dialog over the entry screen rather than under a success screen.
     */
    private suspend fun continueIssuance() {
        // issuanceEvents does not replay, and the outcome may be published while the authorization
        // is still resuming, so listen for it before resuming
        val outcome = viewModelScope.async(start = CoroutineStart.UNDISPATCHED) {
            addDocumentInteractor.issuanceEvents.first()
        }
        val result = addDocumentInteractor.authorizationHandler.resumeWithRedirectUri(redirectUrl)
        if (result.isFailure) {
            outcome.cancel()
            rwscaPinHandler.clearPinSession()
            val exception = result.exceptionOrNull()
            showIssuanceFailed(
                traceId = exception?.traceId() ?: telemetry.currentTraceId(),
                networkFailure = exception?.toNetworkFailure(),
                isAuthorizationExpired = exception?.isExpiredAuthorization() == true,
            )
            return
        }

        val event = withTimeoutOrNull(ISSUANCE_OUTCOME_TIMEOUT) { outcome.await() }
        if (event == null) {
            // stop observing the issuance and drop its pending authorization. This does not stop
            // the wallet-core manager, which issues on its own scope; clearing the PIN session
            // below makes any later signing fail rather than store a document behind the error
            addDocumentInteractor.cancelIssuance()
        }
        rwscaPinHandler.clearPinSession()
        when (event) {
            IssuanceEvent.Completed -> {
                setState {
                    copy(
                        currentStep = WalletPinStep.Success,
                        isLoading = false
                    )
                }
                // let the user see the Success step before leaving for the dashboard
                delay(2.toDuration(DurationUnit.SECONDS))
                navigateToDashboard()
            }

            is IssuanceEvent.Failed -> showIssuanceFailed(
                traceId = telemetry.currentTraceId(),
                networkFailure = event.networkFailure,
                isAuthorizationExpired = event.isAuthorizationExpired,
            )

            null -> {
                outcome.cancel()
                showIssuanceFailed(traceId = telemetry.currentTraceId())
            }
        }
    }

    /**
     * Ends the Issuance span and shows the issuance error over the entry screen. [traceId] must be
     * read before the span ends, while it is still the live one.
     */
    private fun showIssuanceFailed(
        traceId: String?,
        networkFailure: NetworkFailure? = null,
        isAuthorizationExpired: Boolean = false,
    ) {
        telemetry.endSpan(TelemetryConstants.ISSUANCE)
        setState {
            copy(
                isLoading = false,
                errorDialog = issuanceFailedDialog(traceId, networkFailure, isAuthorizationExpired),
            )
        }
    }

    /**
     * The dialog for an issuance that failed once the redirect was resumed. The eID session is used
     * up by then, so retrying and starting again both restart the PID issuance from the document
     * list. The timeout and expired session dialogs cannot be dismissed, so the user chooses between
     * that and cancelling; a rate limit's "Retry later" also goes back to the document list.
     */
    private fun issuanceFailedDialog(
        traceId: String?,
        networkFailure: NetworkFailure?,
        isAuthorizationExpired: Boolean,
    ): GenericErrorDialogConfig {
        val startAgain = { navigateToAddDocumentScreen() }
        val cancel = { navigateToDashboard() }
        return when {
            isAuthorizationExpired -> authorizationExpiredDialogConfig(
                onStartAgain = startAgain,
                onCancel = cancel,
            )

            networkFailure != null -> networkFailure.toPidIssuanceErrorDialogConfig(
                onRetry = startAgain,
                onCancel = cancel,
                onDismiss = startAgain,
            )

            else -> GenericErrorDialogConfig(
                titleRes = R.string.global_error_title,
                bodyTextRes = R.string.global_error_paragraph,
                errorCode = ISSUANCE_FAILED_ERROR_CODE,
                traceId = traceId,
                primaryButtonTextRes = R.string.global_error_prim_button,
                onDismiss = startAgain,
                onPrimaryButtonClick = startAgain,
            )
        }
    }

    private fun navigateToAddDocumentScreen() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = IssuanceScreens.AddDocument,
                    arguments = generateComposableArguments(
                        mapOf(
                            "flowType" to IssuanceFlowUiConfig.fromIssuanceFlowUiConfig(
                                flowType
                            ),
                        )
                    )
                ),
                inclusive = false
            )
        }
    }

    private fun navigateToDashboard() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = DashboardScreens.Dashboard,
                    arguments = "",
                ),
                inclusive = false,
            )
        }
    }
}
