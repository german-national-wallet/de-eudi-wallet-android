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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.wrap.WrapIcon

/**
 * The designs' round checkbox ("Checkboxes M&M"): an outlined circle that fills with the primary
 * container color and shows a checkmark once [checked].
 *
 * It only draws the mark and is hidden from the accessibility tree; use [RoundCheckboxRow], which
 * makes the whole row, label included, one checkbox for screen readers and keyboards.
 */
@Composable
fun RoundCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(CHECKBOX_SIZE)
            .clearForAccessibility(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(MARK_SIZE)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .border(width = BORDER_WIDTH, color = ThemeColors.primaryButtonOutline, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                WrapIcon(
                    iconData = AppIcons.Checkmark,
                    modifier = Modifier.size(CHECK_SIZE),
                    customTint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * A [RoundCheckbox] followed by its label [content]. The whole row toggles the checkbox and is
 * exposed as a single checkbox, announced with the label and its checked state.
 *
 * Links inside the label (for example a `LinkAnnotation` in the text) stay separately reachable:
 * tapping them opens the link without toggling the checkbox.
 *
 * @param checked whether the checkbox is ticked.
 * @param onCheckedChange invoked with the new value when the row is toggled.
 */
@Composable
fun RoundCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundCheckbox(checked = checked)
        content()
    }
}

// The design component's footprint: the 26dp mark in an 11dp state layer and 4dp of padding.
private val CHECKBOX_SIZE = 56.dp
private val MARK_SIZE = 26.dp
private val BORDER_WIDTH = 1.5.dp
private val CHECK_SIZE = 24.dp

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun RoundCheckboxRowPreview() {
    PreviewTheme {
        var checked by remember { mutableStateOf(true) }
        RoundCheckboxRow(checked = checked, onCheckedChange = { checked = it }) {
            Text(text = "I agree to the terms of use.", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
