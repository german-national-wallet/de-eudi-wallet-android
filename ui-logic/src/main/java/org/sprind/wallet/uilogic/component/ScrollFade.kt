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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM

private const val SCROLL_FADE_HEIGHT = SPACING_MEDIUM
private const val SCROLL_FADE_ALPHA = 0.12f

/**
 * A vertically scrolling [Column] with a [ScrollFadeIndicator] along its bottom edge.
 *
 * Meant for content stacked above pinned actions, e.g. a dialog's text above its buttons: give it
 * `Modifier.weight(1f, fill = false)` in the parent column so short content still wraps tightly
 * and only content taller than the available space scrolls (and shows the fade).
 *
 * @param modifier applied to the outer container (size/weight); the fade spans its full width.
 * @param contentModifier applied inside the scroll, e.g. content padding.
 */
@Composable
fun ScrollFadeColumn(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .then(contentModifier),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
        ScrollFadeIndicator(scrollState)
    }
}

/**
 * A soft grey shadow along the bottom edge of a scrolling area, signalling that more content is
 * hidden below (e.g. behind a dialog's buttons). It fades out once the content reaches its end,
 * and never shows on content that fits. Place it in the [Box] that holds the scrolling content.
 */
@Composable
fun BoxScope.ScrollFadeIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val fadeAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollForward) 1f else 0f,
        label = "scrollFadeAlpha",
    )
    val fadeColor = MaterialTheme.colorScheme.scrim.copy(alpha = SCROLL_FADE_ALPHA)
    val fadeBrush = remember(fadeColor) {
        Brush.verticalGradient(listOf(Color.Transparent, fadeColor))
    }

    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(SCROLL_FADE_HEIGHT.dp)
            .graphicsLayer { alpha = fadeAlpha }
            .background(fadeBrush)
    )
}
