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

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.extension.findActivity
import org.sprind.wallet.onboardingfeature.setup.model.SetupStep

/**
 * Hosts a setup [step]: runs the [SetupStepViewModel] navigation effects and renders [content],
 * handing it the callback that completes the step.
 */
@Composable
fun SetupStepRoute(
    navController: NavController,
    step: SetupStep,
    viewModel: SetupStepViewModel,
    content: @Composable (onComplete: () -> Unit, onBack: () -> Unit) -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SetupStepEffect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.route) {
                        if (effect.clearBackStack) {
                            popUpTo(0) { inclusive = true }
                        } else {
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }

    content(
        { viewModel.setEvent(SetupStepEvent.Complete(step)) },
        {
            // Back returns to the previous step. The first step (onboarding clears itself) and
            // the success step, which concludes setup, close the app instead.
            if (step != SetupStep.SUCCESS && navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                context.findActivity().finish()
            }
        },
    )
}

/**
 * Minimal content for a [step] whose screen is not implemented yet: title, paragraph and primary
 * action from the design strings. Replaced by the dedicated screens of the setup sub-tasks.
 */
@Composable
fun SetupStepPlaceholder(
    step: SetupStep,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val texts = step.placeholderTexts()
    SetupStepScreen(
        title = stringResource(texts.title),
        body = texts.body?.let { stringResource(it) },
        primaryButtonText = stringResource(texts.primaryButton),
        onPrimaryClick = onComplete,
        onBack = onBack,
        // The success screen concludes setup and has no header.
        showTopBar = step != SetupStep.SUCCESS,
    )
}

private data class PlaceholderTexts(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int?,
    @param:StringRes val primaryButton: Int,
)

private fun SetupStep.placeholderTexts(): PlaceholderTexts = when (this) {
    SetupStep.PLATFORM_AUTH -> PlaceholderTexts(
        title = R.string.app_onboarding_pa_set_intro_title,
        body = R.string.app_onboarding_pa_set_intro_paragraph,
        primaryButton = R.string.app_onboarding_pa_set_intro_prim_button,
    )

    SetupStep.PRIVACY_POLICY -> PlaceholderTexts(
        title = R.string.app_onboarding_policy_intro_title,
        body = R.string.app_onboarding_policy_intro_paragraph,
        primaryButton = R.string.app_onboarding_policy_intro_prim_button,
    )

    SetupStep.NOTIFICATIONS -> PlaceholderTexts(
        title = R.string.app_onboarding_push_intro_title,
        body = R.string.app_onboarding_push_intro_paragraph,
        primaryButton = R.string.app_onboarding_push_intro_prim_button,
    )

    SetupStep.SUCCESS -> PlaceholderTexts(
        title = R.string.app_onboarding_success_title,
        body = null,
        primaryButton = R.string.app_onboarding_success_prim_button,
    )
}
