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

@file:OptIn(ExperimentalMaterial3Api::class)

package org.sprind.wallet.uilogic.component.dialog

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.ELEVATION_8
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.ScrollFadeColumn

@Immutable
data class RedirectInfoDialogConfig(
    val target: Uri,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

/** Names where a link leads before the user leaves the app for it. */
@Composable
fun RedirectInfoDialog(
    config: RedirectInfoDialogConfig,
) {
    BasicAlertDialog(
        onDismissRequest = config.onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(DIALOG_CORNER),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = ELEVATION_8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DIALOG_PADDING),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Long content scrolls above the pinned buttons, with a fade hinting at the rest.
                ScrollFadeColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WrapIcon(
                        iconData = AppIcons.Info,
                        modifier = Modifier
                            .size(MARK_SIZE)
                            .clearAndSetSemantics { },
                        customTint = MaterialTheme.colorScheme.onSurface,
                    )

                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SPACING_SMALL.dp)
                            .semantics { heading() },
                        text = stringResource(R.string.global_redirect_info_title),
                        style = ThemeTextStyles.dialogTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SPACING_SMALL.dp),
                        text = targetLine(config.target),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ThemeColors.onBackgroundVariant,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SPACING_SMALL.dp),
                        text = stringResource(R.string.global_redirect_info_paragraph_2),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ThemeColors.onBackgroundVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
                ) {
                    DialogButton(
                        text = stringResource(R.string.global_redirect_info_prim_button),
                        onClick = config.onConfirm,
                        type = ButtonType.PRIMARY,
                    )
                    DialogButton(
                        text = stringResource(R.string.global_redirect_info_sec_button),
                        onClick = config.onDismiss,
                        type = ButtonType.SECONDARY,
                    )
                }
            }
        }
    }
}

/** The string carries a literal `PROVIDER.DOMAIN` token rather than a format argument. */
@Composable
private fun targetLine(target: Uri): String {
    val destination = target.destinationLabel()
    val template = stringResource(R.string.global_redirect_info_paragraph_1)
    return when {
        template.contains(TARGET_PLACEHOLDER) ->
            template.replace(TARGET_PLACEHOLDER, destination)

        else -> destination
    }
}

/** The host, or the address of a `mailto:`, which has none. */
private fun Uri.destinationLabel(): String =
    host?.takeIf { it.isNotBlank() }
        ?: schemeSpecificPart?.trimStart('/')?.takeIf { it.isNotBlank() }
        ?: toString()

private const val TARGET_PLACEHOLDER = "PROVIDER.DOMAIN"

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    type: ButtonType,
) {
    WrapButton(
        modifier = Modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(type = type, onClick = onClick),
    ) {
        WrapText(
            modifier = Modifier.fillMaxWidth(),
            text = text,
            textConfig = TextConfig(
                style = if (type == ButtonType.PRIMARY) {
                    ThemeTextStyles.onPrimaryButton
                } else {
                    ThemeTextStyles.onSecondaryButton
                },
                color = if (type == ButtonType.PRIMARY) {
                    ThemeColors.onPrimaryButton
                } else {
                    ThemeColors.onSecondaryButton
                },
                textAlign = TextAlign.Center,
                maxLines = 2,
            ),
        )
    }
}

private val DIALOG_CORNER = 28.dp

private val DIALOG_PADDING = PaddingValues(
    start = SPACING_MEDIUM.dp,
    top = 22.5.dp,
    end = SPACING_MEDIUM.dp,
    bottom = SPACING_MEDIUM.dp,
)

private val MARK_SIZE = 25.dp

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun RedirectInfoDialogPreview() {
    PreviewTheme {
        RedirectInfoDialog(
            config = RedirectInfoDialogConfig(
                target = "https://example.com/complaint".toUri(),
                onConfirm = {},
                onDismiss = {},
            )
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun RedirectInfoDialogMailtoPreview() {
    PreviewTheme {
        RedirectInfoDialog(
            config = RedirectInfoDialogConfig(
                target = "mailto:datenschutz@example.com".toUri(),
                onConfirm = {},
                onDismiss = {},
            )
        )
    }
}

/** Large font scale so the content overflows: it scrolls and shows the bottom fade. */
@Preview(name = "Overflowing content", showBackground = true, fontScale = 2f, locale = "de")
@Composable
private fun RedirectInfoDialogOverflowingPreview() {
    RedirectInfoDialogPreview()
}
