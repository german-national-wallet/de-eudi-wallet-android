package org.sprind.wallet.dashboardfeature.ui.documentdetail

import androidx.lifecycle.viewModelScope
import org.sprind.wallet.dashboardfeature.interactor.DashboardDocumentDetailDeleteDocumentPartialState
import org.sprind.wallet.dashboardfeature.interactor.DashboardDocumentDetailInteractor
import org.sprind.wallet.dashboardfeature.interactor.DashboardDocumentDetailInteractorGetDocumentDetail
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

data class State(
    val documentType: String = "",
    val docId: String = "",
    val validTill: String = "",
    val createdOn: String = "",
    val isLoading: Boolean = false,
    val error: ContentErrorConfig? = null,
    val physicalDocumentName: String = "",
    val topBarBackgroundColor: String? = null,
    val topBarBackgroundImageUri: String? = null,
    val topBarTextColor: String? = null,
    val eaaCardData: EaaCardData? = null,
) : ViewState


sealed class Event : ViewEvent {
    data object Init : Event()
    data class GoToDocumentDetails(val docId: String) : Event()
    data object GoToIssuerDetails : Event()
    data object GoToActivities : Event()
    data object DeleteDocument : Event()
    data object OnBackPressed : Event()
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
    }
}

@KoinViewModel
class DashboardDocumentDetailViewModel(
    private val dashboardDocumentDetailInteractor: DashboardDocumentDetailInteractor,
    @InjectedParam private val documentId: DocumentId,
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State {
        return State(docId = documentId)
    }

    override fun handleEvents(event: Event) {
        when (event) {
            Event.Init -> getMainDocument(documentId)
            is Event.GoToDocumentDetails -> goToDocumentDetailsData(event.docId)
            Event.DeleteDocument -> deleteDocument(documentId)
            Event.GoToActivities -> {} //TODO add navigation
            Event.GoToIssuerDetails -> {} //TODO add navigation
            Event.OnBackPressed -> setEffect { Effect.Navigation.Pop }
        }
    }

    private fun goToDocumentDetailsData(docId: DocumentId) {
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = IssuanceScreens.DocumentDetails,
                    arguments = generateComposableArguments(
                        mapOf(
                            IssuanceScreens.DocumentDetails.ParamKey.DOCUMENT_ID to docId
                        )
                    )
                )
            )
        }
    }

    private fun getMainDocument(docId: String) {
        setState {
            copy(
                isLoading = true
            )
        }

        viewModelScope.launch {
            dashboardDocumentDetailInteractor.getDocumentDetail(docId).collect { response ->
                when (response) {
                    is DashboardDocumentDetailInteractorGetDocumentDetail.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { getMainDocument(documentId) },
                                    errorSubTitle = response.error,
                                    onCancel = {
                                        setEffect {
                                            Effect.Navigation.Pop
                                        }
                                    }
                                )
                            )
                        }
                    }

                    is DashboardDocumentDetailInteractorGetDocumentDetail.Success -> {
                        setState {
                            copy(
                                isLoading = false,
                                validTill = response.validTill,
                                createdOn = response.createdOn,
                                documentType = response.documentType,
                                physicalDocumentName = response.physicalDocumentName,
                                topBarBackgroundColor = response.topBarBackgroundColor,
                                topBarBackgroundImageUri = response.topBarBackgroundImageUri,
                                topBarTextColor = response.topBarTextColor,
                                eaaCardData = response.eaaCardData
                            )
                        }
                    }
                }
            }
        }
    }

    private fun deleteDocument(documentId: String) {
        setState {
            copy(
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            dashboardDocumentDetailInteractor.deleteDocument(
                documentId = documentId
            ).collect { response ->
                when (response) {
                    is DashboardDocumentDetailDeleteDocumentPartialState.Success -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = null
                            )
                        }

                        setEffect {
                            Effect.Navigation.Restart
                        }
                    }

                    is DashboardDocumentDetailDeleteDocumentPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(Event.DeleteDocument) },
                                    errorSubTitle = response.error,
                                    onCancel = {
                                        setState {
                                            copy(error = null)
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}