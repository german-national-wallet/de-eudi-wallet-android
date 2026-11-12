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

package org.sprind.wallet.uilogic.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton

/** The size of the illustration in the design (WD-4524). */
private const val ILLUSTRATION_SIZE_DP = 165

/**
 * The patience screen shown while an operation on the wallet backend's HSM is slow or the HSM is
 * overloaded, instead of an endless loader or an error (WD-5013, design WD-4524): an illustration,
 * "Please be patient", and a loader. The caller decides when to offer a retry, by setting
 * [canRetry], which shows the "Try again later" button.
 *
 * Accessibility: the illustration is decorative, as the title says what is happening. The retry
 * button is a polite live region, so that screen readers may announce it as it appears.
 *
 * @param canRetry Whether to show the retry button.
 * @param onRetry Runs the operation again.
 * @param onClose Invoked by the close button and system back; leaves the flow.
 */
@Composable
fun HsmDelayScreen(
    canRetry: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.CANCELABLE,
        onBack = onClose,
        stickyBottom = if (canRetry) {
            { padding ->
                WrapButton(
                    buttonConfig = ButtonConfig(type = ButtonType.PRIMARY, onClick = onRetry),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(stringResource(R.string.hsm_delay_retry_button))
                }
            }
        } else {
            null
        },
    ) { paddingValues ->
        ProvideContentTemplateStyle(
            style = ContentTemplateDefaults.style.copy(
                titleTextStyle = MaterialTheme.typography.titleLarge,
            ),
        ) {
            ContentTemplateBody(
                screenPadding = paddingValues,
                templateConfig = ContentTemplateConfig(
                    verticalSpacing = SPACING_MEDIUM.dp,
                    centerContent = true,
                ),
                illustration = {
                    Image(
                        painter = painterResource(R.drawable.hsm_preparing_app),
                        contentDescription = null,
                        modifier = Modifier.size(ILLUSTRATION_SIZE_DP.dp),
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.hsm_delay_title),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                body = {
                    Text(
                        text = stringResource(R.string.hsm_delay_paragraph),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                extraContent = {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun HsmDelayScreenWaitingPreview() {
    PreviewTheme {
        HsmDelayScreen(canRetry = false, onRetry = {}, onClose = {})
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun HsmDelayScreenRetryPreview() {
    PreviewTheme {
        HsmDelayScreen(canRetry = true, onRetry = {}, onClose = {})
    }
}
