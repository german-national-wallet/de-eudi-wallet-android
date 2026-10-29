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

package eu.europa.ec.uilogic.component.wrap

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.ICON_SIZE_28
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SIZE_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL

/** How much a [Banner] stands out from the content around it. */
enum class BannerStyle {
    /** A quiet note. */
    DEFAULT,

    /** A note the user has to follow for the step to work, marked by a bar along its leading edge. */
    ACCENTED,
}

/**
 * A note beside the content it qualifies, optionally led by a mark such as a warning.
 *
 * The mark and the note are announced as one phrase, so the note is never read without knowing what
 * kind of note it is.
 */
@Composable
fun Banner(
    modifier: Modifier = Modifier,
    body: String,
    icon: IconData? = null,
    style: BannerStyle = BannerStyle.DEFAULT,
) {
    BannerCard(modifier = modifier, icon = icon, style = style) {
        Text(
            text = body,
            style = style.bodyTextStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun Banner(
    modifier: Modifier = Modifier,
    body: AnnotatedString,
    icon: IconData? = null,
    style: BannerStyle = BannerStyle.DEFAULT,
) {
    BannerCard(modifier = modifier, icon = icon, style = style) {
        Text(
            text = body,
            style = style.bodyTextStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BannerCard(
    modifier: Modifier,
    icon: IconData?,
    style: BannerStyle,
    body: @Composable () -> Unit,
) {
    val isAccented = style == BannerStyle.ACCENTED
    WrapCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isAccented) SPACING_EXTRA_MEDIUM.dp else SIZE_SMALL.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Box {
            Row(
                modifier = Modifier
                    .padding(SPACING_MEDIUM.dp)
                    .semantics(mergeDescendants = true) { },
                horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
                // The mark sits with the first line, not in the middle of a wrapped note.
                verticalAlignment = Alignment.Top,
            ) {
                icon?.let { BannerMark(icon = it, isAccented = isAccented) }
                body()
            }
            if (isAccented) BannerAccent()
        }
    }
}

/**
 * The bar along the leading edge of a [BannerStyle.ACCENTED] note, as tall as the note it runs along.
 * Decorative, so it is drawn without semantics.
 */
@Composable
internal fun BoxScope.BannerAccent() {
    Image(
        painter = painterResource(R.drawable.banner_accent),
        contentDescription = null,
        modifier = Modifier
            .matchParentSize()
            .padding(vertical = ACCENT_INSET)
            .wrapContentWidth(Alignment.Start)
            .width(ACCENT_WIDTH),
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
    )
}

@Composable
private fun BannerMark(icon: IconData, isAccented: Boolean) {
    if (!isAccented) {
        WrapIcon(
            iconData = icon,
            modifier = Modifier.size(SIZE_MEDIUM_LARGE.dp),
            customTint = MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    // The mark's box is as tall as the note's first line, so the two stay level.
    Box(
        modifier = Modifier.size(ICON_SIZE_28.dp),
        contentAlignment = Alignment.Center,
    ) {
        WrapIcon(
            iconData = icon,
            modifier = Modifier.size(SIZE_MEDIUM_LARGE.dp),
            customTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val BannerStyle.bodyTextStyle: TextStyle
    @Composable get() = when (this) {
        BannerStyle.DEFAULT -> MaterialTheme.typography.bodyMedium
        BannerStyle.ACCENTED -> MaterialTheme.typography.bodyLarge
    }

private val ACCENT_WIDTH = 4.dp
private val ACCENT_INSET = 3.dp

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapSuccessMessageCardPreview() {
    PreviewTheme {
        Banner(
            body = "Your documents have been securely presented and saved."
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun AccentedBannerPreview() {
    PreviewTheme {
        Banner(
            body = "The middle of the back of the device and the ID card must touch",
            icon = AppIcons.Warning,
            style = BannerStyle.ACCENTED,
        )
    }
}