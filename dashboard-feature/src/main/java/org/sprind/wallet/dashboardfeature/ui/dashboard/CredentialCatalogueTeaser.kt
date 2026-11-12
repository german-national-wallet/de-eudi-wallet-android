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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.ICON_SIZE_40
import eu.europa.ec.uilogic.component.utils.SIZE_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapIcon

/**
 * The dashboard's entry to the credential catalogue: a stack of small cards, a label and a plus
 * button. The whole card is the tap target; the plus is part of the picture.
 *
 * @param onClick called when the teaser is tapped.
 */
@Composable
fun CredentialCatalogueTeaser(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TEASER_MIN_HEIGHT)
            .clip(TEASER_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(end = SPACING_EXTRA_MEDIUM.dp, top = SPACING_EXTRA_MEDIUM.dp, bottom = SPACING_EXTRA_MEDIUM.dp),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The left part of the stack hangs out over the card's edge and is cut off there. Only
        // the visible width takes up room in the row, so the label ends up one arrangement gap
        // away from what can be seen; an offset would move the stack but not its slot.
        Box(modifier = Modifier.width(STACK_WIDTH + CARD_STACK_OFFSET_X)) {
            CardStack(modifier = Modifier.wrapContentWidth(align = Alignment.End, unbounded = true))
        }

        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.overview_initial_credential_teaser_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        PlusButton()
    }
}

@Composable
private fun PlusButton() {
    Box(
        modifier = Modifier.size(SIZE_EXTRA_LARGE.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ICON_SIZE_40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(BORDER_STROKE_1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            WrapIcon(iconData = AppIcons.Add)
        }
    }
}

/** Three small cards, each showing a differently colored tab, fanned out a little. */
@Composable
private fun CardStack(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = STACK_WIDTH, height = STACK_HEIGHT)
            .rotate(STACK_ROTATION),
    ) {
        MiniCard(offsetY = 0.dp, height = MINI_CARD_HEIGHT, tabColor = TAB_GREEN)
        MiniCard(offsetY = MINI_CARD_MIDDLE_OFFSET, height = MINI_CARD_HEIGHT, tabColor = TAB_LIGHT_GREEN)
        MiniCard(offsetY = STACK_HEIGHT - MINI_CARD_HEIGHT, height = MINI_CARD_HEIGHT, tabColor = TAB_LILAC)
    }
}

@Composable
private fun MiniCard(offsetY: Dp, height: Dp, tabColor: Color) {
    val shape = RoundedCornerShape(MINI_CARD_CORNER_RADIUS)

    Box(
        modifier = Modifier
            .offset(y = offsetY)
            .size(width = STACK_WIDTH, height = height)
            .shadow(elevation = MINI_CARD_ELEVATION, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(MINI_CARD_BORDER, MaterialTheme.colorScheme.outline, shape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = TAB_WIDTH, height = height)
                .background(tabColor),
        )
    }
}

private val TEASER_SHAPE = RoundedCornerShape(SPACING_LARGE.dp)
private val TEASER_MIN_HEIGHT = 80.dp

private val CARD_STACK_OFFSET_X = (-25).dp
private val STACK_WIDTH = 90.dp
private val STACK_HEIGHT = 52.dp
private val MINI_CARD_HEIGHT = 30.dp
private val MINI_CARD_MIDDLE_OFFSET = 11.dp
private val MINI_CARD_CORNER_RADIUS = 5.5.dp
private val MINI_CARD_BORDER = 0.34.dp
private val MINI_CARD_ELEVATION = 1.dp
private val TAB_WIDTH = 29.dp
private const val STACK_ROTATION = -2.86f

// The tab colors of the design's stack illustration.
private val TAB_GREEN = Color(0xFF329D77)
private val TAB_LIGHT_GREEN = Color(0xFF96F5AF)
private val TAB_LILAC = Color(0xFFE8DEF8)

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CredentialCatalogueTeaserPreview() {
    PreviewTheme {
        CredentialCatalogueTeaser(
            onClick = {},
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
        )
    }
}
