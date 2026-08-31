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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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

/**
 * What a [ConfirmationDialog] asks and what its two answers do.
 *
 * @property title the question, e.g. "Really cancel?".
 * @property body what the confirming answer costs the user.
 * @property confirmText the confirming answer, styled as a destructive action.
 * @property dismissText the answer that changes nothing and closes the dialog.
 * @property onConfirm runs the destructive action.
 * @property onDismiss closes the dialog, also on a back press or a tap outside it.
 */
@Immutable
data class ConfirmationDialogConfig(
    val title: String,
    val body: String,
    val confirmText: String,
    val dismissText: String,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The design system's `overlay_dialog`: asks before an action the user cannot undo, and offers the
 * way back as the second, calmer answer.
 *
 * The answers are stacked and full width rather than sitting side by side, so a long label wraps
 * instead of squeezing its neighbour, and the destructive one is marked as such by color.
 *
 * Accessibility:
 * - the title is exposed as a heading, so a screen reader announces the question first;
 * - the mark above it is decorative and hidden, since it carries nothing the title does not say;
 * - the dialog can be dismissed with a back press, which maps to [ConfirmationDialogConfig.onDismiss]
 *   — the answer that changes nothing.
 */
@Composable
fun ConfirmationDialog(
    config: ConfirmationDialogConfig,
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
            tonalElevation = ELEVATION_8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WrapIcon(
                    iconData = AppIcons.Info,
                    modifier = Modifier.clearAndSetSemantics { },
                    customTint = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SPACING_SMALL.dp)
                        .semantics { heading() },
                    text = config.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SPACING_SMALL.dp),
                    text = config.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                DialogButton(
                    text = config.confirmText,
                    onClick = config.onConfirm,
                    isDestructive = true,
                )
                DialogButton(
                    text = config.dismissText,
                    onClick = config.onDismiss,
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    WrapButton(
        modifier = Modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            onClick = onClick,
            isWarning = isDestructive,
        ),
    ) {
        WrapText(
            modifier = Modifier.fillMaxWidth(),
            text = text,
            textConfig = TextConfig(
                style = ThemeTextStyles.onSecondaryButton,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.onErrorContainer
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

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ConfirmationDialogPreview() {
    PreviewTheme {
        ConfirmationDialog(
            config = ConfirmationDialogConfig(
                title = stringResource(R.string.pid_issuance_dialog_cancel_title),
                body = stringResource(R.string.pid_issuance_dialog_cancel_paragraph),
                confirmText = stringResource(R.string.pid_issuance_dialog_cancel_prim_button),
                dismissText = stringResource(R.string.pid_issuance_dialog_cancel_sec_button),
                onConfirm = {},
                onDismiss = {},
            )
        )
    }
}