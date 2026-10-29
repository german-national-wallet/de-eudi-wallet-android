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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.ThemeShapes
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import org.sprind.wallet.uilogic.extension.regular
import org.sprind.wallet.uilogic.extension.untrimmed

/** Where the claims are shown: the design styles the card and the bottom sheet slightly differently. */
enum class CredentialClaimsVariant { CARD, SHEET }

/**
 * The design's `attribute_card_presentation`: a [CredentialClaimsHeader] over a [CredentialClaimList],
 * framed as a card. A bottom sheet showing the same credential uses the two parts on its own surface.
 */
@Composable
fun CredentialClaimsCard(
    name: String,
    issuer: String,
    claimLabels: List<String>,
    logo: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    struckThrough: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = ThemeShapes.EXTRA_LARGE.dp,
            topEnd = ThemeShapes.EXTRA_LARGE.dp,
            bottomStart = CARD_BOTTOM_CORNER,
            bottomEnd = CARD_BOTTOM_CORNER,
        ),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BORDER_STROKE_1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            CredentialClaimsHeader(name = name, issuer = issuer, logo = logo, struckThrough = struckThrough)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CredentialClaimList(
                modifier = Modifier.padding(
                    start = SPACING_EXTRA_MEDIUM.dp,
                    top = SPACING_SMALL.dp,
                    end = SPACING_EXTRA_MEDIUM.dp,
                    bottom = SPACING_MEDIUM.dp,
                ),
                labels = claimLabels,
                struckThrough = struckThrough,
            )
        }
    }
}

/**
 * The credential's name and issuer, with [logo] filling the trailing edge at the header's height.
 * [struckThrough] marks a credential that was not shared.
 */
@Composable
fun CredentialClaimsHeader(
    name: String,
    issuer: String,
    logo: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    struckThrough: Boolean = false,
    variant: CredentialClaimsVariant = CredentialClaimsVariant.CARD,
) {
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = HEADER_START_PADDING,
                    top = SPACING_MEDIUM.dp,
                    end = SPACING_MEDIUM.dp,
                    bottom = SPACING_MEDIUM.dp,
                ),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.untrimmed().struckThrough(struckThrough),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (issuer.isNotBlank()) {
                Text(
                    text = issuer,
                    style = when (variant) {
                        CredentialClaimsVariant.CARD -> MaterialTheme.typography.labelLarge.regular()
                        CredentialClaimsVariant.SHEET -> MaterialTheme.typography.bodyLarge
                    }.untrimmed(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        logo()
    }
}

/** The claim labels, one per row and separated by a rule. */
@Composable
fun CredentialClaimList(
    labels: List<String>,
    modifier: Modifier = Modifier,
    struckThrough: Boolean = false,
    variant: CredentialClaimsVariant = CredentialClaimsVariant.CARD,
) {
    Column(modifier = modifier.padding(horizontal = SPACING_SMALL.dp)) {
        labels.forEachIndexed { index, label ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.tertiaryContainer)
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = when (variant) {
                            CredentialClaimsVariant.CARD -> CLAIM_ROW_MIN_HEIGHT
                            CredentialClaimsVariant.SHEET -> SHEET_CLAIM_ROW_MIN_HEIGHT
                        }
                    )
                    .wrapContentHeight(Alignment.CenterVertically),
                text = label,
                style = when (variant) {
                    CredentialClaimsVariant.CARD -> MaterialTheme.typography.bodyLarge
                    CredentialClaimsVariant.SHEET -> MaterialTheme.typography.labelLarge
                }.untrimmed().struckThrough(struckThrough),
                color = when {
                    struckThrough -> MaterialTheme.colorScheme.onSurfaceVariant
                    variant == CredentialClaimsVariant.SHEET -> ThemeColors.onBackgroundVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun TextStyle.struckThrough(enabled: Boolean): TextStyle =
    if (enabled) copy(textDecoration = TextDecoration.LineThrough) else this

private val CARD_BOTTOM_CORNER = 16.dp
private val HEADER_START_PADDING = 18.dp
private val CLAIM_ROW_MIN_HEIGHT = 37.dp
private val SHEET_CLAIM_ROW_MIN_HEIGHT = 39.dp

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CredentialClaimsCardPreview() {
    PreviewTheme {
        CredentialClaimsCard(
            name = "Digitaler Ausweis",
            issuer = "Bundesrepublik Deutschland",
            claimLabels = listOf("Familienname", "Geburtsname", "Vorname(n)"),
            logo = {},
        )
    }
}
