package eu.europa.ec.issuancefeature.ui.document.add

import android.net.Uri
import androidx.lifecycle.viewModelScope
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import eu.europa.ec.corelogic.di.getOrCreatePresentationScope
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.corelogic.controller.IssueDocumentPartialState
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

data class State(
    val navigatableAction: ScreenNavigateAction,
    val onBackAction: (() -> Unit)? = null,
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,
    val errorDialog: GenericErrorDialogConfig? = null,
    val isInitialised: Boolean = false,
    val notifyOnAuthenticationFailure: Boolean = false,
) : ViewState

sealed class Event : ViewEvent {
    sealed class Init : Event() {
        data class DeepLinkReceived(val deepLink: Uri?) : Init()
    }

    data object Pop : Event()
    data object OnPause : Event()
    data class OnResumeIssuance(val uri: String) : Event()
    data class OnDynamicPresentation(val uri: String) : Event()
    data object Finish : Event()
    data object DismissError : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data object Finish : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val inclusive: Boolean,
            val popUpRoute: String? = null
        ) : Navigation()

        data class OpenDeepLinkAction(val deepLinkUri: Uri, val arguments: String?) : Navigation()
    }
}

@KoinViewModel
class AddDocumentViewModel(
    private val addDocumentInteractor: AddDocumentInteractor,
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer,
    private val rwscaPinHandler: RwscaPinHandler,
    private val telemetry: Telemetry,
    @InjectedParam private val flowType: IssuanceFlowUiConfig,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State(
        navigatableAction = getNavigatableAction(flowType),
        onBackAction = getOnBackAction(flowType),
    )

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init.DeepLinkReceived -> {
                handleDeepLink(event.deepLink)
            }

            is Event.Pop -> setEffect { Effect.Navigation.Pop }

            is Event.DismissError -> {
                setState { copy(error = null, errorDialog = null, isLoading = false) }
            }

            is Event.Finish -> setEffect { Effect.Navigation.Finish }

            is Event.OnPause -> {
                if (viewState.value.isInitialised) {
                    setState { copy(isLoading = false) }
                }
            }

            is Event.OnResumeIssuance -> {
                setState {
                    copy(isLoading = true)
                }
                addDocumentInteractor.resumeOpenId4VciWithAuthorization(event.uri)
                viewModelScope.launch {
                    try {
                        withTimeout(RESUME_ISSUANCE_TIMEOUT_MS) {
                            addDocumentInteractor.issuanceState.first { response ->
                                when (response) {
                                    is IssueDocumentPartialState.Success -> {
                                        setState { copy(isLoading = false, error = null) }
                                        setEvent(Event.Finish)
                                        true
                                    }

                                    is IssueDocumentPartialState.Failure -> {
                                        setState {
                                            copy(
                                                isLoading = false,
                                                error = ContentErrorConfig(
                                                    errorSubTitle = response.errorMessage,
                                                    onCancel = { setEvent(Event.DismissError) }
                                                )
                                            )
                                        }
                                        true
                                    }

                                    else -> { false }
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    errorSubTitle = resourceProvider.getString(R.string.issuance_interrupted_error),
                                    onCancel = { setEvent(Event.DismissError) }
                                )
                            )
                        }
                    }
                }
            }

            is Event.OnDynamicPresentation -> {
                getOrCreatePresentationScope()
                telemetry.startSpan(TelemetryConstants.PRESENTATION)
                setEffect {
                    Effect.Navigation.SwitchScreen(
                        generateComposableNavigationLink(
                            PresentationScreens.PresentationRequest,
                            generateComposableArguments(
                                mapOf(
                                    RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                                        RequestUriConfig(
                                            PresentationMode.OpenId4Vp(
                                                event.uri,
                                                IssuanceScreens.AddDocument.screenRoute
                                            )
                                        ),
                                        RequestUriConfig
                                    )
                                )
                            )
                        ),
                        inclusive = false
                    )
                }
            }

        }
    }

    private fun getNavigatableAction(flowType: IssuanceFlowUiConfig): ScreenNavigateAction {
        return when (flowType) {
            IssuanceFlowUiConfig.NO_DOCUMENT -> ScreenNavigateAction.NONE
            IssuanceFlowUiConfig.EXTRA_DOCUMENT -> ScreenNavigateAction.BACKABLE
        }
    }

    private fun getOnBackAction(flowType: IssuanceFlowUiConfig): (() -> Unit) {
        return when (flowType) {
            IssuanceFlowUiConfig.NO_DOCUMENT -> {
                { setEvent(Event.Finish) }
            }

            IssuanceFlowUiConfig.EXTRA_DOCUMENT -> {
                { setEvent(Event.Pop) }
            }
        }
    }

    private fun handleDeepLink(deepLinkUri: Uri?) {
        deepLinkUri?.let { uri ->
            hasDeepLink(uri)?.let {
                when (it.type) {
                    DeepLinkType.CREDENTIAL_OFFER -> {
                        setEffect {
                            Effect.Navigation.OpenDeepLinkAction(
                                deepLinkUri = uri,
                                arguments = generateComposableArguments(
                                    mapOf(
                                        OfferUiConfig.serializedKeyName to uiSerializer.toBase64(
                                            OfferUiConfig(
                                                offerURI = it.link.toString(),
                                                onSuccessNavigation = ConfigNavigation(
                                                    navigationType = NavigationType.PushScreen(
                                                        screen = DashboardScreens.Dashboard,
                                                        popUpToScreen = IssuanceScreens.AddDocument
                                                    )
                                                ),
                                                onCancelNavigation = ConfigNavigation(
                                                    navigationType = NavigationType.Pop
                                                )
                                            ),
                                            OfferUiConfig.Parser
                                        )
                                    )
                                )
                            )
                        }
                    }

                    DeepLinkType.EXTERNAL -> {
                        setEffect {
                            Effect.Navigation.OpenDeepLinkAction(
                                deepLinkUri = uri,
                                arguments = null
                            )
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        rwscaPinHandler.clearPinSession()
    }
}

private const val RESUME_ISSUANCE_TIMEOUT_MS = 5_000L