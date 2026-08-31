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

package eu.europa.ec.startupfeature.ui.splash

import androidx.lifecycle.viewModelScope
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.startupfeature.interactor.registration.WalletInitialRegistrationPartialState
import eu.europa.ec.startupfeature.interactor.registration.WalletRegistrationInteractor
import eu.europa.ec.startupfeature.interactor.splash.SplashInteractor
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.sprind.wallet.uilogic.navigation.NavigationGuard
import org.sprind.wallet.uilogic.navigation.getDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class State(
    val logoAnimationDuration: Int = 1500,
    val errorDialog: GenericErrorDialogConfig? = null,
) : ViewState

sealed class Event : ViewEvent {
    data object Initialize : Event()
    data object DismissError : Event()
}

sealed class Effect : ViewSideEffect {

    sealed class Navigation : Effect() {
        data class SwitchModule(val moduleRoute: ModuleRoute) : Navigation()
        data class SwitchScreen(val route: String) : Navigation()
    }
}

@KoinViewModel
class SplashViewModel(
    private val interactor: SplashInteractor,
    private val walletRegistrationInteractor: WalletRegistrationInteractor,
    private val logController: LogController,
    private val navigationGuards: List<NavigationGuard>,
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            Event.Initialize -> {
                initializeWallet()
            }

            Event.DismissError -> setState {
                copy(
                    errorDialog = null
                )
            }
        }
    }

    private fun initializeWallet() {
        viewModelScope.launch {
            delay((viewState.value.logoAnimationDuration).toLong())
            walletRegistrationInteractor.registerWallet().collect { result ->
                logController.d(javaClass.simpleName) { "wallet registration result: $result" }
                when (result) {
                    WalletInitialRegistrationPartialState.AlreadyRegistered, WalletInitialRegistrationPartialState.Success -> {
                        enterApplication()
                    }

                    is WalletInitialRegistrationPartialState.Failure -> {
                        val (titleRes, bodyTextRes, primaryButtonTextRes) = when (result.errorCode) {
                            WalletInitialRegistrationPartialState.ErrorCode.WB_SERVICE_UNAVAILABLE -> Triple(
                                R.string.app_onboarding_wb_service_unavailable_title,
                                R.string.app_onboarding_wb_service_unavailable_paragraph,
                                R.string.app_onboarding_wb_service_unavailable_prim_button
                            )

                            WalletInitialRegistrationPartialState.ErrorCode.WB_BAD_REQUEST -> Triple(
                                R.string.app_onboarding_wb_bad_request_title,
                                R.string.app_onboarding_wb_bad_request_paragraph,
                                R.string.app_onboarding_wb_bad_request_prim_button
                            )

                            WalletInitialRegistrationPartialState.ErrorCode.WB_INTERNAL_ERROR -> Triple(
                                R.string.app_onboarding_wb_internal_error_title,
                                R.string.app_onboarding_wb_internal_error_paragraph,
                                R.string.app_onboarding_wb_internal_error_prim_button
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
                                    onDismiss = { setEvent(Event.DismissError) },
                                    onPrimaryButtonClick = {
                                        setEvent(Event.DismissError)
                                        initializeWallet() // retry
                                    }
                                ),
                            )
                        }

                    }
                }
            }
        }
    }

    private fun enterApplication() {
        setEffect {
            Effect.Navigation.SwitchScreen(
                route = navigationGuards.getDirection(
                    destination = ModuleRoute.DashboardModule.route
                )
            )
        }
    }
}