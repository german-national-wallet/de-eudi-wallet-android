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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton

/**
 * The Figma "blocking" layout: full-width [artwork] with a round close button, title and body on
 * the background, and the [actions] pinned to the bottom. System back runs [onBack], so the screen
 * is never a dead end.
 *
 * @param onBack what system back does; the close button's action unless that leaves the app, which
 *   a back press must not.
 * @param modifier applied to the title and body, e.g. for a test tag.
 * @param isLoading replaces the body and the actions with a loading indicator.
 * @param showClose whether the close button is drawn; system back still runs [onBack] without it.
 * @param closeModifier applied to the close button, e.g. for a test tag.
 */
@Composable
fun ArtworkBlockingScreen(
    artwork: IconData,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onClose,
    isLoading: Boolean = false,
    showClose: Boolean = true,
    closeModifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
) {
    ContentScreen(
        isLoading = isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = onBack,
        topBar = {
            ArtworkTopBar(artwork = artwork) {
                if (showClose) {
                    ArtworkCloseAction(onClick = onClose, modifier = closeModifier)
                }
            }
        },
        stickyBottom = { paddingValues ->
            Column(
                modifier = Modifier.padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
                content = actions,
            )
        },
    ) { paddingValues ->
        ContentTemplateBody(
            modifier = modifier.padding(top = SPACING_MEDIUM.dp),
            screenPadding = paddingValues,
            // Hint at text hidden behind the pinned actions.
            templateConfig = ContentTemplateConfig(showScrollFade = true),
            title = {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            body = { Text(stringResource(bodyRes)) },
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ArtworkBlockingScreenPreview() {
    PreviewTheme {
        ArtworkBlockingScreen(
            artwork = AppIcons.MissingCredential,
            titleRes = R.string.eaa_presentation_credential_not_found_title,
            bodyRes = R.string.eaa_presentation_credential_not_found_paragraph_credental_not_available,
            onClose = {},
        ) {
            WrapButton(
                buttonConfig = ButtonConfig(type = ButtonType.SECONDARY, onClick = {}),
            ) {
                Text(stringResource(R.string.global_to_overview_button))
            }
        }
    }
}
