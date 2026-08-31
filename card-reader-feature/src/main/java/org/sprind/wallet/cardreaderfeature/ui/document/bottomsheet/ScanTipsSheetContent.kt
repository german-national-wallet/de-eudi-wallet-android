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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.BulletPointText
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

/**
 * What to try when the card will not read: the tips the designs list, and the way to reach support.
 *
 * Offered by the card PIN entry and by the NFC prompts, which are the two places a scan can go wrong.
 *
 * @param onCallSupportClick places the call to customer service.
 * @param onCloseClick dismisses the sheet.
 */
@Composable
fun ScanTipsSheetContent(
    onCallSupportClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    val tips = listOf(
        stringResource(R.string.nfc_scanning_sheet_nfc_list_1),
        stringResource(R.string.nfc_scanning_sheet_nfc_list_2),
        stringResource(R.string.nfc_scanning_sheet_nfc_list_3),
    )

    ExplanationSheet(
        header = ExplanationSheetHeader(
            icon = AppIcons.Help,
            title = stringResource(R.string.nfc_scanning_sheet_nfc_title),
            markSize = SIZE_LARGE.dp,
        ),
        onCloseClick = onCloseClick,
        primaryAction = { CallSupportButton(onClick = onCallSupportClick) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    collectionInfo = CollectionInfo(rowCount = tips.size, columnCount = 1)
                },
            verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
        ) {
            tips.forEachIndexed { index, tip ->
                Column(
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = index,
                            rowSpan = 1,
                            columnIndex = 0,
                            columnSpan = 1,
                        )
                    }
                ) {
                    BulletPointText(text = tip)
                }
            }
        }
    }
}

@Composable
private fun CallSupportButton(onClick: () -> Unit) {
    WrapButton(
        modifier = Modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(
            type = ButtonType.PRIMARY,
            onClick = onClick,
        ),
    ) {
        WrapIcon(
            iconData = AppIcons.Call,
            // The label right next to it already says what the action does.
            modifier = Modifier
                .size(SIZE_MEDIUM_LARGE.dp)
                .clearAndSetSemantics { },
            customTint = ThemeColors.onPrimaryButton,
        )
        HSpacer.Small()
        WrapText(
            text = stringResource(R.string.nfc_scanning_sheet_nfc_sec_button),
            textConfig = TextConfig(
                style = ThemeTextStyles.onPrimaryButton,
                color = ThemeColors.onPrimaryButton,
                textAlign = TextAlign.Center,
                maxLines = 2,
            ),
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ScanTipsSheetContentPreview() {
    PreviewTheme {
        ScanTipsSheetContent(
            onCallSupportClick = {},
            onCloseClick = {},
        )
    }
}