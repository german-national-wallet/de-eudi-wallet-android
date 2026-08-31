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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import org.sprind.wallet.designsystem.typography.CustomTypography

@Composable
fun DocumentCard(modifier: Modifier) {
    Card(
        modifier = modifier.border(
            width = BORDER_STROKE_1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp)
        ),
        colors = CardDefaults.cardColors(
            containerColor = ThemeColors.primaryPid
        ),
        shape = RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                modifier = Modifier
                    .align(Alignment.TopEnd),
                painter = painterResource(id = R.drawable.ic_eagle_right),
                contentDescription = null,
                tint = ThemeColors.onPrimaryPid.copy(alpha = 0.15f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
            ) {
                Text(
                    text = stringResource(R.string.global_pid_credential_name),
                    style = CustomTypography.titleMediumLarge.copy(color = ThemeColors.onPrimaryPid),
                )
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun DocumentCardPreview() {
    DocumentCard(modifier = Modifier)
}