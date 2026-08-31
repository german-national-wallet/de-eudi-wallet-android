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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.wrap.WrapImage
import org.sprind.wallet.designsystem.typography.CustomTypography

/**
 * The shared body of every code-entry screen: a title, an optional illustration, and the one
 * [CodeEntryField].
 *
 * This replaces the seven near-identical layout shells the wallet used to carry — one per flow —
 * which differed only in their title, their hero image and the code's length. Those three are now
 * parameters, so a new code-entry screen is a call rather than a copy.
 *
 * It renders through [ContentTemplateBody], so it inherits that template's scrolling behaviour and
 * heading semantics and stays consistent with the rest of the content screens. Screen chrome —
 * toolbar, sticky buttons, loading and error state — is deliberately **not** handled here: callers
 * own their [ContentScreen], because the flows differ far more in their chrome (one button vs two,
 * cancellable vs backable, bottom sheets) than in their body.
 *
 * @param title the screen's headline, e.g. "Enter the 6-digit transaction code".
 * @param state the entry state; [CodeEntryState.supportingText] is shown under the cells.
 * @param onCodeChange called after every edit; see [CodeEntryField].
 * @param modifier applied to the template body; pass the [ContentScreen] padding here.
 * @param config visibility and focus behaviour; see [CodeEntryConfig].
 * @param titleTextStyle style for the headline. Defaults to `titleLarge`, which is what the wallet
 *   PIN design asks for; the transaction-code design uses a smaller `titleMedium`.
 * @param illustration optional artwork between the title and the cells, centred horizontally by the
 *   template. The two designed screens pass none; the card-reader screens keep their card and
 *   letter imagery.
 */
@Composable
fun CodeEntryBody(
    title: String,
    state: CodeEntryState,
    onCodeChange: () -> Unit,
    modifier: Modifier = Modifier,
    config: CodeEntryConfig = CodeEntryConfig(),
    titleTextStyle: TextStyle = MaterialTheme.typography.titleLarge,
    illustration: (@Composable () -> Unit)? = null,
) {
    // Based on the ambient style rather than the defaults, so overriding the headline here does not
    // silently reset the other fields an outer ProvideContentTemplateStyle may have set.
    val baseStyle = LocalContentTemplateStyle.current ?: ContentTemplateDefaults.style

    ProvideContentTemplateStyle(
        style = baseStyle.copy(titleTextStyle = titleTextStyle)
    ) {
        ContentTemplateBody(
            modifier = modifier,
            templateConfig = ContentTemplateConfig(
                verticalSpacing = SPACING_LARGE.dp,
                illustrationPlacement = ContentIllustrationPlacement.BELOW_TEXT,
            ),
            title = { Text(title) },
            illustration = illustration,
            extraContent = {
                // The cells sit in the middle of the body rather than right under the title (or
                // under the illustration, where a screen has one): the design centres them, and on
                // a keyboard-first screen the middle is also where the thumb already is.
                // the bigger the weight the more centered in vertical.
                Spacer(modifier = Modifier.weight(.3f))
                CodeEntryField(
                    state = state,
                    onCodeChange = onCodeChange,
                    config = config,
                )
                Spacer(modifier = Modifier.weight(1f))
            },
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CodeEntryBodyWalletPinPreview() {
    PreviewTheme {
        ContentScreen(onBack = {}) { paddingValues ->
            CodeEntryBody(
                modifier = Modifier.padding(paddingValues),
                title = stringResource(R.string.pid_issuance_wallet_pin_setup_title),
                state = codeEntryStateForPreview(
                    capacity = CodeLength.WALLET_PIN,
                    code = "123",
                ),
                onCodeChange = {},
                config = CodeEntryConfig(focusOnCreate = false),
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CodeEntryBodyTransactionCodePreview() {
    PreviewTheme {
        ContentScreen(onBack = {}) { paddingValues ->
            CodeEntryBody(
                modifier = Modifier.padding(paddingValues),
                title = stringResource(R.string.eaa_issuance_transaction_code_entry_title),
                state = codeEntryStateForPreview(capacity = 6, code = "141131"),
                onCodeChange = {},
                config = CodeEntryConfig(
                    visibility = CodeVisibility.ALWAYS_VISIBLE,
                    focusOnCreate = false,
                ),
                titleTextStyle = CustomTypography.titleMedium,
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CodeEntryBodyWithIllustrationPreview() {
    PreviewTheme {
        ContentScreen(onBack = {}) { paddingValues ->
            CodeEntryBody(
                modifier = Modifier.padding(paddingValues),
                title = stringResource(R.string.pid_issuance_can_entry_title),
                state = codeEntryStateForPreview(
                    capacity = CodeLength.CAN,
                    code = "12",
                    supportingText = stringResource(R.string.pid_issuance_can_entry_error_wrong_can),
                ),
                onCodeChange = {},
                config = CodeEntryConfig(focusOnCreate = false),
                illustration = { WrapImage(iconData = AppIcons.EidCanShow) },
            )
        }
    }
}
