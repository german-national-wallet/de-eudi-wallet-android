/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.onboardingfeature.setup.ui

import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel
import org.sprind.wallet.onboardingfeature.setup.interactor.SetupInteractor
import org.sprind.wallet.onboardingfeature.setup.model.SetupStep

data object SetupStepState : ViewState

sealed class SetupStepEvent : ViewEvent {
    data class Complete(val step: SetupStep) : SetupStepEvent()
}

sealed class SetupStepEffect : ViewSideEffect {
    sealed class Navigation : SetupStepEffect() {
        /**
         * Continue with [route]. The setup steps stay on the back stack, so back returns to the
         * previous step; leaving setup ([clearBackStack], the dashboard) clears it so back from the
         * dashboard does not reopen setup.
         */
        data class SwitchScreen(val route: String, val clearBackStack: Boolean) : Navigation()
    }
}

/**
 * Shared view model for a setup step: marks the step completed and continues with whatever the
 * navigation guards point to next (the next setup step, or the dashboard).
 */
@KoinViewModel
class SetupStepViewModel(
    private val setupInteractor: SetupInteractor,
) : MviViewModel<SetupStepEvent, SetupStepState, SetupStepEffect>() {

    override fun setInitialState(): SetupStepState = SetupStepState

    override fun handleEvents(event: SetupStepEvent) {
        when (event) {
            is SetupStepEvent.Complete -> {
                val route = setupInteractor.completeStep(event.step)
                setEffect {
                    SetupStepEffect.Navigation.SwitchScreen(
                        route = route,
                        clearBackStack = route == ModuleRoute.DashboardModule.route,
                    )
                }
            }
        }
    }
}
