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

package org.sprind.wallet.walletpinfeature.ui.document.pinset.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle
import org.sprind.wallet.uilogic.component.WrapLottieAnimation

/**
 * The wallet code is set. One statement centred under the success animation, which is why the body
 * is centred rather than stacked from the top.
 */
@Composable
fun WalletPinSetSuccessView(
    isLoading: Boolean,
    title: String = stringResource(R.string.pid_issuance_wallet_pin_reenter_succes),
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        isLoading = isLoading
    ) { paddingValues ->
        ProvideContentTemplateStyle(
            style = ContentTemplateDefaults.style.copy(
                titleTextStyle = MaterialTheme.typography.titleLarge,
            ),
        ) {
            ContentTemplateBody(
                modifier = Modifier.padding(paddingValues),
                templateConfig = ContentTemplateConfig(
                    centerContent = true,
                ),
                title = {},
                illustration = {
                    Box {
                        WrapLottieAnimation(
                            animation = R.raw.success_inline,
                            modifier = Modifier
                                .size(SUCCESS_ANIMATION_SIZE)
                                .clearAndSetSemantics { },
                            iterations = 1,
                        )
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                                .padding(top = 175.dp),
                            text = title,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            )
        }
    }
}

private val SUCCESS_ANIMATION_SIZE = 354.dp

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun WalletPinSetSuccessPreview() {
    PreviewTheme {
        WalletPinSetSuccessView(false)
    }
}