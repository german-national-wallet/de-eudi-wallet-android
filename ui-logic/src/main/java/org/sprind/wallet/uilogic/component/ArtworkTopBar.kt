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

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.wrap.WrapImage

/**
 * Full-width artwork in place of a toolbar, for [ContentScreen]'s `topBar` slot with
 * `ScreenNavigateAction.NONE`; [action] aligns its own navigation affordance on it.
 */
@Composable
fun ArtworkTopBar(
    artwork: IconData,
    modifier: Modifier = Modifier,
    artworkAlignment: Alignment = Alignment.BottomCenter,
    action: @Composable BoxScope.() -> Unit = {},
) {
    val configuration = LocalConfiguration.current

    Box(modifier = modifier.fillMaxWidth()) {
        WrapImage(
            iconData = artwork,
            modifier = Modifier
                .fillMaxWidth()
                .height(artworkHeight(configuration))
                .clearAndSetSemantics { },
            contentScale = ContentScale.Crop,
            alignment = artworkAlignment,
        )
        action()
    }
}

private fun artworkHeight(configuration: Configuration): Dp = min(
    configuration.screenWidthDp.dp,
    configuration.screenHeightDp.dp * ARTWORK_MAX_SCREEN_FRACTION,
)

private const val ARTWORK_MAX_SCREEN_FRACTION = 0.5f
