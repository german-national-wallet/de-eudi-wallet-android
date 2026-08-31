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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.DEFAULT_ICON_SIZE
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import org.sprind.wallet.designsystem.typography.CustomTypography

data class FoldableCardConfig(
    val title: String,
    val details: List<Pair<String, String>> = emptyList(),
    val expanded: Boolean = false,
    val onDetailsClick: () -> Unit,
)

@Composable
fun FoldableCard(
    modifier: Modifier,
    config: FoldableCardConfig,
    content: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier.border(
            width = BORDER_STROKE_1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp)
        ).height(dimensionResource(R.dimen.document_card_height)),
        shape = RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.document_card_height))
        ) {
            Box(
                modifier = Modifier
                    .background(ThemeColors.secondary)
                    .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_SMALL.dp)
            ) {
                Text(
                    text = config.title,
                    style = CustomTypography.titleMediumLarge.copy(color = ThemeColors.onPrimaryPid),
                    modifier = Modifier.fillMaxWidth()
                )
                WrapIconButton(
                    modifier = Modifier
                        .padding(start = SIZE_MEDIUM.dp)
                        .size(DEFAULT_ICON_SIZE.dp)
                        .align(Alignment.CenterEnd),
                    iconData = if (config.expanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown,
                    customTint = ThemeColors.onPrimaryPid,
                    onClick = config.onDetailsClick
                )

            }
            if (config.expanded && config.details.isNotEmpty()) {
                CredentialDetailsList(details = config.details)
            } else if (config.expanded) {
                Box(
                    modifier = modifier.padding(SPACING_MEDIUM.dp)
                ) {
                    content()
                }
            } else {
                Box(
                    modifier = Modifier
                        .background(ThemeColors.secondary)
                        .fillMaxSize()
                )
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun FoldableCardPreviewFolded() {
    FoldableCard(
        modifier = Modifier,
        config = FoldableCardConfig(
            title = stringResource(R.string.global_pid_credential_name),
            onDetailsClick = {}
        )
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun FoldableCardPreviewExpanded() {
    FoldableCard(
        modifier = Modifier,
        config = FoldableCardConfig(
            title = stringResource(R.string.global_pid_credential_name),
            details = listOf(
                "Family name" to "Mustermann",
                "Date of birth" to "01.01.1990",
                "SaNa Number" to "123456789",
                "First name(s)" to "Erika",
                "address" to "Main Street 123",
                "Issued on" to "01.01.2024"
            ),
            expanded = true,
            onDetailsClick = {}
        )
    )
}