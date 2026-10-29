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

package org.sprind.wallet.onboardingfeature.setup.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.CancellableTopAppBar
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyButtonAction
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomColumn
import eu.europa.ec.uilogic.component.wrap.WrapStickyButton
import eu.europa.ec.uilogic.extension.withoutBottom
import org.sprind.wallet.uilogic.component.ContentIllustration
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig

/**
 * The shared layout of the app setup screens: a [ContentTemplateBody] (illustration/animation,
 * title, body, extra content) with the setup actions pinned to the bottom — a primary button with a
 * trailing icon (an arrow by default) and an optional secondary button below it.
 *
 * The top bar holds a back arrow and a help button, as in the designs; both the arrow and system
 * back invoke [onBack]. The help button has no content yet, so [onHelp] defaults to doing nothing.
 *
 * Accessibility comes from [ContentTemplateBody] (title exposed as a heading, scrolling body for
 * large font scales); the buttons are shared sticky-bottom buttons, whose decorative arrow is
 * hidden from screen readers so the button is announced by its label only. Callers must decide whether their [illustration] is
 * decorative (see [ContentIllustration]).
 *
 * @param title the screen headline.
 * @param primaryButtonText the label of the primary action.
 * @param onPrimaryClick invoked by the primary action.
 * @param onBack invoked by the back arrow and system back, or `null` to ignore back and hide the
 *   arrow.
 * @param body optional paragraph under the title.
 * @param secondaryButtonText label of the optional secondary action; hidden when `null`.
 * @param onSecondaryClick invoked by the secondary action.
 * @param isPrimaryButtonEnabled whether the primary action can be used (e.g. a required checkbox).
 * @param primaryButtonTrailingIcon the trailing icon of the primary button: an arrow by default, an
 *   external-link icon when the action leaves the app, or `null` for the designs that have none
 *   (e.g. the Privacy Policy step).
 * @param isLoading shows the loading state of [ContentScreen].
 * @param showTopBar whether the top bar is shown; the success step has none.
 * @param onHelp invoked by the help button.
 * @param announceTitleChanges makes the title a polite live region, so screen readers announce it
 *   when it changes while the screen is shown (e.g. from a checking phase to its result).
 * @param templateConfig layout options of the body; see [ContentTemplateConfig].
 * @param illustration optional illustration or animation slot.
 * @param extraContent optional content under the text (checklists, notices).
 * @param bottomContent optional content pinned above the primary button (e.g. a link and the
 *   checkbox that enables the primary action).
 */
@Composable
fun SetupStepScreen(
    title: String,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    onBack: (() -> Unit)?,
    body: String? = null,
    secondaryButtonText: String? = null,
    onSecondaryClick: () -> Unit = {},
    isPrimaryButtonEnabled: Boolean = true,
    isLoading: Boolean = false,
    showTopBar: Boolean = true,
    primaryButtonTrailingIcon: IconData? = AppIcons.ArrowRightLong,
    onHelp: () -> Unit = {},
    announceTitleChanges: Boolean = false,
    templateConfig: ContentTemplateConfig = ContentTemplateConfig(),
    illustration: (@Composable () -> Unit)? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ContentScreen(
        isLoading = isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = onBack,
        topBar = if (showTopBar) {
            { CancellableTopAppBar(onBackClick = onBack, onHelpClick = onHelp) }
        } else {
            null
        },
        stickyBottom = { padding ->
            SetupStepButtons(
                padding = padding,
                primaryButtonText = primaryButtonText,
                onPrimaryClick = onPrimaryClick,
                isPrimaryButtonEnabled = isPrimaryButtonEnabled,
                primaryButtonTrailingIcon = primaryButtonTrailingIcon,
                secondaryButtonText = secondaryButtonText,
                onSecondaryClick = onSecondaryClick,
                bottomContent = bottomContent,
            )
        },
    ) { paddingValues ->
        // No bottom padding: the sticky buttons below already sit above the system bars, so it
        // would only shrink the scrolling area and cut the text short of the buttons.
        ContentTemplateBody(
            screenPadding = paddingValues.withoutBottom(),
            templateConfig = templateConfig,
            title = {
                Text(
                    text = title,
                    modifier = if (announceTitleChanges) {
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    } else {
                        Modifier
                    },
                )
            },
            body = body?.let { { Text(text = it) } },
            illustration = illustration,
            extraContent = extraContent,
        )
    }
}

@Composable
private fun SetupStepButtons(
    padding: PaddingValues,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    isPrimaryButtonEnabled: Boolean,
    primaryButtonTrailingIcon: IconData?,
    secondaryButtonText: String?,
    onSecondaryClick: () -> Unit,
    bottomContent: (@Composable ColumnScope.() -> Unit)?,
) {
    WrapStickyBottomColumn(
        paddingValues = padding,
        modifier = Modifier.padding(top = SPACING_MEDIUM.dp),
    ) {
        bottomContent?.invoke(this)
        WrapStickyButton(
            action = StickyButtonAction(
                text = primaryButtonText,
                onClick = onPrimaryClick,
                enabled = isPrimaryButtonEnabled,
                trailingIcon = primaryButtonTrailingIcon,
            ),
            type = ButtonType.PRIMARY,
        )
        secondaryButtonText?.let { text ->
            WrapStickyButton(
                action = StickyButtonAction(text = text, onClick = onSecondaryClick),
                type = ButtonType.SECONDARY,
            )
        }
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun SetupStepScreenPreview() {
    PreviewTheme {
        SetupStepScreen(
            title = "Title",
            body = "Body text explaining the setup step.",
            primaryButtonText = "Next",
            secondaryButtonText = "Later",
            onPrimaryClick = {},
            onBack = null,
            illustration = {
                ContentIllustration(contentDescription = null) { }
            },
        )
    }
}
