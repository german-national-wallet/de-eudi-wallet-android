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

package org.sprind.wallet.onboardingfeature.setup.success

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import kotlinx.coroutines.delay
import org.sprind.wallet.uilogic.component.SuccessContentScreen
import org.sprind.wallet.uilogic.extension.rememberIsScreenReaderEnabled
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The last app setup step. It has no actions: it completes the step, which navigates to the
 * dashboard, once the success animation has played.
 *
 * With a screen reader on it also announces that the user is being taken to the home screen, and
 * waits for the message to be spoken before leaving; see [RedirectTiming]. Back closes the app, like
 * on the other terminal setup screens.
 */
@Composable
fun SetupSuccessScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val currentOnComplete by rememberUpdatedState(onComplete)
    val isScreenReaderEnabled = rememberIsScreenReaderEnabled()
    var announceRedirect by remember { mutableStateOf(false) }
    // Navigation is asynchronous, so the screen can outlive the call below for a moment. Toggling
    // the screen reader in that window restarts the effect, which must not complete the step again.
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(isScreenReaderEnabled) {
        if (completed) return@LaunchedEffect
        announceRedirect = false
        if (isScreenReaderEnabled) {
            delay(RedirectTiming.ANNOUNCE_AFTER)
            announceRedirect = true
            delay(RedirectTiming.NAVIGATE_AFTER_ANNOUNCEMENT)
        } else {
            delay(RedirectTiming.NAVIGATE_AFTER)
        }
        completed = true
        currentOnComplete()
    }

    SuccessContentScreen(
        title = stringResource(R.string.app_onboarding_success_title),
        announcement = if (announceRedirect) {
            stringResource(R.string.app_onboarding_success_redirect_announcement)
        } else {
            null
        },
        onBack = onBack,
    )
}

/** How long the screen stays before it moves on to the dashboard. */
private object RedirectTiming {
    /** Without a screen reader: long enough for the animation to play and the title to be read. */
    val NAVIGATE_AFTER: Duration = 2.seconds

    /** With a screen reader: lets the title and the image be read first. */
    val ANNOUNCE_AFTER: Duration = 1.seconds

    /** With a screen reader: long enough for the redirect announcement to be spoken. */
    val NAVIGATE_AFTER_ANNOUNCEMENT: Duration = 3.seconds
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun SetupSuccessScreenPreview() {
    PreviewTheme {
        SetupSuccessScreen(onComplete = {}, onBack = {})
    }
}
