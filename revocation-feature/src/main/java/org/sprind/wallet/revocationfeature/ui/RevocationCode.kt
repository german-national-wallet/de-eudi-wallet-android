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

package org.sprind.wallet.revocationfeature.ui

import android.content.ClipData
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import kotlinx.coroutines.launch
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

@Composable
fun RevocationCode(
    revocationCode: String?,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardLabel = stringResource(R.string.app_onboarding_wallet_revocation_save_key_headline_1)
    val shape = RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp)
    var copied by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .border(
                width = BORDER_STROKE_1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clip(shape)
            .height(IntrinsicSize.Max)
            .clickable(
                enabled = revocationCode != null,
                onClickLabel = stringResource(R.string.app_onboarding_wallet_revocation_save_key_button_inital_1),
                onClick = {
                    coroutineScope.launch {
                        val entry = ClipData.newPlainText(clipboardLabel, revocationCode)
                            .toClipEntry()
                        clipboard.setClipEntry(entry)
                        copied = true
                    }
                },
                role = Role.Button
            )
            .padding(SPACING_EXTRA_MEDIUM.dp)
            .height(IntrinsicSize.Max)
    ) {
        SelectionContainer(modifier = Modifier.weight(1f)) {
            revocationCode?.let { Text(text = revocationCode, style = ThemeTextStyles.key) }
        }
        VerticalDivider(modifier = Modifier.fillMaxHeight().padding(horizontal = SPACING_SMALL.dp))
        CopyButton(copied = copied)
    }
}

@Composable
private fun CopyButton(copied: Boolean) {
    val text = if (copied) {
        stringResource(R.string.app_onboarding_wallet_revocation_save_key_button_activated_1)
    } else {
        stringResource(R.string.app_onboarding_wallet_revocation_save_key_button_inital_1)
    }
    Column(
        modifier = Modifier
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WrapIcon(
            iconData = AppIcons.CopyContent,
            modifier = Modifier
                .size(SIZE_MEDIUM_LARGE.dp)
                .clearAndSetSemantics { },
        )
        Text(text = text)
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun RevocationCodePreview() {
    PreviewTheme {
        RevocationCode(
            "rev1 dl5r vzak 5x72 6vht dgyf ra79 7n4x uzra w3ps t927 ytdp e5s7 g2us 8xra ur "
        )
    }
}
