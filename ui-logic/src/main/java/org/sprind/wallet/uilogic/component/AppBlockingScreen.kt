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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.Banner
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

@Composable
fun AppBlockingScreen(
    title: String,
    description: String,
    buttonTitle: String,
    iconData: IconData = AppIcons.PhoneSecurity,
    onButtonClick: () -> Unit,
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        isLoading = false,
        stickyBottom = { padding ->
            WrapButton(
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = onButtonClick
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
            ) {
                TextAndIcon(
                    modifier = Modifier,
                    iconModifier = Modifier.size(SIZE_MEDIUM_LARGE.dp),
                    textModifier = Modifier.padding(end = SPACING_SMALL.dp),
                    textValue = buttonTitle,
                    textConfig = TextConfig(
                        style = ThemeTextStyles.onPrimaryButton,
                        color = ThemeColors.onPrimaryButton
                    ),
                    customTint = MaterialTheme.colorScheme.onPrimary,
                    rightIconData = AppIcons.ArrowOutward
                )
            }
        },
        contentErrorConfig = null
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = SPACING_MEDIUM.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ContentHeader(
                modifier = Modifier.fillMaxWidth(),
                config = ContentHeaderConfig(
                    title = title,
                    titleTextConfig = TextConfig(MaterialTheme.typography.titleLarge),
                ),
            )

            WrapImage(
                iconData = iconData,
            )

            Banner(
                body = description,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun AppBlockingScreenPreview() {
    PreviewTheme {
        AppBlockingScreen(
            title = stringResource(R.string.app_update_required_title),
            description = stringResource(R.string.app_update_required_description),
            buttonTitle = stringResource(R.string.app_update_required_button),
            onButtonClick = {}
        )
    }
}
