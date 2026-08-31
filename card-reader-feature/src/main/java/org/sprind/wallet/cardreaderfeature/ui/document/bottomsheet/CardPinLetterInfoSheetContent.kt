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

package org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.InfoCard
import org.sprind.wallet.uilogic.component.InfoCardText

/**
 * The explanation the card PIN question offers: what the card PIN is needed for, and where the
 * 6-digit PIN comes from — with the way to set it up when the user never has.
 *
 * Each card in the designs also carries artwork (the digital ID credential, the PIN letter). The UX
 * team is still working on those, so the sections are laid out without them for now.
 *
 * @param onSetCardPinClick starts the transport-PIN journey that sets a card PIN for the first time.
 * @param onCloseClick dismisses the sheet.
 */
@Composable
fun CardPinLetterInfoSheetContent(
    onSetCardPinClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    ExplanationSheet(
        header = ExplanationSheetHeader(
            icon = AppIcons.Help,
            title = stringResource(R.string.pid_issuance_card_pin_letter_info_title),
            markSize = SIZE_LARGE.dp,
        ),
        onCloseClick = onCloseClick,
    ) {
        InfoCard(headline = stringResource(R.string.pid_issuance_card_pin_letter_info_headline_1)) {
            InfoCardText(stringResource(R.string.pid_issuance_card_pin_letter_info_paragraph_1))
            WrapImage(
                iconData = AppIcons.NationalIdCard,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ILLUSTRATION_ASPECT_RATIO),
                contentScale = ContentScale.Fit
            )
        }
        InfoCard(headline = stringResource(R.string.pid_issuance_card_pin_letter_info_headline_2)) {
            WrapImage(
                iconData = AppIcons.PinLetterStack,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ILLUSTRATION_ASPECT_RATIO),
                contentScale = ContentScale.Fit
            )
            InfoCardText(stringResource(R.string.pid_issuance_card_pin_letter_info_paragraph_2))
            SetCardPinButton(onClick = onSetCardPinClick)
        }
    }
}

/**
 * The card's own action: it answers the question above it ("where does my PIN come from?") for the
 * user who has never set one, so it lives inside the card rather than next to the closing action.
 */
@Composable
private fun SetCardPinButton(onClick: () -> Unit) {
    WrapButton(
        modifier = Modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            onClick = onClick,
        ),
    ) {
        WrapText(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.pid_issuance_card_pin_letter_info_sec_button),
            textConfig = TextConfig(
                style = ThemeTextStyles.onSecondaryButton,
                color = ThemeColors.onSecondaryButton,
                textAlign = TextAlign.Center,
                maxLines = 2,
            ),
        )
    }
}
private const val ILLUSTRATION_ASPECT_RATIO = 1.8f

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CardPinLetterInfoSheetContentPreview() {
    PreviewTheme {
        CardPinLetterInfoSheetContent(
            onSetCardPinClick = {},
            onCloseClick = {},
        )
    }
}