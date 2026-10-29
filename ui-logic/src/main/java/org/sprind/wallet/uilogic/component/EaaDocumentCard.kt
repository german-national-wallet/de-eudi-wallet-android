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
 * See the License for the specific language
 * governing permissions and limitations under the License.
 */

package org.sprind.wallet.uilogic.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.parseCssColor
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapAsyncImage
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import java.time.Instant

/**
 * Displays an EAA (Electronic Attestation of Attributes) document card using the
 * final Figma design: a two-region layout with a white left section (issuer name
 * and credential type) and a colored right panel (issuer logo).
 *
 * The design carries no expiry on the card, so [EaaCardData.validityDate] is not
 * drawn here. The field stays on the model because the document detail screen and
 * the mappers' own handling of implausible issuer dates still depend on it.
 *
 * Falls back gracefully:
 *  - No background color metadata -> [ThemeColors.defaultEaa]
 *  - No logo URI -> colored panel renders without a logo
 *
 * The card is as tall as its text, and never shorter than [minHeight].
 * Neither line is truncated: at the larger font scales a credential's name runs to
 * several lines, and cutting it short there is worse than a taller card on a
 * screen that scrolls anyway.
 *
 * Accessibility:
 *  - The logo is decorative; the issuer's name is read from the line above it
 *  - When [onClick] is non-null: card semantics label = "Open <credentialName>"
 *  - When [onClick] is null: card is non-interactive (no ripple, no click
 *    semantics); child Text nodes announce themselves naturally.
 *
 * @param modifier Modifier for the card
 * @param data The [EaaCardData] to render
 * @param onClick Callback invoked when card is clicked, passing the document ID.
 *   Pass null (the default) for an informational, non-interactive card.
 * @param footer Optional slot pinned to the bottom of the white left section. The issuance offer
 *   puts its "view data" action there, so the action sits on the credential it belongs to instead
 *   of floating underneath the card.
 * @param minHeight The height the card keeps when its text is shorter. Defaults to
 *   R.dimen.eaa_card_height; the issuance offer's details sheet passes a smaller one to show the
 *   card as a cut-off strip.
 * @param bottomCutOff Draws the card as a strip cut off below its text, as the issuance offer's
 *   details sheet heads its claim list with: square bottom corners, no drop shadow, and less room
 *   under the issuer line. The outline still runs all the way round, so it ends exactly where the
 *   card does.
 */
@Composable
fun EaaDocumentCard(
    modifier: Modifier = Modifier,
    data: EaaCardData,
    onClick: ((String) -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    minHeight: Dp = dimensionResource(R.dimen.eaa_card_height),
    bottomCutOff: Boolean = false,
) {
    val panelColor = parseCssColor(
        data.backgroundColor,
        ThemeColors.defaultEaa,
    )
    val faceColor = ThemeColors.surfaceBrightest
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val panelWidth = dimensionResource(R.dimen.eaa_colored_panel_width)
    val openLabel = stringResource(
        R.string.eaa_card_open,
        data.description,
    )

    val borderWidth = BORDER_STROKE_1.dp
    val bottomCornerRadius = if (bottomCutOff) 0.dp else CARD_CORNER_RADIUS
    val cardShape = RoundedCornerShape(
        topStart = CARD_CORNER_RADIUS,
        topEnd = CARD_CORNER_RADIUS,
        bottomStart = bottomCornerRadius,
        bottomEnd = bottomCornerRadius,
    )
    val innerBottomCornerRadius = (bottomCornerRadius - borderWidth).coerceAtLeast(0.dp)
    val contentShape = RoundedCornerShape(
        topStart = CARD_CORNER_RADIUS - borderWidth,
        topEnd = CARD_CORNER_RADIUS - borderWidth,
        bottomStart = innerBottomCornerRadius,
        bottomEnd = innerBottomCornerRadius,
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = if (bottomCutOff) 0.dp else CARD_SHADOW_ELEVATION,
                shape = cardShape,
                clip = false,
                ambientColor = CARD_SHADOW_COLOR,
                spotColor = CARD_SHADOW_COLOR,
            )
            .background(
                color = outlineColor,
                shape = cardShape,
            )
            .padding(borderWidth)
            .clip(contentShape)
            .drawBehind {
                drawRect(color = faceColor)
                val panelWidthPx = panelWidth.toPx()
                drawRect(
                    color = panelColor,
                    topLeft = Offset(size.width - panelWidthPx, 0f),
                    size = Size(panelWidthPx, size.height),
                )
            }
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(role = Role.Button) {
                            onClick(data.id)
                        }
                        .semantics {
                            contentDescription = openLabel
                        }
                } else {
                    Modifier
                }
            )
            .testTag("eaaDigitalCard")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(
                        min = minHeight - borderWidth * 2
                    )
                    .padding(
                        start = SPACING_MEDIUM.dp,
                        top = SPACING_MEDIUM.dp,
                        end = SPACING_MEDIUM.dp,
                        bottom = if (bottomCutOff) SPACING_SMALL.dp else SPACING_MEDIUM.dp,
                    ),
                verticalArrangement = Arrangement.Top,
            ) {
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(
                    modifier = Modifier.size(2.dp)
                )

                Text(
                    text = data.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                if (footer != null) {
                    footer()
                }
            }

            Box(
                modifier = Modifier
                    .width(panelWidth)
                    .padding(top = LOGO_TOP_PADDING),
                contentAlignment = Alignment.TopCenter,
            ) {
                data.logoUri?.let { uri ->
                    WrapAsyncImage(
                        source = uri.toString(),
                        contentDescription = null,
                        modifier = Modifier.sizeIn(
                            maxWidth = dimensionResource(
                                R.dimen.eaa_logo_max_width
                            ),
                            maxHeight = dimensionResource(
                                R.dimen.eaa_logo_max_height
                            ),
                        ),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

/** Vertical nudge that lines the action's icon up with its label's optical center. */
private val ICON_OPTICAL_NUDGE = 1.dp

/**
 * The design's drop shadow for the card: offset y2, blur 8, #1D1D1E at 20%. Compose derives both
 * offset and blur from a single elevation, and 4.dp is the value that lands closest; the platform
 * applies its own ~20% alpha to an opaque spot color, so the color is passed opaque.
 */
private val CARD_CORNER_RADIUS = SPACING_MEDIUM.dp
private val CARD_SHADOW_ELEVATION = 4.dp
private val CARD_SHADOW_COLOR = Color(0xFF1D1D1E)

private val LOGO_TOP_PADDING = 11.dp

/**
 * The action a card can carry at the bottom of its white section, as the design draws it: a label
 * followed by a forward arrow, the whole row tappable.
 *
 * It lives next to [EaaDocumentCard] rather than in the screens that use it so every card-bound
 * action ("view data" on the issuance offer today) keeps the same label style, arrow and hit area.
 *
 * Accessibility: the row is merged and announced as a single button; the arrow is decorative and
 * hidden, since "arrow forward" adds nothing to the label already read out.
 */
@Composable
fun EaaDocumentCardAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable {
                onClick()
            }
            .semantics(
                mergeDescendants = true
            ) {
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            SPACING_SMALL.dp
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f, fill = false),
        )

        WrapIcon(
            iconData = AppIcons.ArrowRightLong,
            modifier = Modifier
                .size(SIZE_MEDIUM_LARGE.dp)
                .offset(y = ICON_OPTICAL_NUDGE)
                .clearAndSetSemantics { },
            customTint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun EaaDocumentCardPreview() {
    EaaDocumentCard(
        data = EaaCardData(
            id = "preview-doc",
            description = "University Diploma",
            name = "University of Berlin",
            backgroundColor = "#1A237E",
            backgroundImageUri = null,
            logoUri = null,
            validityDate = Instant.parse(
                "2030-05-13T14:25:00.073Z"
            ),
        ),
        onClick = {},
    )
}