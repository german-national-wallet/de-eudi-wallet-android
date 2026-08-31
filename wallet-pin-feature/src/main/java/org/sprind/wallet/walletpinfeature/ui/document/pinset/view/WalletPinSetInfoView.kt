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

package org.sprind.wallet.walletpinfeature.ui.document.pinset.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.Banner
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage


@Composable
fun WalletPinSetInfoView(
    onCloseClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        toolBarConfig = ToolbarConfig(
            "",
            listOf(
                ToolbarAction(
                    icon = AppIcons.Close,
                    onClick = {
                        onCloseClick()
                    })
            )
        ),
        stickyBottom = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        PaddingValues(
                            bottom = padding.calculateBottomPadding(),
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {

                WrapButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SPACING_MEDIUM.dp),
                    buttonConfig = ButtonConfig(
                        type = ButtonType.PRIMARY,
                        onClick = {
                            onContinueClick()
                        }
                    )
                ) {
                    Text(
                        text = stringResource(id = R.string.pid_issuance_wallet_pin_intro_prim_button)
                    )
                }
            }
        }) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContentHeader(
                modifier = Modifier.fillMaxWidth(),
                config = ContentHeaderConfig(
                    title = stringResource(R.string.pid_issuance_wallet_pin_intro_title),
                    titleTextConfig = TextConfig(MaterialTheme.typography.titleLarge),
                ),
            )

            WrapImage(
                modifier = Modifier,
                iconData = AppIcons.PhoneSecurity,
            )

            Banner(body = stringResource(R.string.pid_issuance_wallet_pin_intro_paragraph))
        }
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun WalletPinSetInfoPreview() {
    PreviewTheme {
        WalletPinSetInfoView(
            onCloseClick = {},
            onContinueClick = {}
        )
    }
}