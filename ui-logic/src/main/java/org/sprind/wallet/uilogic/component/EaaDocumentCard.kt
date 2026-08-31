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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.businesslogic.util.formatInstantToDateString
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.parseCssColor
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapAsyncImage
import java.time.Instant
import java.util.Locale

/**
 * Displays an EAA (Electronic Attestation of Attributes) document card using the
 * final Figma design: a two-region layout with a white left section (issuer name,
 * credential type, validity badge) and a colored right panel (issuer logo).
 *
 * Falls back gracefully:
 *  - No background color metadata -> [ThemeColors.defaultEaa]
 *  - No logo URI -> colored panel renders without a logo
 *  - No validity date -> validity badge is hidden
 *
 * Accessibility:
 *  - Logo contentDescription = issuer name
 *  - Badge contentDescription = "Valid until <date>" or "Expired"
 *  - When [onClick] is non-null: card semantics label = "Open <credentialName>"
 *  - When [onClick] is null: card is non-interactive (no ripple, no click
 *    semantics); child Text nodes announce themselves naturally.
 *
 * @param modifier Modifier for the card
 * @param data The [EaaCardData] to render
 * @param onClick Callback invoked when card is clicked, passing the document ID.
 *   Pass null (the default) for an informational, non-interactive card.
 */
@Composable
fun EaaDocumentCard(
    modifier: Modifier = Modifier,
    data: EaaCardData,
    onClick: ((String) -> Unit)? = null,
) {
    val backgroundColor = parseCssColor(data.backgroundColor, ThemeColors.defaultEaa)
    val openLabel = stringResource(R.string.eaa_card_open, data.description)
    val cardModifier = modifier
        .height(dimensionResource(R.dimen.eaa_card_height))
        .border(
            width = BORDER_STROKE_1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp)
        )
        .testTag("eaaDigitalCard")
        .let {
            if (onClick != null) it.semantics { contentDescription = openLabel } else it
        }
    val cardColors = CardDefaults.cardColors(containerColor = ThemeColors.surfaceBrightest)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    val cardShape = RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp)

    if (onClick != null) {
        Card(
            modifier = cardModifier,
            onClick = { onClick(data.id) },
            colors = cardColors,
            elevation = cardElevation,
            shape = cardShape
        ) {
            EaaDocumentCardContent(data = data, backgroundColor = backgroundColor)
        }
    } else {
        Card(
            modifier = cardModifier,
            colors = cardColors,
            elevation = cardElevation,
            shape = cardShape
        ) {
            EaaDocumentCardContent(data = data, backgroundColor = backgroundColor)
        }
    }
}

@Composable
private fun EaaDocumentCardContent(
    data: EaaCardData,
    backgroundColor: Color,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Left section: fills remaining width after the colored panel.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .padding(SPACING_EXTRA_MEDIUM.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = data.description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = data.name,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            ValidityBadge(validityDate = data.validityDate)
        }

        // Right section: colored panel, fixed width.
        Box(
            modifier = Modifier
                .width(dimensionResource(R.dimen.eaa_colored_panel_width))
                .fillMaxHeight()
                .background(backgroundColor),
            contentAlignment = Alignment.TopCenter
        ) {
            data.logoUri?.let { uri ->
                WrapAsyncImage(
                    source = uri.toString(),
                    contentDescription = data.name,
                    modifier = Modifier
                        .padding(top = SPACING_EXTRA_MEDIUM.dp)
                        .size(
                            width = dimensionResource(R.dimen.eaa_logo_width),
                            height = dimensionResource(R.dimen.eaa_logo_height),
                        ),
                    placeholder = AppIcons.Id,
                    error = AppIcons.Id,
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/**
 * Renders the validity badge as a gray pill with a check icon and localized text.
 *
 * Shows "Valid until DD.MM.YYYY" when [validityDate] is in the future, "Expired"
 * when it is in the past, and renders nothing when [validityDate] is null.
 */
@Composable
private fun ValidityBadge(
    validityDate: Instant?,
    locale: Locale = Locale.getDefault(),
) {
    validityDate ?: return
    val now = Instant.now()
    val isExpired = validityDate.isBefore(now)
    val text = if (isExpired) {
        stringResource(R.string.dashboard_document_has_expired)
    } else {
        stringResource(
            R.string.dashboard_document_has_not_expired,
            validityDate.formatInstantToDateString(locale)
        )
    }
    val badgeContentDescription = if (isExpired) {
        stringResource(R.string.dashboard_document_has_expired)
    } else {
        stringResource(R.string.dashboard_document_has_not_expired, validityDate.toString())
    }
    Row(
        modifier = Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = SPACING_SMALL.dp, vertical = 2.dp)
            .semantics { contentDescription = badgeContentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            painter = painterResource(id = AppIcons.Check.resourceId ?: 0),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 9.sp,
                lineHeight = 11.sp,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            maxLines = 1,
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
            validityDate = Instant.parse("2030-05-13T14:25:00.073Z"),
        ),
        onClick = {},
    )
}