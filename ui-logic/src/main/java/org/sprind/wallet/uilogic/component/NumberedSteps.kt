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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import androidx.compose.foundation.layout.padding
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.WEIGHT_1

/**
 * What a journey takes, as a numbered list with each step linked to the next.
 *
 * The steps are exposed as a single-column collection, so each is announced with its position.
 */
@Composable
fun NumberedSteps(
    steps: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                collectionInfo = CollectionInfo(rowCount = steps.size, columnCount = 1)
            },
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
    ) {
        steps.forEachIndexed { index, step ->
            if (index > 0) {
                StepConnector()
            }
            Step(text = step, index = index)
        }
    }
}

@Composable
private fun Step(
    text: String,
    index: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                collectionItemInfo = CollectionItemInfo(
                    rowIndex = index,
                    rowSpan = 1,
                    columnIndex = 0,
                    columnSpan = 1,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(SPACING_EXTRA_MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(STEP_NUMBER_SIZE)
                .background(color = MaterialTheme.colorScheme.surfaceContainer, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            modifier = Modifier.weight(WEIGHT_1),
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StepConnector() {
    Box(
        modifier = Modifier
            .width(STEP_NUMBER_SIZE)
            .height(SPACING_LARGE_32.dp),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
    }
}

private val STEP_NUMBER_SIZE = 44.dp

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NumberedStepsPreview() {
    PreviewTheme {
        NumberedSteps(
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
            steps = listOf(
                "Grant data release for your ID card",
                "Enter your card PIN",
                "Scan your ID card and verify your card PIN",
                "Create Digital ID and the associated code",
            ),
        )
    }
}