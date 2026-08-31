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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.cardreaderfeature.domain.CardScanStatus
import org.sprind.wallet.cardreaderfeature.ui.document.nfc.NfcScanReason
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles


@Composable
fun CardScanStatusSheetContent(
    status: CardScanStatus,
    scanReason: NfcScanReason,
    readingProgress: Int?,
    onCancelClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = SPACING_LARGE.dp)
            .padding(top = SPACING_LARGE.dp, bottom = SPACING_LARGE.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
    ) {
        ScanStatusMark(status = status, readingProgress = readingProgress)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_SMALL.dp),
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                text = scanStatusTitle(status),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = scanStatusParagraph(status = status, scanReason = scanReason),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (status == CardScanStatus.SUCCESS) {
            ScanStatusAction(
                text = stringResource(R.string.nfc_scanning_system_sheet_nfc_prim_button),
                type = ButtonType.PRIMARY,
                trailingIcon = AppIcons.ArrowRightLong,
                onClick = onContinueClick,
            )
        } else {
            ScanStatusAction(
                text = stringResource(R.string.global_cancel_button),
                type = ButtonType.SECONDARY,
                onClick = onCancelClick,
            )
        }
    }
}

@Composable
private fun ScanStatusMark(
    status: CardScanStatus,
    readingProgress: Int?,
) {
    if (status == CardScanStatus.IN_PROGRESS) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                progress = { (readingProgress ?: 0) / PERCENT_FULL },
                modifier = Modifier
                    .size(SCAN_MARK_SIZE)
                    // The percentage right next to it is what says how far the read is.
                    .clearAndSetSemantics { },
            )
            Text(
                text = stringResource(
                    R.string.nfc_scanning_progress_percentage,
                    readingProgress ?: 0,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }

    WrapImage(
        iconData = status.mark,
        modifier = Modifier
            .size(SCAN_MARK_SIZE)
            // Decorative: the title right below says what the mark stands for.
            .clearAndSetSemantics { },
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun ScanStatusAction(
    text: String,
    type: ButtonType,
    onClick: () -> Unit,
    trailingIcon: IconData? = null,
) {
    WrapButton(
        modifier = Modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(
            type = type,
            onClick = onClick,
        ),
    ) {
        WrapText(
            text = text,
            textConfig = TextConfig(
                style = if (type == ButtonType.PRIMARY) {
                    ThemeTextStyles.onPrimaryButton
                } else {
                    ThemeTextStyles.onSecondaryButton
                },
                color = if (type == ButtonType.PRIMARY) {
                    ThemeColors.onPrimaryButton
                } else {
                    ThemeColors.onSecondaryButton
                },
                textAlign = TextAlign.Center,
                maxLines = 2,
            ),
        )
        trailingIcon?.let { icon ->
            WrapIcon(
                iconData = icon,
                modifier = Modifier
                    .padding(start = SPACING_EXTRA_SMALL.dp)
                    .size(SIZE_MEDIUM_LARGE.dp)
                    // The label already names the action the arrow points at.
                    .clearAndSetSemantics { },
                customTint = ThemeColors.onPrimaryButton,
            )
        }
    }
}


private val CardScanStatus.mark: IconData
    get() = when (this) {
        CardScanStatus.READY,
        CardScanStatus.IN_PROGRESS,
        -> AppIcons.Contactless

        CardScanStatus.SUCCESS -> AppIcons.SuccessCheckmark
        CardScanStatus.FAILED -> AppIcons.ScanError
    }

@Composable
private fun scanStatusTitle(status: CardScanStatus): String = stringResource(
    when (status) {
        CardScanStatus.READY -> R.string.nfc_scanning_system_sheet_nfc_title_start
        CardScanStatus.IN_PROGRESS -> R.string.nfc_scanning_system_sheet_nfc_title_progress
        CardScanStatus.SUCCESS -> R.string.nfc_scanning_system_sheet_nfc_title_success
        CardScanStatus.FAILED -> R.string.nfc_scanning_system_sheet_nfc_title_error
    }
)


@Composable
private fun scanStatusParagraph(
    status: CardScanStatus,
    scanReason: NfcScanReason,
): String = stringResource(
    when (status) {
        CardScanStatus.READY -> R.string.nfc_scanning_system_sheet_nfc_paragraph_start
        CardScanStatus.IN_PROGRESS -> R.string.nfc_scanning_system_sheet_nfc_paragraph_progress
        CardScanStatus.SUCCESS -> scanReason.successParagraph
        CardScanStatus.FAILED -> scanReason.errorParagraph
    }
)

private val NfcScanReason.successParagraph: Int
    get() = when (this) {
        NfcScanReason.EID_PIN -> R.string.nfc_scanning_system_sheet_nfc_paragraph_success_card_pin
        NfcScanReason.TRANSPORT_PIN -> R.string.nfc_scanning_system_sheet_nfc_paragraph_success_one_time_pin
        NfcScanReason.PUK -> R.string.nfc_scanning_system_sheet_nfc_paragraph_success_puk
        NfcScanReason.CAN -> R.string.nfc_scanning_system_sheet_nfc_paragraph_success_can
        NfcScanReason.EID_NEW_PIN_SET -> R.string.nfc_scanning_system_sheet_nfc_paragraph_success_card_pin_set
    }

private val NfcScanReason.errorParagraph: Int
    get() = when (this) {
        NfcScanReason.EID_PIN -> R.string.nfc_scanning_system_sheet_nfc_paragraph_error_card_pin
        NfcScanReason.TRANSPORT_PIN -> R.string.nfc_scanning_system_sheet_nfc_paragraph_error_one_time_pin
        NfcScanReason.PUK -> R.string.nfc_scanning_system_sheet_nfc_paragraph_error_puk
        NfcScanReason.CAN -> R.string.nfc_scanning_system_sheet_nfc_paragraph_error_can
        // Setting a new PIN can fail for reasons that have nothing to do with a code, so the
        // generic explanation is the honest one here.
        NfcScanReason.EID_NEW_PIN_SET -> R.string.nfc_scanning_system_sheet_nfc_error_paragraph
    }

private val SCAN_MARK_SIZE = 48.dp
private const val PERCENT_FULL = 100f

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CardScanStatusReadyPreview() {
    PreviewTheme {
        CardScanStatusSheetContent(
            status = CardScanStatus.READY,
            scanReason = NfcScanReason.EID_PIN,
            readingProgress = null,
            onCancelClick = {},
            onContinueClick = {},
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CardScanStatusInProgressPreview() {
    PreviewTheme {
        CardScanStatusSheetContent(
            status = CardScanStatus.IN_PROGRESS,
            scanReason = NfcScanReason.EID_PIN,
            readingProgress = 42,
            onCancelClick = {},
            onContinueClick = {},
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CardScanStatusSuccessPreview() {
    PreviewTheme {
        CardScanStatusSheetContent(
            status = CardScanStatus.SUCCESS,
            scanReason = NfcScanReason.EID_PIN,
            readingProgress = 100,
            onCancelClick = {},
            onContinueClick = {},
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CardScanStatusFailedPreview() {
    PreviewTheme {
        CardScanStatusSheetContent(
            status = CardScanStatus.FAILED,
            scanReason = NfcScanReason.TRANSPORT_PIN,
            readingProgress = null,
            onCancelClick = {},
            onContinueClick = {},
        )
    }
}