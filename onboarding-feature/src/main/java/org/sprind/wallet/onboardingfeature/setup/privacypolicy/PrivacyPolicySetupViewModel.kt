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

package org.sprind.wallet.onboardingfeature.setup.privacypolicy

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel

/**
 * @property isAccepted the terms of use checkbox is ticked; the step cannot be completed without it.
 */
data class PrivacyPolicySetupState(
    val isAccepted: Boolean = false,
) : ViewState

sealed class PrivacyPolicySetupEvent : ViewEvent {
    /** The terms of use checkbox was toggled. */
    data class SetAccepted(val isAccepted: Boolean) : PrivacyPolicySetupEvent()

    /** The primary action. */
    data object Continue : PrivacyPolicySetupEvent()

    /** The "read the privacy policy" action. */
    data object OpenPrivacyPolicy : PrivacyPolicySetupEvent()

    /** The terms of use link in the checkbox label. */
    data object OpenTermsOfUse : PrivacyPolicySetupEvent()
}

sealed class PrivacyPolicySetupEffect : ViewSideEffect {
    /** The step is done; the host completes it and moves on. */
    data object Complete : PrivacyPolicySetupEffect()

    /** Opens [route] in the app's web view. */
    data class OpenWebPage(val route: SetupWebPageRoute) : PrivacyPolicySetupEffect()
}

/**
 * The Privacy Policy setup step: a summary of the terms of use and the privacy policy, both of which
 * can be read in full, and the acceptance of the terms of use, without which the step cannot be
 * completed. The documents are the ones the settings menu links to ([ConfigLogic.settingsLinks]).
 */
@KoinViewModel
class PrivacyPolicySetupViewModel(
    private val configLogic: ConfigLogic,
) : MviViewModel<PrivacyPolicySetupEvent, PrivacyPolicySetupState, PrivacyPolicySetupEffect>() {

    override fun setInitialState(): PrivacyPolicySetupState = PrivacyPolicySetupState()

    override fun handleEvents(event: PrivacyPolicySetupEvent) {
        when (event) {
            is PrivacyPolicySetupEvent.SetAccepted -> setState { copy(isAccepted = event.isAccepted) }

            PrivacyPolicySetupEvent.Continue -> {
                if (viewState.value.isAccepted) setEffect { PrivacyPolicySetupEffect.Complete }
            }

            PrivacyPolicySetupEvent.OpenPrivacyPolicy -> openWebPage(
                url = configLogic.settingsLinks.privacyPolicy,
                titleRes = R.string.settings_overview_list_privacy_policy,
            )

            PrivacyPolicySetupEvent.OpenTermsOfUse -> openWebPage(
                url = configLogic.settingsLinks.termsAndConditions,
                titleRes = R.string.settings_overview_list_terms_and_conditions,
            )
        }
    }

    private fun openWebPage(url: String, titleRes: Int) = setEffect {
        PrivacyPolicySetupEffect.OpenWebPage(SetupWebPageRoute(url = url, titleRes = titleRes))
    }
}
