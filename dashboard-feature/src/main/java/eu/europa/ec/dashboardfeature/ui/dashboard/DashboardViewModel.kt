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

package eu.europa.ec.dashboardfeature.ui.dashboard

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.di.getOrCreatePresentationScope
import eu.europa.ec.corelogic.controller.ResolvePreferredPidConfigurationsPartialState
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractorGetIssuedDocumentsPartialState
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.extension.shareLogs
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.RouterHost
import eu.europa.ec.uilogic.navigation.helper.DeepLinkType
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.navigation.helper.hasDeepLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.annotation.KoinViewModel
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import org.sprind.wallet.businesslogic.config.EidCardType
import org.sprind.wallet.businesslogic.config.UserRuntimeConfig
import org.sprind.wallet.businesslogic.util.SpanAttributes

data class State(
    val appVersion: String = "",
    val pidDocument: IssuedDocument? = null,
    val eaaDocuments: List<EaaCardData> = emptyList(),
    val eidCardType: EidCardType = EidCardType.PHYSICAL,
    val isDebugMenuEnabled: Boolean = false,
    val isLogWriterEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,
    val errorDialog: GenericErrorDialogConfig? = null,
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object OpenIssuance : Event()
    data class InitDeepLink(val deepLinkUri: Uri?) : Event()
    data object Pop : Event()
    data class CardPressed(val docId: String) : Event()
    data class OnDynamicPresentation(val uri: String) : Event()
    data object OnInterruptedIssuance : Event()
    data object DismissError : Event()
    data class ExportLogs(val context: Context): Event()
    data class IssueDocument(val credentialTypes: Set<CredentialConfigurationIdentifier>) : Event()
    /**
     * Triggers async resolution of the preferred (beta-first) PID configuration
     * IDs from the PID issuer, then navigates to the card reader with the resolved
     * subset. Emits an error dialog if the issuer advertises no recognized PID
     * configuration or the metadata fetch fails.
     */
    data object IssuePreferredPidDocument : Event()
    data class ToggleEidCardSimulation(val useVirtualEidCard: Boolean) : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data object Restart : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Dashboard.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()

        data class OpenDeepLinkAction(val deepLinkUri: Uri, val arguments: String?) :
            Navigation()
    }
}

@KoinViewModel
class DashboardViewModel(
    private val dashboardInteractor: DashboardInteractor,
    private val uiSerializer: UiSerializer,
    private val telemetry: Telemetry,
    private val configLogic: ConfigLogic,
    private val userRuntimeConfig: UserRuntimeConfig,
    private val routerHost: RouterHost,
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State {
        return State(
            isDebugMenuEnabled = configLogic.isDebugMenuEnabled,
            isLogWriterEnabled = configLogic.isLogWriterEnabled,
            appVersion = dashboardInteractor.getAppVersion(),
            eidCardType = userRuntimeConfig.eidCardType,
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> {
                val interruptedRedirect = routerHost.getNavController()
                    .currentBackStackEntry
                    ?.savedStateHandle
                    ?.get<String>(CoreActions.INTERRUPTED_ISSUANCE_REDIRECT_KEY)
                if (interruptedRedirect != null) {
                    routerHost.getNavController().currentBackStackEntry
                        ?.savedStateHandle
                        ?.remove<String>(CoreActions.INTERRUPTED_ISSUANCE_REDIRECT_KEY)
                    setEvent(Event.OnInterruptedIssuance)
                }
                getIssuedDocuments()
            }

            Event.OpenIssuance -> {
                setEffect {
                    val addDocumentScreenRoute = generateComposableNavigationLink(
                        screen = IssuanceScreens.AddDocument,
                        arguments = generateComposableArguments(
                            mapOf(
                                "flowType" to IssuanceFlowUiConfig.fromIssuanceFlowUiConfig(
                                    IssuanceFlowUiConfig.NO_DOCUMENT
                                )
                            )
                        )
                    )
                    Effect.Navigation.SwitchScreen(
                        screenRoute = addDocumentScreenRoute,
                        inclusive = false
                    )
                }
            }

            is Event.InitDeepLink -> handleDeepLink(event.deepLinkUri)

            is Event.CardPressed -> goToDocumentDetails(event.docId)

            is Event.Pop -> setEffect { Effect.Navigation.Pop }

            is Event.OnInterruptedIssuance -> {
                setState {
                    copy(
                        isLoading = false,
                        errorDialog = GenericErrorDialogConfig(
                            titleRes = R.string.issuance_interrupted_error_title,
                            bodyTextRes = R.string.issuance_interrupted_error_paragraph,
                            errorCode = "ISSUANCE_INTERRUPTED",
                            traceId = telemetry.currentTraceId(),
                            dismissable = true,
                            primaryButtonTextRes = R.string.global_error_prim_button,
                            onPrimaryButtonClick = { setEvent(Event.DismissError) },
                            onDismiss = { setEvent(Event.DismissError) },
                        )
                    )
                }
            }

            is Event.DismissError -> {
                setState { copy(error = null, errorDialog = null) }
            }

            is Event.OnDynamicPresentation -> {
                getOrCreatePresentationScope()
                setEffect {
                    Effect.Navigation.SwitchScreen(
                        screenRoute = generateComposableNavigationLink(
                            screen = PresentationScreens.PresentationRequest,
                            arguments = generateComposableArguments(
                                mapOf(
                                    pair = RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                                        model = RequestUriConfig(
                                            mode = PresentationMode.OpenId4Vp(
                                                event.uri,
                                                IssuanceScreens.AddDocument.screenRoute
                                            )
                                        ),
                                        parser = RequestUriConfig
                                    )
                                )
                            )
                        ),
                        inclusive = false
                    )
                }
            }

            is Event.ExportLogs -> {
                if (configLogic.isLogWriterEnabled) {
                    event.context.shareLogs(dashboardInteractor.retrieveLogFileUris())
                }
            }

            is Event.IssueDocument -> {
                navigateToReadCardScreen(credentialTypes = event.credentialTypes)
            }

            is Event.IssuePreferredPidDocument -> {
                viewModelScope.launch {
                    when (val result = dashboardInteractor.resolvePreferredPidConfigurations()) {
                        is ResolvePreferredPidConfigurationsPartialState.Success -> {
                            navigateToReadCardScreen(credentialTypes = result.configurationIds)
                        }

                        is ResolvePreferredPidConfigurationsPartialState.NoPidConfigurationsAdvertised -> {
                            setState {
                                copy(
                                    isLoading = false,
                                    errorDialog = GenericErrorDialogConfig(
                                        titleRes = R.string.global_error_title,
                                        bodyTextRes = R.string.issuance_generic_error,
                                        errorCode = "NO_PID_CONFIG_ADVERTISED",
                                        traceId = telemetry.currentTraceId(),
                                        dismissable = true,
                                        primaryButtonTextRes = R.string.global_error_prim_button,
                                        onPrimaryButtonClick = { setEvent(Event.DismissError) },
                                        onDismiss = { setEvent(Event.DismissError) },
                                    )
                                )
                            }
                        }

                        is ResolvePreferredPidConfigurationsPartialState.Failure -> {
                            setState {
                                copy(
                                    isLoading = false,
                                    errorDialog = GenericErrorDialogConfig(
                                        titleRes = R.string.global_error_title,
                                        bodyTextRes = R.string.issuance_generic_error,
                                        errorCode = "PID_CONFIG_RESOLVE_FAILED",
                                        traceId = telemetry.currentTraceId(),
                                        dismissable = true,
                                        primaryButtonTextRes = R.string.global_error_prim_button,
                                        onPrimaryButtonClick = { setEvent(Event.DismissError) },
                                        onDismiss = { setEvent(Event.DismissError) },
                                    )
                                )
                            }
                        }
                    }
                }
            }

            is Event.ToggleEidCardSimulation -> {
                val eidCardType = if(event.useVirtualEidCard) EidCardType.VIRTUAL else EidCardType.PHYSICAL
                userRuntimeConfig.eidCardType = eidCardType
                setState { copy(eidCardType = eidCardType) }
            }
        }
    }

    private fun getIssuedDocuments() {
        setState {
            copy(
                isLoading = true
            )
        }

        viewModelScope.launch {
            dashboardInteractor.getIssuedDocuments().collect { response ->
                when (response) {
                    is DashboardInteractorGetIssuedDocumentsPartialState.Failure -> {
                        telemetry.endSpan(TelemetryConstants.ISSUANCE)
                        setState {
                            copy(
                                isLoading = false
                            )
                        }
                    }

                    is DashboardInteractorGetIssuedDocumentsPartialState.Success -> {
                        telemetry.endSpan(TelemetryConstants.ISSUANCE)
                        setState {
                            copy(
                                error = null,
                                isLoading = false,
                                pidDocument = response.pidDocument,
                                eaaDocuments = response.eaaDocuments,
                            )
                        }
                    }

                    DashboardInteractorGetIssuedDocumentsPartialState.Restart ->
                        setEffect { Effect.Navigation.Restart }
                }
            }
        }
    }

    private fun handleDeepLink(deepLinkUri: Uri?) {
        deepLinkUri?.let { uri ->
            hasDeepLink(uri)?.let {
                val arguments: String? = when (it.type) {
                    DeepLinkType.OPENID4VP -> {
                        telemetry.startSpan(
                            TelemetryConstants.PRESENTATION,
                            SpanAttributes(mapOf("presentation_request" to uri.toString()))
                        )
                        getOrCreatePresentationScope()
                        generateComposableArguments(
                            mapOf(
                                RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                                    RequestUriConfig(
                                        PresentationMode.OpenId4Vp(
                                            uri.toString(),
                                            DashboardScreens.Dashboard.screenRoute
                                        )
                                    ),
                                    RequestUriConfig.Parser
                                )
                            )
                        )
                    }

                    DeepLinkType.CREDENTIAL_OFFER -> {
                        generateComposableArguments(
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
                    }

                    else -> null
                }
                setEffect {
                    Effect.Navigation.OpenDeepLinkAction(
                        deepLinkUri = uri,
                        arguments = arguments
                    )
                }
            }
        }
    }

    private fun goToDocumentDetails(docId: DocumentId) {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = DashboardScreens.DashboardDocumentDetails,
                    arguments = generateComposableArguments(
                        mapOf(
                            "documentId" to docId
                        )
                    )
                )
            )
        }
    }

    private fun navigateToReadCardScreen(credentialTypes: Set<CredentialConfigurationIdentifier>) {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = eu.europa.ec.uilogic.navigation.CardReaderScreens.Reader,
                    arguments = generateComposableArguments(
                        mapOf(
                            "flowType" to IssuanceFlowUiConfig.fromIssuanceFlowUiConfig(
                                IssuanceFlowUiConfig.NO_DOCUMENT
                            ),
                            "credentialTypes" to Json.encodeToString(credentialTypes)
                        )
                    )
                ),
                inclusive = true,
            )
        }
    }
}
