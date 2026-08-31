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

package org.sprind.wallet.uilogic.component.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import org.sprind.wallet.uilogic.component.clearForAccessibility

/**
 * Preview-only sticky bottom: the design-system two-button layout (a primary button with a trailing
 * "→" stacked above a secondary button), composed with the shared [WrapStickyBottomContent] using a
 * [StickyBottomType.Generic] block. Real screens compose their own actions the same way.
 */
@Composable
internal fun ContentTemplatePreviewButtons(padding: PaddingValues) {
    WrapStickyBottomContent(
        stickyBottomModifier = Modifier
            .fillMaxWidth()
            .padding(top = SPACING_MEDIUM.dp, bottom = padding.calculateBottomPadding())
            .padding(horizontal = SPACING_MEDIUM.dp),
        stickyBottomConfig = StickyBottomConfig(
            showDivider = false,
            type = StickyBottomType.Generic,
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)) {
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(type = ButtonType.PRIMARY, onClick = {}),
            ) {
                Text(text = "Label", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.width(SPACING_SMALL.dp))
                WrapIcon(
                    iconData = AppIcons.ArrowRight,
                    modifier = Modifier
                        .size(SIZE_MEDIUM_LARGE.dp)
                        .clearForAccessibility(),
                )
            }
            WrapButton(
                modifier = Modifier.fillMaxWidth(),
                buttonConfig = ButtonConfig(type = ButtonType.SECONDARY, onClick = {}),
            ) {
                Text(text = "Label", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}