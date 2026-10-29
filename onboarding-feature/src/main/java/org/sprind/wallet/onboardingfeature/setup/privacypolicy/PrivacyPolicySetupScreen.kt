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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyButtonAction
import eu.europa.ec.uilogic.component.wrap.WrapStickyButton
import org.sprind.wallet.onboardingfeature.setup.ui.SetupStepScreen
import org.sprind.wallet.uilogic.component.ContentChecklist
import org.sprind.wallet.uilogic.component.ContentChecklistItem
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle
import org.sprind.wallet.uilogic.component.RoundCheckboxRow

/**
 * The Privacy Policy setup step (design "Setup/privacy_policy"): a summary of the terms of use and
 * the privacy policy, the link to the full privacy policy and the acceptance of the terms of use,
 * which enables the primary action. Both documents open in the app's web view ([SetupWebPageRoute]);
 * completing the step invokes [onComplete].
 *
 * The privacy policy link and the checkbox are pinned to the bottom with the primary action, as in
 * the designs; only the summary scrolls.
 */
@Composable
fun PrivacyPolicySetupScreen(
    viewModel: PrivacyPolicySetupViewModel,
    navController: NavController,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PrivacyPolicySetupEffect.Complete -> onComplete()
                is PrivacyPolicySetupEffect.OpenWebPage -> navController.navigate(effect.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    PrivacyPolicySetupContent(
        state = state,
        onEvent = viewModel::setEvent,
        onBack = onBack,
    )
}

@Composable
private fun PrivacyPolicySetupContent(
    state: PrivacyPolicySetupState,
    onEvent: (PrivacyPolicySetupEvent) -> Unit,
    onBack: () -> Unit,
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleLarge,
            bodyContentColor = ThemeColors.onBackgroundVariant,
        ),
    ) {
        SetupStepScreen(
            title = stringResource(R.string.app_onboarding_policy_intro_title),
            body = stringResource(R.string.app_onboarding_policy_intro_paragraph),
            primaryButtonText = stringResource(R.string.app_onboarding_policy_intro_prim_button),
            onPrimaryClick = { onEvent(PrivacyPolicySetupEvent.Continue) },
            isPrimaryButtonEnabled = state.isAccepted,
            primaryButtonTrailingIcon = null,
            onBack = onBack,
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_MEDIUM.dp),
            extraContent = {
                ContentChecklist(
                    items = listOf(
                        ContentChecklistItem(
                            text = stringResource(R.string.app_onboarding_policy_intro_list_1),
                            icon = AppIcons.Checkmark,
                        ),
                        ContentChecklistItem(
                            text = stringResource(R.string.app_onboarding_policy_intro_list_2),
                            icon = AppIcons.Checkmark,
                        ),
                        ContentChecklistItem(
                            text = stringResource(R.string.app_onboarding_policy_intro_list_3),
                            icon = AppIcons.Checkmark,
                        ),
                    ),
                    boxed = true,
                )
            },
            bottomContent = {
                WrapStickyButton(
                    action = StickyButtonAction(
                        text = stringResource(R.string.app_onboarding_policy_intro_tertiary_button),
                        onClick = { onEvent(PrivacyPolicySetupEvent.OpenPrivacyPolicy) },
                        leadingIcon = AppIcons.Info,
                    ),
                    type = ButtonType.TEXT,
                )
                RoundCheckboxRow(
                    checked = state.isAccepted,
                    onCheckedChange = { onEvent(PrivacyPolicySetupEvent.SetAccepted(it)) },
                ) {
                    Text(
                        text = termsLabel(onOpenTerms = { onEvent(PrivacyPolicySetupEvent.OpenTermsOfUse) }),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ThemeColors.onBackgroundVariant,
                    )
                }
            },
        )
    }
}

/**
 * The checkbox label with its "terms of use" part as a link. Screen readers and keyboards reach the
 * link separately from the checkbox. If a translation no longer contains the link text, the label is
 * shown without the link; the terms stay reachable from the settings menu.
 */
@Composable
private fun termsLabel(onOpenTerms: () -> Unit): AnnotatedString {
    val label = stringResource(R.string.app_onboarding_policy_intro_checkbox_label)
    val linkText = stringResource(R.string.setup_privacy_policy_terms_link)
    val start = label.indexOf(linkText, ignoreCase = true)
    if (start < 0) return AnnotatedString(label)
    val end = start + linkText.length
    val linkStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        append(label.substring(0, start))
        withLink(LinkAnnotation.Clickable(tag = TERMS_LINK_TAG, styles = linkStyles) { onOpenTerms() }) {
            append(label.substring(start, end))
        }
        append(label.substring(end))
    }
}

private const val TERMS_LINK_TAG = "terms_of_use"

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PrivacyPolicySetupPreview() {
    PreviewTheme {
        PrivacyPolicySetupContent(state = PrivacyPolicySetupState(), onEvent = {}, onBack = {})
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PrivacyPolicySetupAcceptedPreview() {
    PreviewTheme {
        PrivacyPolicySetupContent(
            state = PrivacyPolicySetupState(isAccepted = true),
            onEvent = {},
            onBack = {},
        )
    }
}
