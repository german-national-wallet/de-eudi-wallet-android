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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapIcon

/**
 * Help sheet explaining the rules for choosing a new card PIN.
 *
 * Shared by the new-card-PIN intro, choose and confirm steps, which each used to carry their own
 * byte-for-byte copy of it.
 *
 * Accessibility: the title row is merged and exposed as a heading, so the sheet announces as one
 * phrase a screen reader can jump to. The info icon is dropped from the accessibility tree — it
 * only repeats visually what the title already says.
 */
@Composable
fun NewPinInfoSheetContent(
    title: String,
) {
    val textColor = MaterialTheme.colorScheme.onSurface

    GenericBottomSheet(
        titleContent = {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WrapIcon(
                    iconData = AppIcons.Info,
                    modifier = Modifier.clearAndSetSemantics { },
                    customTint = textColor,
                )
                HSpacer.Medium()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(color = textColor),
                )
            }
        },
        bodyContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
            ) {
                Text(
                    text = stringResource(R.string.new_pin_set_bottom_sheet_content_one),
                    style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                )
                Text(
                    text = stringResource(R.string.new_pin_set_bottom_sheet_content_two),
                    style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                )
            }
        },
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NewPinInfoSheetContentPreview() {
    PreviewTheme {
        NewPinInfoSheetContent(title = stringResource(R.string.eid_setup_card_pin_setup_title))
    }
}
