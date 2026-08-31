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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.networklogic.trace.traceId
import org.sprind.wallet.uilogic.component.CodeEntryBuffer
import org.sprind.wallet.uilogic.component.CodeEntryState
import org.sprind.wallet.uilogic.component.CodeLength
import org.sprind.wallet.uilogic.component.cleared
import org.sprind.wallet.walletpinfeature.interactor.wscd.WscaRegistrationInteractor
import org.sprind.wallet.walletpinfeature.interactor.wscd.WscaRegistrationResult
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/** Reported when issuance could not be completed after the wallet PIN was set. */
private const val ISSUANCE_FAILED_ERROR_CODE = "ISSUANCE_FAILED"

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
    private var issuanceEventsJob: Job? = null

    override fun setInitialState(): State = State(isLoading = false)

    override fun onCleared() {
        // Abandoning the flow — backing out, or the process tearing the screen down — has to erase
        // the digits as surely as completing it does.
        firstEntry.wipe()
        viewState.value.pinState.buffer.wipe()
        super.onCleared()
    }

    private fun handleError(errorCode: String, traceId: String?) =
        setState {
            copy(
                isLoading = false,
                errorDialog = GenericErrorDialogConfig(
                    titleRes = R.string.global_error_title,
                    bodyTextRes = R.string.global_error_paragraph,
                    errorCode = errorCode,
                    traceId = traceId,
                    primaryButtonTextRes = R.string.global_error_prim_button,
                    onDismiss = { setEvent(Event.OnDismissRequested) },
                    onPrimaryButtonClick = { setEvent(Event.OnDismissRequested) }
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
                            showSuccessScreenAndContinueIssuance()
                        }
                    } else {
                        wscaRegistrationInteractor.register(pin).collect { registrationResult ->
                            when (registrationResult) {
                                is WscaRegistrationResult.Failure -> {
                                    handleError(registrationResult.errorCode, registrationResult.traceId)
                                }

                                is WscaRegistrationResult.Success -> {
                                    showSuccessScreenAndContinueIssuance()
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

    private suspend fun showSuccessScreenAndContinueIssuance() {
        setState {
            copy(
                currentStep = WalletPinStep.Success,
                isLoading = false
            )
        }
        // delay required to allow the user to see the Success screen otherwise the loader takes
        // over very fast
        delay(2.toDuration(DurationUnit.SECONDS))
        setState { copy(isLoading = true) }
        subscribeToIssuance()
        val result = addDocumentInteractor.authorizationHandler.resumeWithRedirectUri(redirectUrl)
        if (result.isSuccess) {
            // Issuance completion is handled by the active subscription.
        } else {
            issuanceEventsJob?.cancel()
            issuanceEventsJob = null
            rwscaPinHandler.clearPinSession()
            // show error then navigate to add document screen
            setState {
                copy(
                    isLoading = false,
                    errorDialog = GenericErrorDialogConfig(
                        titleRes = R.string.global_error_title,
                        bodyTextRes = R.string.global_error_paragraph,
                        errorCode = ISSUANCE_FAILED_ERROR_CODE,
                        traceId = result.exceptionOrNull()?.traceId()
                            ?: telemetry.currentTraceId(),
                        primaryButtonTextRes = R.string.global_error_prim_button,
                        onDismiss = { navigateToAddDocumentScreen() },
                        onPrimaryButtonClick = { navigateToAddDocumentScreen() }
                    )
                )
            }
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

    private fun subscribeToIssuance() {
        if (issuanceEventsJob?.isActive == true) return

        issuanceEventsJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            addDocumentInteractor.issuanceEvents.collect { event ->
                when (event) {
                    IssuanceEvent.Completed -> {
                        issuanceEventsJob = null
                        rwscaPinHandler.clearPinSession()
                        navigateToDashboard()
                        this.cancel()
                    }

                    is IssuanceEvent.Failed -> {
                        issuanceEventsJob = null
                        rwscaPinHandler.clearPinSession()
                        setState {
                            copy(
                                isLoading = false,
                                errorDialog = GenericErrorDialogConfig(
                                    titleRes = R.string.global_error_title,
                                    bodyTextRes = R.string.global_error_paragraph,
                                    errorCode = ISSUANCE_FAILED_ERROR_CODE,
                                    traceId = telemetry.currentTraceId(),
                                    primaryButtonTextRes = R.string.global_error_prim_button,
                                    onDismiss = { navigateToAddDocumentScreen() },
                                    onPrimaryButtonClick = { navigateToAddDocumentScreen() }
                                )
                            )
                        }
                        this.cancel()
                    }
                }
            }
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
