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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import org.sprind.wallet.walletpinfeature.ui.document.pinset.WalletPinStep
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.wrap.WrapStickyPrimaryButton
import org.sprind.wallet.uilogic.component.ContentIllustrationPlacement
import org.sprind.wallet.uilogic.component.ContentNotice
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.PidCredentialCard
import kotlin.math.max
import kotlin.math.roundToInt

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
                PidCredentialCardWithCode(modifier = Modifier.clearAndSetSemantics { })
            },
            extraContent = {
                ContentNotice {
                    Text(text = stringResource(R.string.pid_issuance_wallet_pin_intro_banner))
                }
            },
        )
    }
}

/**
 * The design shows the digital ID card at 70% with the code field laid over its lower edge. The card
 * is measured at its full width and scaled down as drawn, so its text keeps the card's proportions.
 * The field is placed from the scaled card's real height, so it stays on the card's edge when a
 * large font scale makes the card taller.
 */
@Composable
private fun PidCredentialCardWithCode(modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier,
        content = {
            PidCredentialCard()
            Image(
                painter = painterResource(R.drawable.pid_card_code_overlay),
                contentDescription = null,
            )
        },
    ) { measurables, _ ->
        val card = measurables[0].measure(Constraints.fixedWidth(PID_CARD_FULL_WIDTH.roundToPx()))
        val codeField = measurables[1].measure(Constraints())
        val scaledCardWidth = (card.width * PID_CARD_SCALE).roundToInt()
        val scaledCardHeight = (card.height * PID_CARD_SCALE).roundToInt()
        val codeFieldTop = scaledCardHeight - (codeField.height * CODE_FIELD_OVERLAP).roundToInt()
        val width = max(scaledCardWidth, codeField.width)

        layout(width = width, height = codeFieldTop + codeField.height) {
            card.placeWithLayer(x = (width - card.width) / 2, y = 0) {
                scaleX = PID_CARD_SCALE
                scaleY = PID_CARD_SCALE
                transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)
            }
            codeField.place(x = (width - codeField.width) / 2, y = codeFieldTop)
        }
    }
}

private val PID_CARD_FULL_WIDTH = 328.dp
private const val PID_CARD_SCALE = 0.7f

// Share of the code field's height that lies over the card in the design.
private const val CODE_FIELD_OVERLAP = 0.6f

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