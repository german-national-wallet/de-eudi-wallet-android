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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.WEIGHT_1
import eu.europa.ec.uilogic.component.wrap.WrapImage


data class EligibleDocument(
    val artwork: IconData,
    val name: String,
)


@Composable
fun EligibleDocumentRow(
    document: EligibleDocument,
    index: Int,
) {
    val artworkShape = RoundedCornerShape(ARTWORK_CORNER)

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
        horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WrapImage(
            iconData = document.artwork,
            modifier = Modifier
                .width(ARTWORK_WIDTH)
                .aspectRatio(ARTWORK_ASPECT_RATIO)
                .clip(artworkShape)
                .border(
                    width = BORDER_STROKE_1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = artworkShape,
                )
                .clearAndSetSemantics { },
            contentScale = ContentScale.Crop,
        )
        Text(
            modifier = Modifier.weight(WEIGHT_1),
            text = document.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val ARTWORK_WIDTH = 145.dp
private val ARTWORK_CORNER = 5.dp
private const val ARTWORK_ASPECT_RATIO = 1.585f