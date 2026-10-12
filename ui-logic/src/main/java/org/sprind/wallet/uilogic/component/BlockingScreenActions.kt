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

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon

/**
 * The outlined round close button of the full-screen blocking layouts (Figma "header",
 * `Close Button`), drawn over an [ArtworkTopBar] or on its own in the top end corner.
 */
@Composable
fun BoxScope.ArtworkCloseAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(SPACING_SMALL.dp)
            .size(CLOSE_ACTION_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .border(
                border = BorderStroke(CLOSE_ACTION_BORDER, MaterialTheme.colorScheme.outline),
                shape = CircleShape,
            )
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        WrapIcon(
            iconData = AppIcons.Close,
            customTint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A full-width button whose action leaves the app, marked with a trailing launch icon. */
@Composable
fun ExternalLinkButton(
    type: ButtonType,
    @StringRes textRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WrapButton(
        modifier = modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(type = type, onClick = onClick),
    ) {
        TextAndIcon(
            iconModifier = Modifier.size(SIZE_MEDIUM_LARGE.dp),
            textModifier = Modifier.padding(end = SPACING_SMALL.dp),
            textValue = stringResource(textRes),
            textConfig = TextConfig(style = MaterialTheme.typography.labelLarge),
            rightIconData = AppIcons.Launch,
            customTint = when (type) {
                ButtonType.PRIMARY -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private val CLOSE_ACTION_SIZE = 40.dp
private val CLOSE_ACTION_BORDER = 1.dp
