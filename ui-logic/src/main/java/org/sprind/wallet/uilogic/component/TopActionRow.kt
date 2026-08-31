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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.utils.BUTTON_HEIGHT
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapImage


/**
 * Renders a [NavigationTopAction] either as a text button or, when it carries no
 * [NavigationTopAction.onClick], as a plain labelled row. Both keep the same icon-then-text metrics so
 * the section looks identical whether or not the row is actionable.
 */
@Composable
fun TopActionRow(action: NavigationTopAction) {
    val icon: @Composable () -> Unit = {
        val iconModifier = Modifier
            .size(SIZE_MEDIUM_LARGE.dp)
            .clearForAccessibility()
        if (action.tintIcon) {
            WrapIcon(
                iconData = action.icon,
                modifier = iconModifier,
                customTint = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            WrapImage(
                iconData = action.icon,
                modifier = iconModifier,
                contentScale = ContentScale.Fit,
            )
        }
    }

    val label: @Composable () -> Unit = {
        Text(
            text = action.text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    val onClick = action.onClick
    if (onClick == null) {
        Row(
            modifier = Modifier
                .heightIn(min = BUTTON_HEIGHT.dp)
                .padding(horizontal = SPACING_EXTRA_MEDIUM.dp)
                .semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            label()
        }
    } else {
        WrapButton(
            modifier = Modifier.semantics(mergeDescendants = true) { },
            buttonConfig = ButtonConfig(
                type = ButtonType.TEXT,
                onClick = onClick,
                contentPadding = PaddingValues(
                    start = SPACING_EXTRA_MEDIUM.dp,
                    end = SPACING_MEDIUM.dp,
                ),
            ),
        ) {
            icon()
            Spacer(modifier = Modifier.width(SPACING_SMALL.dp))
            label()
        }
    }
}
