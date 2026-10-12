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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import org.sprind.wallet.resourceslogic.generated.LightColors

/**
 * The digital ID (PID) as the design draws it: credential name and issuer over a white face, the
 * federal eagle in the top end corner and the PID pattern filling the lower part of the card.
 *
 * The face keeps its light colors in dark mode too: the eagle artwork has a white background, so the
 * card reads as the printed credential it stands for rather than as themed surface.
 *
 * Accessibility: the artwork is decorative; the card is announced as one element with its two
 * lines. With [onClick] it becomes a single button.
 */
@Composable
fun PidCredentialCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(CARD_CORNER_RADIUS)

    Box(
        modifier = modifier
            .shadow(
                elevation = CARD_SHADOW_ELEVATION,
                shape = shape,
                clip = false,
                ambientColor = CARD_SHADOW_COLOR,
                spotColor = CARD_SHADOW_COLOR,
            )
            .clip(shape)
            .background(Color(LightColors.schemesSurface))
            .border(
                width = BORDER_STROKE_1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {}
            .testTag("digitalCard")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SPACING_MEDIUM.dp),
            verticalArrangement = Arrangement.spacedBy(HEADER_TO_PATTERN_SPACING),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = SPACING_MEDIUM.dp,
                    end = EAGLE_AREA_WIDTH,
                ),
                verticalArrangement = Arrangement.spacedBy(TITLE_TO_ISSUER_SPACING),
            ) {
                Text(
                    text = stringResource(R.string.global_pid_credential_name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(LightColors.schemesOnSurface),
                )
                Text(
                    text = stringResource(R.string.__variable_text_pid_credential_1_var_issuer_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(LightColors.schemesOnSurfaceVariant),
                )
            }

            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PATTERN_ASPECT_RATIO),
                painter = painterResource(R.drawable.pid_card_pattern),
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        }

        Image(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = SPACING_EXTRA_MEDIUM.dp)
                .size(EAGLE_SIZE),
            painter = painterResource(R.drawable.pid_card_eagle),
            contentDescription = null,
        )
    }
}

private val CARD_CORNER_RADIUS = SPACING_MEDIUM.dp

// Same shadow_low token as EaaDocumentCard: offset y2, blur 8, #1D1D1E at 20%.
private val CARD_SHADOW_ELEVATION = 4.dp
private val CARD_SHADOW_COLOR = Color(0xFF1D1D1E)

private val HEADER_TO_PATTERN_SPACING = 13.dp
private val TITLE_TO_ISSUER_SPACING = 2.dp
private val EAGLE_SIZE = 68.dp
private val EAGLE_AREA_WIDTH = 91.dp
private const val PATTERN_ASPECT_RATIO = 328f / 141f

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PidCredentialCardPreview() {
    PreviewTheme {
        PidCredentialCard(
            modifier = Modifier
                .padding(SPACING_MEDIUM.dp)
                .fillMaxWidth(),
        )
    }
}