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

package org.sprind.wallet.revocationfeature.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.navigation.RevocationScreens
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.ContentChecklist
import org.sprind.wallet.uilogic.component.ContentChecklistItem
import org.sprind.wallet.uilogic.component.ContentIllustration
import org.sprind.wallet.uilogic.component.ContentIllustrationPlacement
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig


@Composable
fun RevocationIntroScreen(
    navController: NavController,
) {
    Content(
        onNext = { navController.navigate(RevocationScreens.SaveCode.screenRoute) },
        onBack = { navController.popBackStack() }
    )
}

@Composable
private fun Content(
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    ContentScreen(
        onBack = onBack,
        stickyBottom = { padding ->
            WrapButton(
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = onNext
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
            ) {
                TextAndIcon(
                    modifier = Modifier,
                    iconModifier = Modifier.size(SIZE_MEDIUM_LARGE.dp),
                    textModifier = Modifier.padding(end = SPACING_SMALL.dp),
                    textValue = stringResource(R.string.app_onboarding_wallet_revocation_intro_prim_button),
                    textConfig = TextConfig(
                        style = ThemeTextStyles.onPrimaryButton,
                        color = ThemeColors.onPrimaryButton,
                    ),
                    customTint = MaterialTheme.colorScheme.onPrimary,
                    rightIconData = AppIcons.ArrowForward,
                )
            }
        },
    ) { paddingValues ->
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            templateConfig = ContentTemplateConfig(
                illustrationPlacement = ContentIllustrationPlacement.ABOVE_TITLE,
            ),
            title = {
                Text(
                    text = stringResource(R.string.app_onboarding_wallet_revocation_intro_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            illustration = {
                ContentIllustration(contentDescription = null) {
                    WrapImage(
                        iconData = AppIcons.LogoPlain,
                        modifier = Modifier.size(184.dp), // Todo: extract into constant
                        contentScale = ContentScale.Fit,
                    )
                }
            },
            extraContent = {
                ContentChecklist(
                    items = listOf(
                        ContentChecklistItem(
                            text = stringResource(R.string.app_onboarding_wallet_revocation_intro_list_1),
                            icon = AppIcons.Key,
                        ),
                        ContentChecklistItem(
                            text = stringResource(R.string.app_onboarding_wallet_revocation_intro_list_2),
                            icon = AppIcons.Lock,
                        ),
                    ),
                )
            },
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun RevocationIntroPreview() {
    PreviewTheme {
        Content(
            onNext = { },
            onBack = { }
        )
    }
}
