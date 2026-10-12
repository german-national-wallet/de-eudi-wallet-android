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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import org.sprind.wallet.designsystem.typography.CustomTypography
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

/**
 * How an [OrganisationRow] is drawn.
 *
 * @property nameTextStyle how the organisation's name is set.
 * @property iconSize the box the tile's mark is drawn in; it fills the box.
 * @property trailingIconSize the box the mark at the end is drawn in.
 * @property verticalPadding air above and below the row's own contents. A row that opens something
 *   wants it, for the touch target; a row that only states who the party is takes its spacing from
 *   whatever it sits in.
 */
@Immutable
data class OrganisationRowStyle(
    val nameTextStyle: TextStyle,
    val iconSize: Dp,
    val trailingIconSize: Dp,
    val verticalPadding: Dp,
)

object OrganisationRowDefaults {

    /** An issuer named inside a credential offer's section. */
    val issuer: OrganisationRowStyle
        @Composable get() = OrganisationRowStyle(
            nameTextStyle = ThemeTextStyles.titleMediumLarge,
            iconSize = SIZE_MEDIUM_LARGE.dp,
            trailingIconSize = SIZE_LARGE.dp,
            verticalPadding = SPACING_SMALL.dp,
        )

    /**
     * The service a request comes from, where the row is the subject of the screen: the name is set
     * at the screen's own title size, and the two marks are drawn smaller so the name carries it.
     */
    val service: OrganisationRowStyle
        @Composable get() = OrganisationRowStyle(
            nameTextStyle = CustomTypography.titleMedium,
            iconSize = 15.dp,
            trailingIconSize = 19.dp,
            verticalPadding = 0.dp,
        )
}

@Composable
fun OrganisationRow(
    name: String,
    icon: IconData,
    modifier: Modifier = Modifier,
    trailingIcon: IconData? = null,
    onClick: (() -> Unit)? = null,
    style: OrganisationRowStyle = OrganisationRowDefaults.issuer,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = style.verticalPadding)
            .semantics(mergeDescendants = true) {
                if (onClick != null) role = Role.Button
            },
    ) {
        Box(
            modifier = Modifier
                .size(TILE_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            WrapIcon(
                iconData = icon,
                modifier = Modifier.size(style.iconSize),
                customTint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = name,
            style = style.nameTextStyle,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SPACING_MEDIUM.dp),
        )

        trailingIcon?.let { mark ->
            WrapIcon(
                iconData = mark,
                modifier = Modifier
                    .size(style.trailingIconSize)
                    .clearAndSetSemantics { },
                customTint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val TILE_SIZE = 40.dp

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun OrganisationRowPreview() {
    PreviewTheme {
        OrganisationRow(
            name = "BVG Mobility",
            icon = AppIcons.Transfer,
            trailingIcon = AppIcons.Info,
            style = OrganisationRowDefaults.service,
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun OrganisationRowClickablePreview() {
    PreviewTheme {
        OrganisationRow(
            name = "Bundesdruckerei GmbH",
            icon = AppIcons.Building,
            trailingIcon = AppIcons.Info,
            onClick = {},
        )
    }
}
