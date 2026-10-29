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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
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
 * The blocking screen shown while the device has no screen lock (design "Content device lock"), the
 * failure counterpart of [PlatformAuthSetupScreen] and built from the same setup step layout.
 *
 * It shows the "display locked" failure animation, the title, a paragraph, a notice pointing to the
 * device settings, and a primary action that opens them. The action carries the external-link icon
 * because it leaves the app.
 *
 * Accessibility: the animation is decorative, as the title and the notice say what it shows; the
 * title is a heading and the body scrolls for large font scales (from [SetupStepScreen]).
 *
 * The app cannot be used without a screen lock, so the back arrow and system back leave it
 * ([onBack]); the help button has no content yet, so [onHelp] defaults to doing nothing.
 *
 * @param onOpenSettings invoked by the primary action; opens the device security settings.
 * @param onBack invoked by the back arrow and system back.
 * @param onHelp invoked by the help button.
 */
@Composable
fun PlatformAuthBlockingScreen(
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onHelp: () -> Unit = {},
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleLarge,
        ),
    ) {
        SetupStepScreen(
            title = stringResource(R.string.app_onboarding_pa_set_error_title),
            primaryButtonText = stringResource(R.string.app_onboarding_pa_set_error_prim_button),
            onPrimaryClick = onOpenSettings,
            primaryButtonTrailingIcon = AppIcons.Launch,
            onBack = onBack,
            onHelp = onHelp,
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_MEDIUM.dp),
            illustration = {
                // Decorative: the title and the notice say what the animation shows.
                ContentIllustration(contentDescription = null) {
                    WrapLottieAnimation(
                        animation = R.raw.display_locked_failure,
                        modifier = Modifier.fillMaxSize(),
                        iterations = 1,
                    )
                }
            },
            extraContent = {
                // The paragraph is in the variant color of the designs, which the body slot of the
                // setup layout does not offer.
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.app_onboarding_pa_set_error_paragraph),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ThemeColors.onBackgroundVariant,
                )
                ContentNotice {
                    Text(text = stringResource(R.string.app_onboarding_pa_set_error_paragraph_android))
                }
            },
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PlatformAuthBlockingScreenPreview() {
    PreviewTheme {
        PlatformAuthBlockingScreen(onOpenSettings = {}, onBack = {})
    }
}
