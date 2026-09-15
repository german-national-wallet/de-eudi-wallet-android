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

package org.sprind.wallet.uilogic.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL

/** One attribute of a credential. [value] may run to several lines, as an address does. */
@Immutable
data class CredentialAttribute(
    val label: String,
    val value: String,
)

/**
 * The attributes of a credential, each under its own label and separated by a rule.
 *
 * Deliberately one column: an address or a list of given names needs the width, which is why it does
 * not reuse [CredentialDetailsList], the two-column bullet summary used where space is tight.
 */
@Composable
fun CredentialAttributeList(
    attributes: List<CredentialAttribute>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                collectionInfo = CollectionInfo(rowCount = attributes.size, columnCount = 1)
            },
    ) {
        attributes.forEachIndexed { index, attribute ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = index,
                            rowSpan = 1,
                            columnIndex = 0,
                            columnSpan = 1,
                        )
                    }
                    .padding(vertical = SPACING_SMALL.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_SMALL.dp),
            ) {
                Text(
                    text = attribute.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = attribute.value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            // No trailing rule: it would read as another row starting.
            if (index != attributes.lastIndex) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CredentialAttributeListPreview() {
    PreviewTheme {
        CredentialAttributeList(
            modifier = Modifier.padding(SPACING_SMALL.dp),
            attributes = listOf(
                CredentialAttribute("Family name", "MUSTERMANN"),
                CredentialAttribute("Birth name", "GABLER"),
                CredentialAttribute("Given name(s)", "GABRIELA"),
                CredentialAttribute("Address", "HEIDESTRASSE 17\n51147 KÖLN\nDEUTSCHLAND"),
                CredentialAttribute("Staatsangehörigkeit", "DEUTSCH"),
            ),
        )
    }
}