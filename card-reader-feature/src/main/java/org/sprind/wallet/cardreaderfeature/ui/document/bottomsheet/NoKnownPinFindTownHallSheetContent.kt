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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

@Composable
fun NoKnownPinFindTownHallBottomSheetContent(
    onSecondaryButtonClick: () -> Unit,
) {
    GenericBottomSheet(
        titleContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                WrapIcon(
                    iconData = AppIcons.Info,
                    customTint = MaterialTheme.colorScheme.primary
                )

                WrapText(
                    text = stringResource(R.string.pid_issuance_no_letter_forgot_info_title),
                    textConfig = TextConfig(
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                )
            }
        },
        bodyContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp)
            ) {
                WrapText(
                    text = stringResource(R.string.pid_issuance_no_letter_forgot_info_paragraph),
                    textConfig = TextConfig(style = MaterialTheme.typography.bodyLarge).copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = Int.MAX_VALUE
                    )
                )
                WrapButton(
                    buttonConfig = ButtonConfig(
                        type = ButtonType.SECONDARY,
                        onClick = onSecondaryButtonClick
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    TextAndIcon(
                        modifier = Modifier,
                        textValue = stringResource(R.string.pid_issuance_sheet_eid_pin_not_set_sec_button),
                        textConfig = TextConfig(
                            style = ThemeTextStyles.onSecondaryButton,
                            color = ThemeColors.onSecondaryButton
                        ),
                        customTint = ThemeColors.onSecondaryButton,
                        rightIconData = AppIcons.ArrowOutward
                    )
                }
            }
        }
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NoKnownPinFindTownHallBottomSheetPreview() {
    PreviewTheme {
        NoKnownPinFindTownHallBottomSheetContent() {}
    }
}