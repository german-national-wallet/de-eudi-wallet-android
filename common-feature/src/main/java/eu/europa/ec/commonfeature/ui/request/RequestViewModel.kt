package eu.europa.ec.commonfeature.ui.request

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.jwt.InstantProvider
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentClaim
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.corelogic.di.closePresentationScope
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import org.sprind.wallet.businesslogic.model.UserPin
import org.sprind.wallet.uilogic.component.CodeEntryBuffer
import org.sprind.wallet.uilogic.component.CodeEntryState
import org.sprind.wallet.uilogic.component.CodeLength
import org.sprind.wallet.uilogic.component.cleared
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.config.NavigationType.PushRoute
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant


sealed class RequestScreenStep {
    data object Initial : RequestScreenStep()
    data object CredentialPreview : RequestScreenStep()
    data object EnterPin : RequestScreenStep()
    data class TemporaryPinBlocked(val isLastTry: Boolean = false) : RequestScreenStep()
    data object AccountLocked : RequestScreenStep()
    data object DocumentDeletedConfirmation : RequestScreenStep()
    data object Completed : RequestScreenStep()
}

data class State(
    val currentStep: RequestScreenStep = RequestScreenStep.Initial,
    val pinState: CodeEntryState = CodeEntryState(CodeEntryBuffer(CodeLength.WALLET_PIN)),
    val isLoading: Boolean = true,
    val headerConfig: ContentHeaderConfig,
    val error: ContentErrorConfig? = null,
    val errorDialog: GenericErrorDialogConfig? = null,
    val claimItems: List<RequestDocumentClaim> = emptyList(),
    val claimItemLabels: List<String> = emptyList(),
    val noItems: Boolean = false,
    val items: List<RequestDocumentItemUi> = emptyList(),
    val isBottomSheetOpen: Boolean = false,
    val sheetContent: RequestBottomSheetContent = RequestBottomSheetContent(),
    val onContinueAction: () -> Unit = {},
    val onSecurityInfoClicked: () -> Unit = {},
    val verifierName: String? = null,
    val toShowDataDetails: Boolean = false,
    val shouldDisplayConfirmCancelDialog: Boolean = false,
    val shouldDisplayPidDeletionConfirmDialog: Boolean = false,
    val onBackAction: (() -> Unit)? = null,
    val unBlockRemainingTime: String = "",
) : ViewState

sealed class Event : ViewEvent {
    data object DoWork : Event()
    data object DismissError : Event()
    data object Pop : Event()
    data object StickyButtonPressed : Event()
    data object CredentialDetailsView : Event()
    data object ShowPinView : Event()
    data class ShowWrongPinView(val remainingTime: String, val lastTry: Boolean) : Event()

    data class UserIdentificationClicked(val itemId: String) : Event()
    data class ExpandOrCollapseRequestDocumentItem(val itemId: String) : Event()

    sealed class BottomSheet : Event() {
        data class UpdateBottomSheetState(val isOpen: Boolean) : BottomSheet()
    }

    data object OnBackPressed : Event()
    data object OnPinEntered : Event()
    data object ConfirmPin : Event()

    data object ShowDataDetailsButtonPressed : Event()
    data object HideDataDetailsButtonPressed : Event()

    data object CancelButtonPressed : Event()

    data object DismissClickOnCancelConfirmationDialog : Event()
    data object ConfirmClickOnCancelConfirmationDialog : Event()

    data object PidDeletionRequested : Event()
    data object IdDeletionDialogDismissed : Event()
    data object DeletePidDocumentsRequested : Event()
    data object GoToDashboardButtonPressed : Event()
    data object TemporaryPinBlockedBottomSheetButtonPressed : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        data object Pop : Navigation()
        data class PopTo(val screenRoute: String) : Navigation()
        data class DeepLink(
            val link: Uri,
            val routeToPop: String?,
        ) : Navigation()
    }

    data object ShowBottomSheet : Effect()
    data object CloseBottomSheet : Effect()
    data object HideKeyboard : Effect()
}

data class RequestBottomSheetContent(
    val title: String = "",
    val description: String = "",
    val isInfo: Boolean = true,
    val buttonText: String? = null,
    val onButtonClick: (() -> Unit) = { },
)

abstract class RequestViewModel(
    protected open val resourceProvider: ResourceProvider,
    protected open val uiSerializer: UiSerializer,
    private val instantProvider: InstantProvider,
) :
    MviViewModel<Event, State, Effect>() {

    protected var viewModelJob: Job? = null
    protected var walletPinBlockEndTimeUtc: String = ""

    abstract fun getHeaderConfig(): ContentHeaderConfig
    abstract fun getNextScreen(): String
    abstract fun doWork()
    abstract fun resetWalletPin()
    abstract suspend fun processRequest(pin: UserPin)
    abstract fun deletePidDocuments()
    abstract fun isWalletPinBlocked(): Boolean
    abstract fun isLastTry(): Boolean
    abstract fun startUnblockTimer(endTimeIsoUtc: String)

    protected open fun onStickyButtonPressed() {
        setEvent(Event.ShowPinView)
    }

    protected open fun onCredentialPreviewContinue() {
        setEvent(Event.ShowPinView)
    }

    override fun setInitialState(): State {
        return State(
            headerConfig = getHeaderConfig(),
            error = null,
        )
    }

    /**
     * Called during [NavigationType.Pop].
     *
     * Kill presentation scope.
     *
     * */
    open fun cleanUp() {
        closePresentationScope()
        resetWalletPin()
    }

    /**
     * Called before navigating away from a request flow that the user has terminally abandoned.
     *
     * Use this for explicit user decisions where the current presentation can no longer continue,
     * such as confirming request rejection, leaving the account-locked screen for Dashboard, or
     * completing PID deletion after the account is locked. Do not call it for intermediate
     * navigation that preserves the presentation, such as moving to presentation loading/success.
     */
    protected open fun onRequestAbandoned() = Unit

    open fun updateData(
        updatedItems: List<RequestDocumentItemUi>,
    ) {
        setState {
            copy(
                items = updatedItems
            )
        }
    }

    private fun moveToStep(step: RequestScreenStep) {
        setState {
            copy(
                currentStep = step,
                headerConfig = getHeaderForStep(step),
                onContinueAction = getContinueActionForStep(step),
                // Entering the PIN step starts from a clean field; leaving it clears whatever was
                // typed so a PIN never survives into another step.
                pinState = if (step == RequestScreenStep.EnterPin) {
                    pinState.cleared()
                } else {
                    pinState.cleared(supportingText = pinState.supportingText)
                }
            )
        }
    }

    abstract fun getHeaderForStep(step: RequestScreenStep): ContentHeaderConfig

    fun getContinueActionForStep(step: RequestScreenStep): () -> Unit {
        return when (step) {
            RequestScreenStep.CredentialPreview -> {
                {
                    if (isWalletPinBlocked()) {
                        val nextStep =
                            RequestScreenStep.TemporaryPinBlocked(isLastTry = isLastTry())

                        setState {
                            copy(
                                error = null,
                                isLoading = false,
                                currentStep = nextStep,
                                headerConfig = getHeaderForStep(nextStep),
                                onContinueAction = getContinueActionForStep(nextStep),
                                pinState = pinState.cleared(),
                                sheetContent = getBottomSheetContentForStep(nextStep),
                                unBlockRemainingTime = formatTime(walletPinBlockEndTimeUtc),
                            )
                        }

                        startUnblockTimer(walletPinBlockEndTimeUtc)
                    } else {
                        onCredentialPreviewContinue()
                    }
                }
            }

            RequestScreenStep.EnterPin -> {
                { setEvent(Event.ConfirmPin) }
            }

            RequestScreenStep.Initial -> {
                { setEvent(Event.CredentialDetailsView) }
            }

            else -> {
                { /** Do nothing **/ }
            }
        }
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.DoWork -> doWork()
            is Event.DismissError -> setState { copy(error = null, errorDialog = null) }
            is Event.Pop -> doNavigation(NavigationType.Pop)
            is Event.StickyButtonPressed -> onStickyButtonPressed()
            is Event.CredentialDetailsView -> moveToStep(RequestScreenStep.CredentialPreview)
            is Event.ShowPinView -> moveToStep(RequestScreenStep.EnterPin)
            is Event.ShowWrongPinView -> moveToStep(
                RequestScreenStep.TemporaryPinBlocked(isLastTry = event.lastTry)
            )

            is Event.OnBackPressed -> {
                val currentStep = viewState.value.currentStep
                val previousStep = when (currentStep) {
                    RequestScreenStep.EnterPin -> RequestScreenStep.CredentialPreview
                    RequestScreenStep.CredentialPreview -> RequestScreenStep.Initial
                    else -> null
                }
                if (previousStep != null) {
                    moveToStep(previousStep)
                }
            }

            is Event.OnPinEntered -> {
                // CodeEntryField already caps the length and strips non-digits, so no re-sanitising here.
                val entered = viewState.value.pinState.buffer
                setState {
                    copy(
                        pinState = pinState.copy(
                            isValid = entered.isComplete,
                            // If there's an error message e.g. from previously entered incorrect
                            // PIN, only clear it after a digit from the next attempt is entered.
                            supportingText = if (entered.length == 0) pinState.supportingText else null
                        )
                    )
                }
            }

            is Event.ConfirmPin -> {
                if (viewState.value.pinState.buffer.isComplete) {
                    val pin = viewState.value.pinState.buffer.consumeAsPin()
                    setState {
                        copy(
                            isLoading = true
                        )
                    }
                    viewModelJob = viewModelScope.launch {
                        processRequest(pin)
                    }
                }
                setEffect { Effect.HideKeyboard }
            }

            is Event.UserIdentificationClicked -> updateUserIdentificationItem(event.itemId)
            is Event.ExpandOrCollapseRequestDocumentItem -> expandOrCollapseRequestDocumentItem(
                event.itemId
            )

            is Event.BottomSheet.UpdateBottomSheetState -> setState { copy(isBottomSheetOpen = event.isOpen) }

            Event.HideDataDetailsButtonPressed -> setState { copy(toShowDataDetails = false) }
            Event.ShowDataDetailsButtonPressed -> setState { copy(toShowDataDetails = true) }

            Event.CancelButtonPressed -> setState { copy(shouldDisplayConfirmCancelDialog = true) }

            Event.ConfirmClickOnCancelConfirmationDialog -> {
                setState { copy(shouldDisplayConfirmCancelDialog = false) }
                onRequestAbandoned()
                doNavigation(PushRoute(DashboardScreens.Dashboard.screenRoute))
            }

            Event.GoToDashboardButtonPressed -> {
                onRequestAbandoned()
                doNavigation(PushRoute(DashboardScreens.Dashboard.screenRoute))
            }

            Event.DismissClickOnCancelConfirmationDialog -> setState {
                copy(
                    shouldDisplayConfirmCancelDialog = false
                )
            }

            Event.IdDeletionDialogDismissed -> {
                setState { copy(shouldDisplayPidDeletionConfirmDialog = false) }
            }

            Event.PidDeletionRequested -> {
                setState {
                    copy(shouldDisplayPidDeletionConfirmDialog = true)

                }
            }

            Event.DeletePidDocumentsRequested -> {
                setEvent(
                    Event.BottomSheet.UpdateBottomSheetState(
                        isOpen = false
                    )
                )

                deletePidDocuments()
            }

            Event.TemporaryPinBlockedBottomSheetButtonPressed -> {
                showBottomSheet(getBottomSheetContentForStep(viewState.value.currentStep))
            }
        }
    }

    private fun updateUserIdentificationItem(id: String) {
        val currentItems = viewState.value.items
        val updatedList = currentItems.map { item ->
            val updatedExpandedItems = item.expandedUiItems.map { expandedItem ->
                val checkbox = expandedItem.uiItem.trailingContentData
                if (expandedItem.uiItem.itemId == id && checkbox is eu.europa.ec.uilogic.component.ListItemTrailingContentData.Checkbox) {
                    val updatedCheckbox =
                        checkbox.checkboxData.copy(isChecked = !checkbox.checkboxData.isChecked)
                    expandedItem.copy(
                        uiItem = expandedItem.uiItem.copy(
                            trailingContentData = eu.europa.ec.uilogic.component.ListItemTrailingContentData.Checkbox(
                                updatedCheckbox
                            )
                        )
                    )
                } else expandedItem
            }
            item.copy(expandedUiItems = updatedExpandedItems)
        }
        updateData(updatedList)
    }

    private fun expandOrCollapseRequestDocumentItem(id: String) {
        val updatedItems = viewState.value.items.map { item ->
            if (item.collapsedUiItem.uiItem.itemId == id) {
                val newIsExpanded = !item.collapsedUiItem.isExpanded
                val newIconData =
                    if (newIsExpanded) eu.europa.ec.uilogic.component.AppIcons.KeyboardArrowUp else eu.europa.ec.uilogic.component.AppIcons.KeyboardArrowDown
                item.copy(
                    collapsedUiItem = item.collapsedUiItem.copy(
                        isExpanded = newIsExpanded,
                        uiItem = item.collapsedUiItem.uiItem.copy(
                            trailingContentData = eu.europa.ec.uilogic.component.ListItemTrailingContentData.Icon(
                                newIconData
                            )
                        )
                    )
                )
            } else item
        }
        updateData(updatedItems)
    }

    protected fun doNavigation(navigationType: NavigationType) {
        when (navigationType) {
            is NavigationType.PushScreen -> {
                setEffect { Effect.Navigation.SwitchScreen(navigationType.screen.screenRoute) }
            }

            is NavigationType.Pop, NavigationType.Finish -> {
                setEffect { Effect.Navigation.Pop }
            }

            is NavigationType.PopTo -> {
                setEffect { Effect.Navigation.PopTo(navigationType.screen.screenRoute) }
            }

            is NavigationType.PushRoute -> {
                setEffect { Effect.Navigation.SwitchScreen(navigationType.route) }
            }

            is NavigationType.Deeplink -> {
                setEffect {
                    Effect.Navigation.DeepLink(
                        navigationType.link.toUri(),
                        navigationType.routeToPop
                    )
                }
            }

            else -> {}
        }
    }

    protected fun getBottomSheetContentForStep(step: RequestScreenStep): RequestBottomSheetContent {
        return when (step) {
            RequestScreenStep.CredentialPreview -> RequestBottomSheetContent(
                title = resourceProvider.getString(R.string.pid_issuance_sheet_explain_data_title),
                description = resourceProvider.getString(R.string.pid_issuance_sheet_explain_data_paragraph)
            )

            RequestScreenStep.EnterPin -> RequestBottomSheetContent(
                title = resourceProvider.getString(R.string.pid_presentation_sheet_wallet_pin_entry_title),
                description = resourceProvider.getString(R.string.pid_presentation_sheet_wallet_pin_entry_paragraph_1),
                isInfo = true
            )

            is RequestScreenStep.TemporaryPinBlocked -> RequestBottomSheetContent(
                title = resourceProvider.getString(R.string.pid_presentation_retry_counter_counter_sec_button),
                description = resourceProvider.getString(R.string.pid_presentation_retry_counter_counter_overlay_paragraph),
                isInfo = false,
                buttonText = resourceProvider.getString(R.string.pid_presentation_retry_counter_counter_overlay_sec_button),

                onButtonClick = { setEvent(Event.PidDeletionRequested) }
            )

            else -> RequestBottomSheetContent(
                title = "",
                description = ""
            )
        }
    }

    protected fun formatTime(iso8601utcTime: String): String {
        val unblockTime = Instant.parse(iso8601utcTime)
        val now = instantProvider.getCurrentInstant()
        val duration = Duration.between(now, unblockTime)

        val totalSeconds = duration.seconds.coerceAtLeast(0) // avoid negative values

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val locale = resourceProvider.getLocale()

        return when {
            hours > 0 -> String.format(locale, "%02d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format(locale, "%02d:%02d", minutes, seconds)
            else -> String.format(locale, "00:%02d", seconds)
        }
    }

    protected fun showBottomSheet(sheetContent: RequestBottomSheetContent) {
        setState {
            copy(sheetContent = sheetContent)
        }
        setEffect {
            Effect.ShowBottomSheet
        }
    }

    private fun unsubscribe() {
        viewModelJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        unsubscribe()
        cleanUp()
        // Leaving the request behind has to erase the PIN as surely as submitting it does.
        viewState.value.pinState.buffer.wipe()
    }
}
