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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.utils.ELEVATION_8
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.ScrollFadeColumn
import org.sprind.wallet.uilogic.component.wrap.StackedActionButton
import org.sprind.wallet.uilogic.component.wrap.StackedActionStyle

@Immutable
data class ConfirmationDialogLink(
    val text: String,
    val onClick: () -> Unit,
)

/**
 * What a [ConfirmationDialog] asks and what its answers do.
 *
 * @property title the question, e.g. "Really cancel?".
 * @property body what the confirming answer costs the user; omitted when the title says it all.
 * @property confirmText the confirming answer, styled as a destructive action.
 * @property dismissText the answer that changes nothing and closes the dialog.
 * @property onConfirm runs the destructive action.
 * @property onDismiss closes the dialog, also on a back press or a tap outside it.
 * @property link an optional third answer that leaves the wallet - see [ConfirmationDialogLink].
 * @property icon the decorative mark above the title.
 * @property iconTint the mark's color; the title's color when null.
 */
@Immutable
data class ConfirmationDialogConfig(
    val title: String,
    val body: String? = null,
    val confirmText: String,
    val dismissText: String,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
    val link: ConfirmationDialogLink? = null,
    val icon: IconData = AppIcons.Info,
    val iconTint: Color? = null,
)

/**
 * The design system's `overlay_dialog`: asks before an action the user cannot undo, and offers the
 * way back as the second, calmer answer.
 *
 * The answers are stacked and full width rather than sitting side by side, so a long label wraps
 * instead of squeezing its neighbour, and the destructive one is marked as such by color. A
 * [ConfirmationDialogLink] adds a third answer between them, for a way out of the wallet entirely.
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
        ConfirmationDialogContent(config)
    }
}

@Composable
internal fun ConfirmationDialogContent(config: ConfirmationDialogConfig) {
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
                    iconData = config.icon,
                    modifier = Modifier
                        .size(MARK_SIZE)
                        .clearAndSetSemantics { },
                    customTint = config.iconTint ?: MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SPACING_SMALL.dp)
                        .semantics { heading() },
                    text = config.title,
                    style = ThemeTextStyles.dialogTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                config.body?.let { body ->
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SPACING_SMALL.dp),
                        text = body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ThemeColors.onBackgroundVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SPACING_SMALL.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
            ) {
                StackedActionButton(
                    text = config.confirmText,
                    onClick = config.onConfirm,
                    style = StackedActionStyle.DESTRUCTIVE,
                )
                config.link?.let { link ->
                    StackedActionButton(
                        text = link.text,
                        onClick = link.onClick,
                        trailingIcon = AppIcons.Launch,
                    )
                }
                StackedActionButton(
                    text = config.dismissText,
                    onClick = config.onDismiss,
                )
            }
        }
    }
}

private val DIALOG_CORNER = 28.dp

private val DIALOG_PADDING = PaddingValues(
    start = SPACING_LARGE.dp,
    top = 22.5.dp,
    end = SPACING_LARGE.dp,
    bottom = SPACING_LARGE.dp,
)

private val MARK_SIZE = 25.dp

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

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ConfirmationDialogWithLinkPreview() {
    PreviewTheme {
        ConfirmationDialog(
            config = ConfirmationDialogConfig(
                title = stringResource(R.string.pid_presentation_dialog_rp_rejection_title),
                body = stringResource(R.string.pid_presentation_dialog_rp_rejection_paragraph),
                confirmText = stringResource(R.string.pid_presentation_dialog_rp_rejection_prim_button),
                dismissText = stringResource(R.string.pid_presentation_dialog_rp_rejection_sec_button_2),
                onConfirm = {},
                onDismiss = {},
                link = ConfirmationDialogLink(
                    text = stringResource(R.string.pid_presentation_dialog_rp_rejection_sec_button),
                    onClick = {},
                ),
            )
        )
    }
}

/** Large font scale so the content overflows: it scrolls and shows the bottom fade. */
@Preview(name = "Overflowing content", showBackground = true, fontScale = 2f, locale = "de")
@Composable
private fun ConfirmationDialogOverflowingPreview() {
    ConfirmationDialogWithLinkPreview()
}
