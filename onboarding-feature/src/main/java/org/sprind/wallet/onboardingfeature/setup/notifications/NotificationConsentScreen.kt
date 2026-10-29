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

package org.sprind.wallet.onboardingfeature.setup.notifications

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import org.sprind.wallet.onboardingfeature.BuildConfig
import org.sprind.wallet.onboardingfeature.setup.ui.SetupStepScreen
import org.sprind.wallet.uilogic.component.ContentChecklist
import org.sprind.wallet.uilogic.component.ContentChecklistItem
import org.sprind.wallet.uilogic.component.ContentIllustration
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle

/**
 * The notification consent step, and the only place the app requests `POST_NOTIFICATIONS`.
 *
 * Android only shows the system permission dialog a limited number of times: after the user denies
 * it (or dismisses it) twice, the request is silently refused and the permission can only be
 * granted from the app's system settings. Because a retry on this screen could therefore do
 * nothing, every outcome continues the setup flow instead of keeping the user here:
 *
 * - **Enable notifications** requests the permission. The step completes once the system answers,
 *   whether the user allowed or denied. If the permission is already granted, or
 *   `ENABLE_PNS_PERMISSION_POP_UP` is off, no dialog is shown and the step completes immediately.
 * - **Later in Settings** completes the step without requesting anything; the user can enable
 *   notifications later in the app's settings.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Suppress("KotlinConstantCondition", "SimplifyBooleanWithConstants")
@Composable
fun NotificationConsentScreen(
    viewModel: NotificationConsentViewModel,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val currentOnComplete by rememberUpdatedState(onComplete)
    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) {
        currentOnComplete()
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { consent ->
            when (consent.effect) {
                NotificationConsentEffect.Effect.GrantConsent -> {
                    // The flag keeps CI UI automation from being blocked by the system dialog. It is a
                    // per-variant BuildConfig constant, so the IDE may suggest dropping it; that is wrong
                    // because it is false for CI builds (-PenablePNSPermissionPopUp=false).
                    val needsRequest = BuildConfig.ENABLE_PNS_PERMISSION_POP_UP &&
                        !permissionState.status.isGranted
                    if (needsRequest) {
                        permissionState.launchPermissionRequest()
                    } else {
                        currentOnComplete()
                    }
                }
            }
        }
    }

    NotificationConsentContent(
        onEnable = {
            viewModel.setEvent(NotificationConsentEvent(NotificationConsentEvent.Event.GrantConsent))
        },
        onLater = onComplete,
        onBack = onBack,
        onHelp = {
            viewModel.setEvent(NotificationConsentEvent(NotificationConsentEvent.Event.HelpPressed))
        }
    )
}

@Composable
private fun NotificationConsentContent(
    onEnable: () -> Unit,
    onLater: () -> Unit,
    onBack: () -> Unit,
    onHelp: () -> Unit,
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleLarge,
        ),
    ) {
        SetupStepScreen(
            title = stringResource(R.string.app_onboarding_push_intro_title),
            primaryButtonText = stringResource(R.string.app_onboarding_push_intro_prim_button),
            onPrimaryClick = onEnable,
            primaryButtonTrailingIcon = null,
            secondaryButtonText = stringResource(R.string.app_onboarding_push_intro_sec_button),
            onSecondaryClick = onLater,
            onBack = onBack,
            onHelp = onHelp,
            // The fade hints at text hidden behind the sticky buttons, e.g. at 200% font scale.
            templateConfig = ContentTemplateConfig(
                verticalSpacing = SPACING_MEDIUM.dp,
                showScrollFade = true,
            ),
            illustration = {
                // Decoration: the title already states the purpose of the screen.
                ContentIllustration(contentDescription = null) {
                    Image(
                        painter = painterResource(R.drawable.push_notification_consent),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            },
            extraContent = {
                ContentChecklist(
                    contentColor = ThemeColors.onBackgroundVariant,
                    items = listOf(
                        ContentChecklistItem(
                            text = stringResource(R.string.app_onboarding_push_intro_list_1),
                            icon = AppIcons.Renew,
                        ),
                        ContentChecklistItem(
                            text = stringResource(R.string.app_onboarding_push_intro_list_2),
                            icon = AppIcons.Notification,
                        ),
                    ),
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.app_onboarding_push_intro_paragraph),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ThemeColors.onBackgroundVariant,
                )
            },
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NotificationConsentContentPreview() {
    PreviewTheme {
        NotificationConsentContent(onEnable = {}, onLater = {}, onBack = {}, onHelp = {})
    }
}
