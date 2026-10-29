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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.ThemeShapes
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

/** The design system's `banner` colour variants. */
enum class NoticeBannerType { NEUTRAL, WARNING, ERROR }

/**
 * The design system's `banner`: a surface with a coloured stripe on its leading edge, a mark and the
 * text, with an optional [title] above it. Announced as one phrase.
 *
 * The mark follows the design's two kinds: an exclamation mark beside a plain text, an info mark
 * beside a titled one.
 */
@Composable
fun NoticeBanner(
    body: String,
    modifier: Modifier = Modifier,
    type: NoticeBannerType = NoticeBannerType.NEUTRAL,
    title: String? = null,
    icon: IconData = if (title == null) AppIcons.Warning else AppIcons.Info,
) {
    val (container, stripe) = type.colors()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        shape = RoundedCornerShape(ThemeShapes.MEDIUM.dp),
        color = container,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(STRIPE_WIDTH)
                    .background(stripe),
            )
            Row(
                modifier = Modifier.padding(SPACING_MEDIUM.dp),
                horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
            ) {
                Box(
                    modifier = Modifier.size(if (title == null) SIZE_LARGE.dp else TITLED_ICON_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    WrapIcon(
                        iconData = icon,
                        modifier = Modifier.size(if (title == null) PLAIN_ICON_SIZE else TITLED_ICON_SIZE),
                        customTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)) {
                    title?.let {
                        Text(
                            text = it,
                            style = ThemeTextStyles.bannerTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** The container and stripe colours of a [NoticeBannerType]. */
@Composable
private fun NoticeBannerType.colors(): Pair<Color, Color> = when (this) {
    NoticeBannerType.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurface
    NoticeBannerType.WARNING -> ThemeColors.warningContainer to ThemeColors.warningOutline
    NoticeBannerType.ERROR -> MaterialTheme.colorScheme.errorContainer to ThemeColors.errorOutline
}

private val STRIPE_WIDTH = 4.dp
private val TITLED_ICON_SIZE = 20.dp
private val PLAIN_ICON_SIZE = 18.dp

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NoticeBannerPreview() {
    val body = "Um den Vorgang abzuschließen, gehe zurück zum Dienst, von dem aus du die Identifizierung gestartet hast."
    PreviewTheme {
        Column(verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)) {
            NoticeBannerType.entries.forEach { NoticeBanner(body = body, type = it) }
            NoticeBanner(title = "Zweck", body = body)
        }
    }
}
