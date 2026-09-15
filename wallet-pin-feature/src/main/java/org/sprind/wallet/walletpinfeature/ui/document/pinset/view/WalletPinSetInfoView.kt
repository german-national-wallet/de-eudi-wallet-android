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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import eu.europa.ec.resourceslogic.R
import org.sprind.wallet.walletpinfeature.ui.document.pinset.WalletPinStep
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapStickyPrimaryButton
import org.sprind.wallet.uilogic.component.ContentIllustrationPlacement
import org.sprind.wallet.uilogic.component.ContentNotice
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig

/**
 * Announces the wallet code before it is set: why it matters, and that it cannot be traded for a
 * fingerprint.
 */
@Composable
fun WalletPinSetInfoView(
    onCloseClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        topBar = {
            WalletCodeJourneyHeader(
                step = WalletPinStep.Info,
                onCloseClick = onCloseClick,
            )
        },
        stickyBottom = { padding ->
            WrapStickyPrimaryButton(
                text = stringResource(R.string.pid_issuance_wallet_pin_intro_prim_button),
                enabled = true,
                paddingValues = padding,
                trailingIcon = AppIcons.ArrowRightLong,
                onClick = onContinueClick,
            )
        },
    ) { paddingValues ->
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            templateConfig = ContentTemplateConfig(
                illustrationPlacement = ContentIllustrationPlacement.BELOW_TEXT,
            ),
            title = { Text(text = stringResource(R.string.pid_issuance_wallet_pin_intro_title)) },
            body = {
                Text(text = stringResource(R.string.pid_issuance_wallet_pin_intro_paragraph))
            },
            illustration = {
                // Decoration: the title and the paragraph above it already say what the code is for.
                WrapImage(
                    iconData = AppIcons.CardPin,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            },
            extraContent = {
                ContentNotice {
                    Text(text = stringResource(R.string.pid_issuance_wallet_pin_intro_banner))
                }
            },
        )
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