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

package org.sprind.wallet.cardreaderfeature.ui.document.nfc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.clearAndSetSemantics
import eu.europa.ec.uilogic.component.wrap.Banner
import org.sprind.wallet.uilogic.component.ContentIllustrationPlacement
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle
import org.sprind.wallet.uilogic.component.WrapLottieAnimation
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.ScanTipsSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.read.ReadCardBottomSheetConfig
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.BulletPointText
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

enum class NfcScanReason {
    EID_PIN,
    TRANSPORT_PIN,
    PUK,
    CAN,
    EID_NEW_PIN_SET
}

@Composable
fun NfcScanPromptView(
    modifier: Modifier,
    nfcScanReason: org.sprind.wallet.cardreaderfeature.ui.document.nfc.NfcScanReason,
    bottomCardSheetConfig: ReadCardBottomSheetConfig,
    onCustomerServiceCallButtonClick: () -> Unit,
) {
    val title = when (nfcScanReason) {
        NfcScanReason.EID_PIN -> stringResource(R.string.nfc_scanning_nfc_tap_title_card_pin)
        NfcScanReason.TRANSPORT_PIN -> stringResource(R.string.nfc_scanning_nfc_tap_title_transport_pin)
        NfcScanReason.PUK -> stringResource(R.string.nfc_scanning_nfc_tap_title_puk)
        NfcScanReason.CAN -> stringResource(R.string.nfc_scanning_nfc_tap_title_can)
        NfcScanReason.EID_NEW_PIN_SET -> stringResource(R.string.nfc_scanning_nfc_tap_title_card_pin)
    }

    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleLarge,
        ),
    ) {
        ContentTemplateBody(
            modifier = modifier,
            templateConfig = ContentTemplateConfig(
                verticalSpacing = SPACING_LARGE_32.dp,
                illustrationPlacement = ContentIllustrationPlacement.BELOW_TEXT,
            ),
            title = { Text(text = title) },
            illustration = {
                WrapLottieAnimation(
                    animation = R.raw.nfc_tapping_middle,
                    modifier = Modifier
                        .size(NFC_ANIMATION_SIZE)
                        // The title and the banner say what the animation shows.
                        .clearAndSetSemantics { },
                )
            },
            extraContent = {
                Banner(
                    modifier = Modifier.fillMaxWidth(),
                    body = stringResource(R.string.nfc_scanning_nfc_tap_banner_android),
                    icon = AppIcons.Warning,
                )
            },
        )
    }

    if (bottomCardSheetConfig.isBottomSheetOpen) {
        WrapModalBottomSheet(
            onDismissRequest = { bottomCardSheetConfig.onBottomSheetDismissRequest() },
            sheetState = bottomCardSheetConfig.sheetState
        ) {
            ScanTipsSheetContent(
                onCallSupportClick = onCustomerServiceCallButtonClick,
                onCloseClick = { bottomCardSheetConfig.onBottomSheetDismissRequest() },
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NfcInfoCardPinPreview() {
    PreviewTheme {
        val sheet = rememberModalBottomSheetState(
            skipPartiallyExpanded = false
        )

        NfcScanPromptView(
            modifier = Modifier.fillMaxSize(),
            nfcScanReason = NfcScanReason.EID_PIN,
            bottomCardSheetConfig = ReadCardBottomSheetConfig(
                title = "",
                sheetState = sheet,
                isBottomSheetOpen = false,
                onBottomSheetDismissRequest = {}
            ),
            onCustomerServiceCallButtonClick = {}
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NfcInfoTransportPinPreview() {
    PreviewTheme {
        val sheet = rememberModalBottomSheetState(
            skipPartiallyExpanded = false
        )

        NfcScanPromptView(
            modifier = Modifier.fillMaxSize(),
            nfcScanReason = NfcScanReason.TRANSPORT_PIN,
            bottomCardSheetConfig = ReadCardBottomSheetConfig(
                title = "",
                sheetState = sheet,
                isBottomSheetOpen = false,
                onBottomSheetDismissRequest = {}
            ),
            onCustomerServiceCallButtonClick = {}
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NfcInfoNewPinSetPreview() {
    PreviewTheme {
        val sheet = rememberModalBottomSheetState(
            skipPartiallyExpanded = false
        )

        NfcScanPromptView(
            modifier = Modifier.fillMaxSize(),
            nfcScanReason = NfcScanReason.EID_NEW_PIN_SET,
            bottomCardSheetConfig = ReadCardBottomSheetConfig(
                title = "",
                sheetState = sheet,
                isBottomSheetOpen = false,
                onBottomSheetDismissRequest = {}
            ),
            onCustomerServiceCallButtonClick = {}
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NfcInfoCanPreview() {
    PreviewTheme {
        val sheet = rememberModalBottomSheetState(
            skipPartiallyExpanded = false
        )

        NfcScanPromptView(
            modifier = Modifier.fillMaxSize(),
            nfcScanReason = NfcScanReason.CAN,
            bottomCardSheetConfig = ReadCardBottomSheetConfig(
                title = "",
                sheetState = sheet,
                isBottomSheetOpen = false,
                onBottomSheetDismissRequest = {}
            ),
            onCustomerServiceCallButtonClick = {}
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NfcInfoPukPreview() {
    PreviewTheme {
        val sheet = rememberModalBottomSheetState(
            skipPartiallyExpanded = false
        )

        NfcScanPromptView(
            modifier = Modifier.fillMaxSize(),
            nfcScanReason = NfcScanReason.PUK,
            bottomCardSheetConfig = ReadCardBottomSheetConfig(
                title = "",
                sheetState = sheet,
                isBottomSheetOpen = false,
                onBottomSheetDismissRequest = {}
            ),
            onCustomerServiceCallButtonClick = {}
        )
    }
}

/** The size the designs export the animation at. */
private val NFC_ANIMATION_SIZE = 204.dp