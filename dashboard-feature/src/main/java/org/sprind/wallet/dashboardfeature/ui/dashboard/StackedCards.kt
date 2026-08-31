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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.resourceslogic.R
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
    val cardHeight = dimensionResource(R.dimen.eaa_card_height)
    val stackHeight = if (cards.isEmpty()) {
        0.dp
    } else {
        (cardHeight - overlapDp) * cards.size + overlapDp
    }
    val cardsOffset = cardHeight - overlapDp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(stackHeight)
    ) {
        cards.forEachIndexed { index, card ->
            EaaDocumentCard(
                modifier = Modifier
                    .offset(y = cardsOffset * index)
                    .zIndex(index.toFloat())
                    .fillMaxWidth(),
                data = card,
                onClick = onClick
            )
        }
    }
}