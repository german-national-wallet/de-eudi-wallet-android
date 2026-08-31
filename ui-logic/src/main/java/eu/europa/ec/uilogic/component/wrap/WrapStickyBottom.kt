/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.uilogic.component.wrap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.divider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.WEIGHT_1

sealed interface StickyBottomType {
    data class OneButton(
        val config: ButtonConfig,
    ) : StickyBottomType

    data class TwoButtons(
        val primaryButtonConfig: ButtonConfig,
        val secondaryButtonConfig: ButtonConfig,
    ) : StickyBottomType

    /** Two buttons one above the other, the primary on top. */
    data class StackedButtons(
        val primaryButtonConfig: ButtonConfig,
        val secondaryButtonConfig: ButtonConfig,
    ) : StickyBottomType

    data object Generic : StickyBottomType
}

data class StickyBottomConfig(
    val type: StickyBottomType,
    val showDivider: Boolean = true,
)

/**
 * @param secondaryContent the label of the declining button, used by [StickyBottomType.TwoButtons];
 *   [content] is the accepting one there.
 */
@Composable
fun WrapStickyBottomContent(
    stickyBottomModifier: Modifier = Modifier,
    stickyBottomConfig: StickyBottomConfig,
    secondaryContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    when (val stickyBottomType = stickyBottomConfig.type) {
        is StickyBottomType.OneButton -> {
            Column(
                modifier = stickyBottomModifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (stickyBottomConfig.showDivider) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.divider
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WrapButton(
                        modifier = Modifier.fillMaxWidth(),
                        buttonConfig = stickyBottomType.config,
                    ) {
                        content()
                    }
                }
            }
        }

        is StickyBottomType.TwoButtons -> {
            Column(
                modifier = stickyBottomModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (stickyBottomConfig.showDivider) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.divider
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
                ) {
                    // The declining answer leads, so the accepting one sits where a thumb lands.
                    WrapButton(
                        modifier = Modifier.weight(1f),
                        buttonConfig = stickyBottomType.secondaryButtonConfig
                    ) {
                        // Callers that predate the second slot keep their old behaviour instead of
                        // silently rendering a blank button.
                        (secondaryContent ?: content)()
                    }
                    WrapButton(
                        modifier = Modifier.weight(1f),
                        buttonConfig = stickyBottomType.primaryButtonConfig
                    ) {
                        content()
                    }
                }
            }
        }

        is StickyBottomType.StackedButtons -> {
            Column(
                modifier = stickyBottomModifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (stickyBottomConfig.showDivider) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.divider
                    )
                }

                WrapButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonConfig = stickyBottomType.primaryButtonConfig
                ) {
                    content()
                }
                WrapButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonConfig = stickyBottomType.secondaryButtonConfig
                ) {
                    (secondaryContent ?: content)()
                }
            }
        }

        is StickyBottomType.Generic -> {
            content()
        }
    }
}

/**
 * The insets every sticky bottom shares
 * Apply it to anything dropped into a [eu.europa.ec.uilogic.component.content.ContentScreen]
 * `stickyBottom` slot that is not one of the `WrapSticky*` buttons — a [WrapStickyBottomColumn] of
 * custom content, or a section such as `ButtonNavigationBottom`
 *
 * @param paddingValues the `stickyBottom` slot padding; only its bottom inset is used, the
 *   horizontal margin is the shared one.
 */
fun Modifier.stickyBottomInsets(paddingValues: PaddingValues): Modifier = this
    .fillMaxWidth()
    .padding(bottom = paddingValues.calculateBottomPadding())
    .padding(horizontal = SPACING_MEDIUM.dp)

/**
 * One action inside a sticky bottom: what it says, whether it can be pressed, and the icons that
 * decorate it.
 *
 * Both icons are decorative — the label already says what the action does — so they are kept out of
 * the accessibility tree.
 *
 * @property text the label.
 * @property onClick called when the action is pressed.
 * @property enabled whether the action is available yet.
 * @property leadingIcon shown before the label, e.g. a help or info mark.
 * @property trailingIcon shown after the label, e.g. the "continue" arrow the stepped screens carry.
 */
@Immutable
data class StickyButtonAction(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val leadingIcon: IconData? = null,
    val trailingIcon: IconData? = null,
)

/**
 * A full-width sticky-bottom button rendering [action] as a button of [type].
 *
 * This is the shared building block behind every `WrapSticky*` bottom bar: it owns the label metrics
 * (centered, wrapping over at most two lines, decorative icons at a fixed size) but carries no
 * insets of its own, so it is meant to sit inside [WrapStickyBottomColumn] rather than in a
 * `stickyBottom` slot directly. Use it for bottom bars the named wrappers do not cover, e.g. a text
 * action stacked above a primary button.
 *
 * @param action the label, click handler and icons; see [StickyButtonAction].
 * @param type the button style.
 * @param modifier applied to the button, e.g. a [androidx.compose.foundation.layout.RowScope.weight]
 *   when several sit side by side.
 */
@Composable
fun WrapStickyButton(
    action: StickyButtonAction,
    type: ButtonType,
    modifier: Modifier = Modifier,
) {
    WrapButton(
        modifier = modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(
            type = type,
            enabled = action.enabled,
            onClick = action.onClick,
        ),
    ) {
        StickyButtonLabel(
            action = action,
            // A text button defaults to the accent color, which is not how this app draws the
            // action under a bottom bar; it reads as body text there, like the filled buttons do.
            contentColor = if (type == ButtonType.TEXT) ThemeColors.onSecondaryButton else null,
        )
    }
}

/**
 * The container every sticky bottom is built from: the shared [stickyBottomInsets] plus the standard
 * gap between stacked actions.
 *
 * The named wrappers below ([WrapStickyPrimaryButton] and friends) cover the recurring bottom bars;
 * reach for this one directly when a screen needs a combination they do not have, and fill it with
 * [WrapStickyButton]s so the result still matches them.
 *
 * @param paddingValues the `stickyBottom` slot padding; see [stickyBottomInsets].
 * @param modifier applied to the container.
 * @param content the actions, laid out top to bottom.
 */
@Composable
fun WrapStickyBottomColumn(
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.stickyBottomInsets(paddingValues),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * A sticky bottom holding one primary button — the wallet's most common bottom bar.
 *
 * @param text the button label.
 * @param enabled whether the action is available.
 * @param paddingValues the [eu.europa.ec.uilogic.component.content.ContentScreen] sticky-bottom
 *   padding; only its bottom inset is used.
 * @param onClick called when the button is pressed.
 * @param modifier applied to the sticky bottom container.
 * @param trailingIcon shown after the label, e.g. the arrow the stepped screens carry. Label and
 *   icon are centered as a pair.
 */
@Composable
fun WrapStickyPrimaryButton(
    text: String,
    enabled: Boolean,
    paddingValues: PaddingValues,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: IconData? = null,
) {
    WrapStickyBottomColumn(paddingValues = paddingValues, modifier = modifier) {
        WrapStickyButton(
            action = StickyButtonAction(
                text = text,
                onClick = onClick,
                enabled = enabled,
                trailingIcon = trailingIcon,
            ),
            type = ButtonType.PRIMARY,
        )
    }
}

/**
 * A sticky bottom holding a single outlined button, for screens whose only action is a secondary
 * one — an explanation the user may open, rather than a step that carries the flow on.
 *
 * @param text the button label.
 * @param paddingValues the [eu.europa.ec.uilogic.component.content.ContentScreen] sticky-bottom
 *   padding; only its bottom inset is used.
 * @param onClick called when the button is pressed.
 * @param modifier applied to the sticky bottom container.
 * @param leadingIcon shown before the label, e.g. an info mark.
 */
@Composable
fun WrapStickySecondaryButton(
    text: String,
    paddingValues: PaddingValues,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: IconData? = null,
) {
    WrapStickyBottomColumn(paddingValues = paddingValues, modifier = modifier) {
        WrapStickyButton(
            action = StickyButtonAction(
                text = text,
                onClick = onClick,
                leadingIcon = leadingIcon,
            ),
            type = ButtonType.SECONDARY,
        )
    }
}

/**
 * A sticky bottom holding a single borderless action, for the side notes of a flow — a privacy
 * policy, a "what is this?" — that should not compete with a step button.
 *
 * @param text the button label.
 * @param paddingValues the [eu.europa.ec.uilogic.component.content.ContentScreen] sticky-bottom
 *   padding; only its bottom inset is used.
 * @param onClick called when the button is pressed.
 * @param modifier applied to the sticky bottom container.
 * @param leadingIcon shown before the label, e.g. an info mark.
 */
@Composable
fun WrapStickyTextButton(
    text: String,
    paddingValues: PaddingValues,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: IconData? = null,
) {
    WrapStickyBottomColumn(paddingValues = paddingValues, modifier = modifier) {
        WrapStickyButton(
            action = StickyButtonAction(
                text = text,
                onClick = onClick,
                leadingIcon = leadingIcon,
            ),
            type = ButtonType.TEXT,
        )
    }
}

/**
 * A sticky bottom holding two equally wide answers, the accepting one on the trailing side.
 *
 * @param secondaryText the declining label.
 * @param primaryText the accepting label.
 * @param paddingValues the [eu.europa.ec.uilogic.component.content.ContentScreen] sticky-bottom
 *   padding; only its bottom inset is used.
 * @param onSecondaryClick called when the declining answer is pressed.
 * @param onPrimaryClick called when the accepting answer is pressed.
 * @param modifier applied to the sticky bottom container.
 */
@Composable
fun WrapStickyTwoButtons(
    secondaryText: String,
    primaryText: String,
    paddingValues: PaddingValues,
    onSecondaryClick: () -> Unit,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WrapStickyBottomColumn(paddingValues = paddingValues, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        ) {
            // The declining answer leads, so the accepting one sits where a thumb lands.
            WrapStickyButton(
                action = StickyButtonAction(text = secondaryText, onClick = onSecondaryClick),
                type = ButtonType.SECONDARY,
                modifier = Modifier.weight(WEIGHT_1),
            )
            WrapStickyButton(
                action = StickyButtonAction(text = primaryText, onClick = onPrimaryClick),
                type = ButtonType.PRIMARY,
                modifier = Modifier.weight(WEIGHT_1),
            )
        }
    }
}

/**
 * A sticky bottom holding a primary action with a secondary one under it.
 *
 * @param primaryText the label of the action that carries the flow on.
 * @param secondaryText the label of the action beneath it.
 * @param paddingValues the [eu.europa.ec.uilogic.component.content.ContentScreen] sticky-bottom
 *   padding; only its bottom inset is used.
 * @param onPrimaryClick called when the primary action is pressed.
 * @param onSecondaryClick called when the secondary action is pressed.
 * @param modifier applied to the sticky bottom container.
 * @param primaryEnabled whether the primary action is available yet.
 * @param primaryTrailingIcon shown after the primary label, e.g. the "continue" arrow.
 * @param secondaryLeadingIcon shown before the secondary label, e.g. a help mark. Both icons are
 *   decorative: the labels next to them already say what the actions do.
 */
@Composable
fun WrapStickyStackedButtons(
    primaryText: String,
    secondaryText: String,
    paddingValues: PaddingValues,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    primaryTrailingIcon: IconData? = null,
    secondaryLeadingIcon: IconData? = null,
) {
    WrapStickyBottomColumn(paddingValues = paddingValues, modifier = modifier) {
        WrapStickyButton(
            action = StickyButtonAction(
                text = primaryText,
                onClick = onPrimaryClick,
                enabled = primaryEnabled,
                trailingIcon = primaryTrailingIcon,
            ),
            type = ButtonType.PRIMARY,
        )
        WrapStickyButton(
            action = StickyButtonAction(
                text = secondaryText,
                onClick = onSecondaryClick,
                leadingIcon = secondaryLeadingIcon,
            ),
            type = ButtonType.SECONDARY,
        )
    }
}

private const val MAX_LABEL_LINES = 2

/**
 * The label of a sticky-bottom button: the text centered between its decorative icons.
 *
 * @param contentColor overrides the color the button provides; `null` inherits it.
 */
@Composable
private fun StickyButtonLabel(
    action: StickyButtonAction,
    contentColor: Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = SPACING_SMALL.dp,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        action.leadingIcon?.let { DecorativeIcon(it, action.enabled, contentColor) }
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = action.text,
            color = contentColor ?: Color.Unspecified,
            textAlign = TextAlign.Center,
            maxLines = MAX_LABEL_LINES,
        )
        action.trailingIcon?.let { DecorativeIcon(it, action.enabled, contentColor) }
    }
}

/** The label next to it already says what the action does, so the icon stays out of the tree. */
@Composable
private fun DecorativeIcon(
    icon: IconData,
    enabled: Boolean,
    customTint: Color?,
) {
    WrapIcon(
        iconData = icon,
        modifier = Modifier
            .size(SIZE_MEDIUM_LARGE.dp)
            .clearAndSetSemantics { },
        customTint = customTint,
        enabled = enabled,
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapStickyPrimaryButtonPreview() {
    PreviewTheme {
        WrapStickyPrimaryButton(
            text = "Weiter",
            enabled = true,
            paddingValues = PaddingValues(),
            trailingIcon = AppIcons.ArrowRightLong,
            onClick = {},
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapStickyStackedButtonsPreview() {
    PreviewTheme {
        WrapStickyStackedButtons(
            primaryText = "Weiter",
            secondaryText = "Warum brauche ich das?",
            paddingValues = PaddingValues(),
            primaryTrailingIcon = AppIcons.ArrowRightLong,
            secondaryLeadingIcon = AppIcons.Help,
            onPrimaryClick = {},
            onSecondaryClick = {},
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapStickyTwoButtonsPreview() {
    PreviewTheme {
        WrapStickyTwoButtons(
            secondaryText = "Abbrechen",
            primaryText = "Einverstanden",
            paddingValues = PaddingValues(),
            onSecondaryClick = {},
            onPrimaryClick = {},
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapStickySingleActionsPreview() {
    PreviewTheme {
        Column {
            WrapStickySecondaryButton(
                text = "Ich kenne meine Karten-PIN nicht",
                paddingValues = PaddingValues(),
                leadingIcon = AppIcons.Info,
                onClick = {},
            )
            WrapStickyTextButton(
                text = "Datenschutzerklärung",
                paddingValues = PaddingValues(),
                leadingIcon = AppIcons.Info,
                onClick = {},
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapStickyBottomColumnPreview() {
    PreviewTheme {
        WrapStickyBottomColumn(paddingValues = PaddingValues()) {
            WrapStickyButton(
                action = StickyButtonAction(
                    text = "Datenschutzerklärung",
                    onClick = {},
                    leadingIcon = AppIcons.Info,
                ),
                type = ButtonType.TEXT,
            )
            WrapStickyButton(
                action = StickyButtonAction(
                    text = "Weiter",
                    onClick = {},
                    trailingIcon = AppIcons.ArrowRightLong,
                ),
                type = ButtonType.PRIMARY,
            )
        }
    }
}