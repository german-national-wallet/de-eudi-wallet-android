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

package org.sprind.wallet.cardreaderfeature.ui.document.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import org.sprind.wallet.uilogic.component.ContentIllustrationPlacement
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.EligibleDocument
import org.sprind.wallet.uilogic.component.EligibleDocumentRow
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle

@Composable
fun OnboardingCardView(
    modifier: Modifier,
) {
    val documents = listOf(
        EligibleDocument(
            artwork = AppIcons.NationalIdCard,
            name = stringResource(R.string.pid_issuance_onboarding_cards_list_1),
        ),
        EligibleDocument(
            artwork = AppIcons.ResidencePermitCard,
            name = stringResource(R.string.pid_issuance_onboarding_cards_list_2),
        ),
    )

    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleLarge,
        ),
    ) {
        ContentTemplateBody(
            modifier = modifier,
            templateConfig = ContentTemplateConfig(
                verticalSpacing = SPACING_LARGE_32.dp,
                illustrationPlacement = ContentIllustrationPlacement.BELOW_TEXT,
            ),
            title = { Text(text = stringResource(R.string.pid_issuance_onboarding_cards_title)) },
            extraContent = { EligibleDocuments(documents = documents) },
        )
    }
}


@Composable
private fun EligibleDocuments(modifier: Modifier = Modifier, documents: List<EligibleDocument>) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                collectionInfo = CollectionInfo(rowCount = documents.size, columnCount = 1)
            },
        verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp),
    ) {
        documents.forEachIndexed { index, document ->
            EligibleDocumentRow(document = document, index = index)
        }
    }
}


@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun OnboardingCardContentPreview() {
    PreviewTheme {
        OnboardingCardView(
            modifier = Modifier.fillMaxSize()
        )
    }
}