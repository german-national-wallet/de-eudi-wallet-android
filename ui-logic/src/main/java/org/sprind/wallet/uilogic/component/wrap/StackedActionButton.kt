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

package org.sprind.wallet.uilogic.component.wrap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

/**
 * How a [StackedActionButton] reads.
 */
enum class StackedActionStyle {
    /** The ordinary answer: an outlined button on the surface colour. */
    NEUTRAL,

    /** The answer the user cannot undo: the error container behind the error outline. */
    DESTRUCTIVE,
}

/**
 * Full-width button for vertically stacked choices inside dialogs.
 */
@Composable
fun StackedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: StackedActionStyle = StackedActionStyle.NEUTRAL,
    trailingIcon: IconData? = null,
    enabled: Boolean = true,
) {
    val isDestructive = style == StackedActionStyle.DESTRUCTIVE
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        ThemeColors.onSecondaryButton
    }

    WrapButton(
        modifier = modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            enabled = enabled,
            onClick = onClick,
            isWarning = isDestructive,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_LARGE.dp),
            horizontalArrangement = Arrangement.spacedBy(
                space = SPACING_SMALL.dp,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WrapText(
                modifier = Modifier.weight(weight = 1f, fill = false),
                text = text,
                textConfig = TextConfig(
                    style = ThemeTextStyles.onSecondaryButton,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                ),
            )

            trailingIcon?.let { icon ->
                WrapIcon(
                    modifier = Modifier.size(TRAILING_ICON_SIZE),
                    iconData = icon,
                    customTint = contentColor,
                )
            }
        }
    }
}

private val TRAILING_ICON_SIZE = 20.dp

@Preview(showBackground = true)
@Composable
private fun StackedActionButtonPreview() {
    PreviewTheme {
        Column(
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        ) {
            StackedActionButton(
                text = "Yes, reject",
                onClick = {},
                style = StackedActionStyle.DESTRUCTIVE,
            )
            StackedActionButton(
                text = "Report suspicion",
                onClick = {},
                trailingIcon = AppIcons.Launch,
            )
            StackedActionButton(text = "Close", onClick = {})
        }
    }
}
