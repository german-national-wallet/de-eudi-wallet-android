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

package org.sprind.wallet.dashboardfeature.ui.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.corelogic.extension.EaaCardData
import org.sprind.wallet.uilogic.component.EaaDocumentCard

/**
 * Displays EAA documents in a stacked layout with configurable overlap.
 * Cards are stacked with each subsequent card appearing below the previous
 * and on top of it, with the last card in the list appearing as the topmost.
 *
 * @param cards List of [EaaCardData] to display in stacked layout
 * @param onClick Callback invoked when card is clicked, passing document ID
 * @param overlapDp Vertical overlap amount between cards (default: 40dp, leaving 2/3 of each card visible)
 * @param modifier Modifier for the container
 */
@Composable
fun StackedCards(
    cards: List<EaaCardData>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    overlapDp: Dp = 40.dp,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            cards.forEach { card ->
                EaaDocumentCard(data = card, onClick = onClick)
            }
        },
    ) { measurables, constraints ->
        val overlap = overlapDp.roundToPx()
        val cardConstraints = Constraints(
            minWidth = constraints.maxWidth,
            maxWidth = constraints.maxWidth,
        )
        val placeables = measurables.map { it.measure(cardConstraints) }

        var next = 0
        val tops = placeables.map { placeable ->
            val top = next
            next += placeable.height - overlap
            top
        }
        val height = placeables.lastOrNull()?.let { tops.last() + it.height } ?: 0

        layout(constraints.maxWidth, height) {
            // Placed in order, so each card draws over the one it overlaps.
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(x = 0, y = tops[index])
            }
        }
    }
}