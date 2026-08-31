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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL

/**
 * Displays a scrollable two-column list of credential details.
 *
 * This composable renders a list of label-value pairs in two columns,
 * split evenly. Each item is displayed as a bullet point with the format
 * "• label: value". The content scrolls vertically if it exceeds the
 * available height.
 *
 * @param modifier Optional modifier for the root Column. Defaults to [Modifier].
 * @param details List of label-value pairs to display. The list is split
 *                in half, with the first half shown in the left column
 *                and the second half in the right column.
 *
 * @see CredentialDetailsListPreview
 */
@Composable
fun CredentialDetailsList(
    modifier: Modifier = Modifier,
    details: List<Pair<String, String>>
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SPACING_SMALL.dp)
    ) {
        val halfSize = (details.size + 1) / 2
        val leftColumn = details.take(halfSize)
        val rightColumn = details.drop(halfSize)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
            ) {
                leftColumn.forEach { (label, value) ->
                    Text(
                        text = "• $label: $value",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
            ) {
                rightColumn.forEach { (label, value) ->
                    Text(
                        text = "• $label: $value",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CredentialDetailsListPreview() {
    CredentialDetailsList(
        details = listOf(
            "family name" to "Doe",
            "date of birth" to "01.01.1990",
            "SaNa Number" to "123456789",
            "First name(s)" to "John",
            "address" to "Main Street 123",
            "Issued on" to "01.01.2024"
        )
    )
}
