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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.WEIGHT_1
import eu.europa.ec.uilogic.component.wrap.WrapCard
import eu.europa.ec.uilogic.component.wrap.WrapIcon

@Immutable
data class NavigationCardButton(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Immutable
data class NavigationTopAction(
    val text: String,
    val icon: IconData,
    val onClick: (() -> Unit)? = null,
    val tintIcon: Boolean = true,
)

/**
 * Accessibility:
 * - the answers are exposed as a single-column collection and each card reports its position, so a
 *   screen reader announces e.g. "Yes, I have one, button, item 1 of 2";
 * - each card is merged into one node, so it is announced as one phrase rather than text plus icon;
 * - the trailing arrows and the [topAction] icon are decorative and hidden, since the text next to
 *   them already says where the choice leads.
 *
 * @param buttons the answers, rendered top to bottom in the given order.
 * @param modifier applied to the section; pass the sticky-bottom insets here.
 * @param topAction the optional row above the answers.
 */
@Composable
fun ButtonNavigationBottom(
    modifier: Modifier = Modifier,
    buttons: List<NavigationCardButton>,
    topAction: NavigationTopAction? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        topAction?.let { TopActionRow(action = it) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    collectionInfo = CollectionInfo(rowCount = buttons.size, columnCount = 1)
                },
            verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
        ) {
            buttons.forEachIndexed { index, button ->
                CardButton(button = button, index = index)
            }
        }
    }
}
/**
 * One answer card. It grows past [CARD_BUTTON_MIN_HEIGHT] instead of truncating, so the long answers
 * ("No, I don't have any of those ID documents") stay readable at large font scales.
 */
@Composable
private fun CardButton(
    button: NavigationCardButton,
    index: Int,
) {
    val shape = RoundedCornerShape(CARD_BUTTON_CORNER)
    val borderColor = if (button.enabled) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_BORDER_ALPHA)
    }

    WrapCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = BORDER_STROKE_1.dp, color = borderColor, shape = shape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                collectionItemInfo = CollectionItemInfo(
                    rowIndex = index,
                    rowSpan = 1,
                    columnIndex = 0,
                    columnSpan = 1,
                )
            },
        enabled = button.enabled,
        onClick = button.onClick,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CARD_BUTTON_MIN_HEIGHT)
                .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_SMALL.dp),
            horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(WEIGHT_1),
                text = button.text,
                style = MaterialTheme.typography.labelLarge,
            )
            // Decoration: every card carries the same arrow, so it says nothing the answer text and
            // the button role do not already say.
            WrapIcon(
                iconData = AppIcons.ArrowRightLong,
                modifier = Modifier
                    .size(SIZE_LARGE.dp)
                    .clearForAccessibility(),
                customTint = MaterialTheme.colorScheme.onSurface,
                enabled = button.enabled,
            )
        }
    }
}

private val CARD_BUTTON_MIN_HEIGHT = 56.dp
private val CARD_BUTTON_CORNER = 12.dp
private const val DISABLED_BORDER_ALPHA = 0.12f

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ButtonNavigationBottomTwoAnswersPreview() {
    PreviewTheme {
        ButtonNavigationBottom(
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
            topAction = NavigationTopAction(
                text = "Nutzt die Online-Ausweisfunktion",
                icon = AppIcons.EidLogo,
                tintIcon = false,
            ),
            buttons = listOf(
                NavigationCardButton(text = "Ja, vorhanden", onClick = {}),
                NavigationCardButton(
                    text = "Nein, ich habe keinen dieser Ausweise",
                    onClick = {},
                ),
            ),
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ButtonNavigationBottomThreeAnswersPreview() {
    PreviewTheme {
        ButtonNavigationBottom(
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
            topAction = NavigationTopAction(
                text = "Was ist die Karten-PIN oder PIN-Brief",
                icon = AppIcons.Help,
                onClick = {},
            ),
            buttons = listOf(
                NavigationCardButton(text = "Ja, Karten-PIN festgelegt und bekannt", onClick = {}),
                NavigationCardButton(text = "Nein, aber jetzt mit PIN-Brief festlegen", onClick = {}),
                NavigationCardButton(
                    text = "Ich habe keinen PIN-Brief oder meine PIN vergessen",
                    onClick = {},
                ),
            ),
        )
    }
}