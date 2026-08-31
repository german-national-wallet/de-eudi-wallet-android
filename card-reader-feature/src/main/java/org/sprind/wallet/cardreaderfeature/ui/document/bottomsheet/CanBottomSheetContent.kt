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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapImage

@Composable
fun CanBottomSheetContent() {
    val textColor = MaterialTheme.colorScheme.onSurface
    GenericBottomSheet(
        titleContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                WrapIcon(
                    iconData = AppIcons.Info,
                    customTint = textColor
                )
                HSpacer.Medium()
                Text(
                    text = stringResource(R.string.pid_issuance_sheet_can_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = textColor
                    ),
                )
            }
        },
        bodyContent = {
            Column(
                modifier = Modifier.padding(top = SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                val cardWidth = 242
                val cardHeight = 142

                val cardImageModifier = Modifier
                    .height(cardHeight.dp)
                    .width(cardWidth.dp)

                WrapImage(
                    modifier = cardImageModifier.align(Alignment.CenterHorizontally),
                    painter = painterResource(R.drawable.id_german_national),
                    contentDescription = stringResource(R.string.content_description_id_german_national)
                )

                WrapImage(
                    modifier = cardImageModifier.align(Alignment.CenterHorizontally),
                    painter = painterResource(R.drawable.id_migrant_in_germany),
                    contentDescription = stringResource(R.string.content_description_id_migrant_in_germany)
                )

                Text(
                    text = stringResource(R.string.pid_issuance_sheet_can_entry_paragraph),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = textColor
                    )
                )
            }
        }
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CanEntryBottomSheetPreview() {
    PreviewTheme {
        CanBottomSheetContent()
    }
}