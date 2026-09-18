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

package org.sprind.wallet.onboardingfeature.ui

import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.koin.android.annotation.KoinViewModel
import org.sprind.wallet.onboardingfeature.interactor.OnboardingInteractor
import org.sprind.wallet.uilogic.navigation.NavigationGuard
import org.sprind.wallet.uilogic.navigation.getDirection

class State : ViewState

class Event : ViewEvent

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val route: String) : Navigation()
    }
}

@KoinViewModel
class OnboardingViewModel(
    private val interactor: OnboardingInteractor,
    private val navigationGuards: List<NavigationGuard>,
) : MviViewModel<Event, State, Effect>() {

    val pages = listOf(
        OnboardingPageModel(
            titleRes = R.string.app_onboarding_onboarding_1_title,
            bodyRes = R.string.app_onboarding_onboarding_1_paragraph,
            animationRes = R.raw.onboarding_1_video,
            placeholderRes = R.drawable.onboarding_1_placeholder,
            primaryButtonTextRes = R.string.app_onboarding_onboarding_1_prim_button,
            secondaryButtonTextRes = R.string.app_onboarding_onboarding_1_tertiary_button,
        ),
        OnboardingPageModel(
            titleRes = R.string.app_onboarding_onboarding_2_title,
            bodyRes = R.string.app_onboarding_onboarding_2_paragraph,
            animationRes = R.raw.onboarding_2_video,
            placeholderRes = R.drawable.onboarding_2_placeholder,
            primaryButtonTextRes = R.string.app_onboarding_onboarding_2_prim_button,
            secondaryButtonTextRes = R.string.app_onboarding_onboarding_2_tertiary_button,
            isAnimationPausable = true,
        ),
        OnboardingPageModel(
            titleRes = R.string.app_onboarding_onboarding_4_title,
            bodyRes = R.string.app_onboarding_onboarding_4_paragraph,
            animationRes = R.raw.onboarding_3_video,
            placeholderRes = R.drawable.onboarding_3_placeholder,
            primaryButtonTextRes = R.string.app_onboarding_onboarding_4_prim_button,
            secondaryButtonTextRes = null,
            isLastPage = true
        ),
    )

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) = Unit

    fun enterApplication() {
        interactor.storeUserCompletedOnboarding(true)
        setEffect {
            Effect.Navigation.SwitchScreen(
                route = navigationGuards.getDirection(
                    destination = ModuleRoute.DashboardModule.route
                )
            )
        }
    }
}