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

package org.sprind.wallet.onboardingfeature.setup.platformauth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import org.sprind.wallet.onboardingfeature.setup.ui.SetupStepScreen
import org.sprind.wallet.uilogic.component.ContentIllustration
import org.sprind.wallet.uilogic.component.ContentNotice
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle
import org.sprind.wallet.uilogic.component.WrapLottieAnimation

/**
 * The platform authentication setup step (designs "Setup/pa_check_success" and its sheet): a short
 * checking phase, then the confirmation that the screen lock protects the app. Continuing opens
 * [DeviceLockSheet]; acknowledging it invokes [onComplete].
 */
@Composable
fun PlatformAuthSetupScreen(
    viewModel: PlatformAuthSetupViewModel,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PlatformAuthSetupEffect.Complete -> onComplete()
            }
        }
    }

    PlatformAuthSetupContent(
        state = state,
        onEvent = viewModel::setEvent,
        onBack = onBack,
    )
}

@Composable
private fun PlatformAuthSetupContent(
    state: PlatformAuthSetupState,
    onEvent: (PlatformAuthSetupEvent) -> Unit,
    onBack: () -> Unit,
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleLarge,
        ),
    ) {
        SetupStepScreen(
            title = stringResource(
                if (state.isChecking) {
                    R.string.app_onboarding_pa_set_intro_title_loading
                } else {
                    R.string.app_onboarding_pa_set_success_title
                },
            ),
            // Announce the switch from the checking phase to its result.
            announceTitleChanges = true,
            primaryButtonText = stringResource(R.string.app_onboarding_pa_set_success_prim_button),
            onPrimaryClick = { onEvent(PlatformAuthSetupEvent.Continue) },
            isPrimaryButtonEnabled = !state.isChecking,
            onBack = onBack,
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_MEDIUM.dp),
            illustration = {
                // Decorative: the title and the notice say what the animation shows.
                ContentIllustration(contentDescription = null) {
                    WrapLottieAnimation(
                        animation = R.raw.display_locked_success,
                        modifier = Modifier.fillMaxSize(),
                        iterations = 1,
                    )
                }
            },
            extraContent = {
                AnimatedContent(
                    targetState = state.isChecking,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "platformAuthCheck",
                ) { isChecking ->
                    // While checking, the title says so; the notice fades in with the result.
                    if (!isChecking) {
                        ContentNotice {
                            Text(text = stringResource(R.string.app_onboarding_pa_set_success_paragraph))
                        }
                    }
                }
            },
        )
    }

    if (state.isSheetVisible) {
        DeviceLockSheet(
            onConfirm = { onEvent(PlatformAuthSetupEvent.ConfirmSheet) },
            onDismiss = { onEvent(PlatformAuthSetupEvent.DismissSheet) },
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PlatformAuthSetupCheckingPreview() {
    PreviewTheme {
        PlatformAuthSetupContent(state = PlatformAuthSetupState(), onEvent = {}, onBack = {})
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PlatformAuthSetupCheckedPreview() {
    PreviewTheme {
        PlatformAuthSetupContent(
            state = PlatformAuthSetupState(isChecking = false),
            onEvent = {},
            onBack = {},
        )
    }
}
