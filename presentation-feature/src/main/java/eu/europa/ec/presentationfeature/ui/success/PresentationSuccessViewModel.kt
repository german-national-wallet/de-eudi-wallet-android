package eu.europa.ec.presentationfeature.ui.success

import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.ui.document_success.DocumentSuccessViewModel
import eu.europa.ec.corelogic.di.closePresentationScope
import eu.europa.ec.presentationfeature.interactor.PresentationSuccessInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationSuccessInteractorGetUiItemsPartialState
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.DashboardScreens
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class PresentationSuccessViewModel(
    private val interactor: PresentationSuccessInteractor,
) : DocumentSuccessViewModel() {

    override fun getNextScreenConfigNavigation(): ConfigNavigation {
        val redirectUri = interactor.redirectUri
        val deepLinkWithUriOrPopToDashboard = ConfigNavigation(
            navigationType = redirectUri?.let {
                NavigationType.Deeplink(it.toString(), interactor.initiatorRoute)
            } ?: NavigationType.PushRoute(DashboardScreens.Dashboard.screenRoute)
        )

        return deepLinkWithUriOrPopToDashboard
    }

    override fun doWork() {
        setState {
            copy(isLoading = true)
        }

        viewModelScope.launch {
            interactor.getUiItems().collect { response ->
                when (response) {
                    is PresentationSuccessInteractorGetUiItemsPartialState.Failed -> {
                        setState {
                            copy(
                                isLoading = false,
                            )
                        }
                    }

                    is PresentationSuccessInteractorGetUiItemsPartialState.Success -> {
                        setState {
                            copy(
                                headerConfig = response.headerConfig,
                                items = response.documentsUi,
                                stickyButtonText = response.stickyButtonText,
                                isLoading = false,
                                redirectUrlAvailable = interactor.redirectUri != null
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        interactor.stopPresentation()
        closePresentationScope()
    }
}
