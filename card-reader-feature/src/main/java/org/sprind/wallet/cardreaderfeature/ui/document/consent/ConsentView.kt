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

@file:OptIn(ExperimentalMaterial3Api::class)

package org.sprind.wallet.cardreaderfeature.ui.document.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.BulletPointText
import eu.europa.ec.uilogic.component.ClickableArea
import eu.europa.ec.uilogic.component.ListItem
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapExpandableCard
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.EidPinBottomSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.read.ReadCardBottomSheetConfig
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle

/**
 * The data release the flow asks for before reading the card: what will be read, and from whom the
 * digital ID comes.
 *
 * The answers live in the flow's sticky bottom bar, see
 * [org.sprind.wallet.cardreaderfeature.ui.document.read.CardReaderStickyButtons].
 */
@Composable
fun ConsentView(
    modifier: Modifier,
    claimLabels: List<String>,
    bottomCardSheetConfig: ReadCardBottomSheetConfig,
    onSetCardPinButtonClick: () -> Unit,
    onSearchCitizenOfficeButtonClick: () -> Unit,
    onIssuerTitleClick: () -> Unit,
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleMedium,
            bodyTextStyle = MaterialTheme.typography.labelLarge,
        ),
    ) {
        ContentTemplateBody(
            modifier = modifier,
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_MEDIUM.dp),
            title = { Text(text = stringResource(R.string.pid_issuance_data_consent_title)) },
            body = { Text(text = stringResource(R.string.pid_issuance_data_consent_headline_credential)) },
            extraContent = {
                ClaimsCard(claimLabels = claimLabels)
                IssuerSection(onIssuerTitleClick = onIssuerTitleClick)
            },
        )
    }

    if (bottomCardSheetConfig.isBottomSheetOpen) {
        WrapModalBottomSheet(
            onDismissRequest = { bottomCardSheetConfig.onBottomSheetDismissRequest() },
            sheetState = bottomCardSheetConfig.sheetState
        ) {
            EidPinBottomSheetContent(
                onSetCardPinButtonClick = onSetCardPinButtonClick,
                onSearchCitizenOfficeButtonClick = onSearchCitizenOfficeButtonClick,
            )
        }
    }
}

/**
 * What will be read from the card. The first [COLLAPSED_CLAIM_COUNT] are always listed and the rest
 * follow once the card is expanded.
 */
@Composable
private fun ClaimsCard(claimLabels: List<String>) {
    var isExpanded by remember { mutableStateOf(false) }
    val collapsed = claimLabels.take(COLLAPSED_CLAIM_COUNT)
    val rest = claimLabels.drop(COLLAPSED_CLAIM_COUNT)
    val toggleLabel = stringResource(
        if (isExpanded) R.string.global_show_less_button else R.string.global_show_more_button
    )
    val cardName = stringResource(R.string.pid_issuance_data_consent_headline_credential)
    val expansionState = stringResource(
        if (isExpanded) {
            R.string.content_description_expanded
        } else {
            R.string.content_description_collapsed
        }
    )

    WrapExpandableCard(
        // The card is the control: it says whether it is expanded and what tapping it does, while
        // the claims stay readable one by one below it.
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                collectionInfo = CollectionInfo(
                    rowCount = if (isExpanded) claimLabels.size else collapsed.size,
                    columnCount = 1,
                )
                // The claims below carry their own semantics, so nothing merges into the card and it
                // needs a name of its own to be more than "collapsed".
                contentDescription = cardName
                stateDescription = expansionState
                onClick(label = toggleLabel) {
                    isExpanded = !isExpanded
                    true
                }
            },
        isExpanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded },
        shape = RoundedCornerShape(SPACING_MEDIUM.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        cardCollapsedContent = { Claims(labels = collapsed, firstIndex = 0) },
        cardExpandedContent = { Claims(labels = rest, firstIndex = collapsed.size) },
        cardFooterContent = {
            if (rest.isNotEmpty()) {
                ExpandClaimsButton(
                    isExpanded = isExpanded,
                    onClick = { isExpanded = !isExpanded },
                )
            }
        },
    )
}

@Composable
private fun Claims(
    labels: List<String>,
    firstIndex: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_EXTRA_MEDIUM.dp)
            .padding(top = SPACING_EXTRA_MEDIUM.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    collectionItemInfo = CollectionItemInfo(
                        rowIndex = firstIndex + index,
                        rowSpan = 1,
                        columnIndex = 0,
                        columnSpan = 1,
                    )
                }
            ) {
                BulletPointText(text = label)
            }
        }
    }
}

@Composable
private fun ExpandClaimsButton(
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    WrapButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = SPACING_SMALL.dp,
                start = 0.dp,
                bottom = SPACING_SMALL.dp,
                end = SPACING_SMALL.dp
            )
            // Repeats the card's own action, so it is kept out of the accessibility tree.
            .semantics { hideFromAccessibility() },
        buttonConfig = ButtonConfig(
            type = ButtonType.TEXT,
            onClick = onClick,
        ),
    ) {
        TextAndIcon(
            textValue = stringResource(
                if (isExpanded) R.string.global_show_less_button else R.string.global_show_more_button
            ),
            textConfig = TextConfig(
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            leftIconData = if (isExpanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown,
            customTint = MaterialTheme.colorScheme.onSurface,
            horizontalArrangement = Arrangement.Start,
        )
    }
}

/** Who issues the digital ID, and the way to its details. */
@Composable
private fun IssuerSection(onIssuerTitleClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
    ) {
        Text(
            text = stringResource(R.string.pid_issuance_data_consent_label_issued_by),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ListItem(
            item = ListItemData(
                itemId = "issuer",
                leadingContentData = ListItemLeadingContentData.Image(
                    imageRes = R.drawable.bundesdruckerei_logo_squared,
                ),
                mainContentData = ListItemMainContentData.Text(ISSUER_NAME),
            ),
            onItemClick = { onIssuerTitleClick() },
            leadingContentScale = ContentScale.Crop,
            clickableAreas = listOf(ClickableArea.ENTIRE_ROW),
        )
    }
}

private const val COLLAPSED_CLAIM_COUNT = 6

// TODO WD-4133: taken from the certificate once the consent carries the issuer it was issued for.
private const val ISSUER_NAME = "Bundesdruckerei"

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ConsentViewPreview() {
    PreviewTheme {
        ConsentView(
            modifier = Modifier.fillMaxSize(),
            claimLabels = listOf(
                "Family name",
                "Birth name",
                "Given name(s)",
                "Doctoral degree",
                "Religous/artistic name",
                "Address",
                "Nationality",
                "Date of birth",
            ),
            bottomCardSheetConfig = ReadCardBottomSheetConfig(
                title = "",
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                isBottomSheetOpen = false,
                onBottomSheetDismissRequest = {},
            ),
            onSetCardPinButtonClick = {},
            onSearchCitizenOfficeButtonClick = {},
            onIssuerTitleClick = {},
        )
    }
}