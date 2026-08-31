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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM

/**
 * The design system's `info card`: one question of an explanation, answered by a paragraph.
 *
 * The headline is exposed as a heading, so a screen reader can jump between the questions of an
 * explanation instead of having to read it from the top.
 */
@Composable
fun InfoCard(
    headline: String,
    body: String,
) {
    InfoCard(headline = headline) {
        InfoCardText(body)
    }
}

/**
 * The slot form of [InfoCard], for the cards that answer with more than a paragraph — artwork, or an
 * action that follows from the question. The order inside the card is the caller's, since the designs
 * put the artwork above the text in some cards and below it in others.
 */
@Composable
fun InfoCard(
    headline: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(INFO_CARD_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_MEDIUM.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

/** One paragraph of an [InfoCard]. */
@Composable
fun InfoCardText(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private val INFO_CARD_CORNER = 24.dp