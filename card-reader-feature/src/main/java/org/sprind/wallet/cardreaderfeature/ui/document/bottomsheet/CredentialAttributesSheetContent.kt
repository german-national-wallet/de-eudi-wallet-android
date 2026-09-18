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

package org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapStickySecondaryButton
import org.sprind.wallet.uilogic.component.CredentialAttribute
import org.sprind.wallet.uilogic.component.CredentialAttributeList

/** What the credential about to be issued holds, opened from the consent screen's details button. */
@Composable
fun CredentialAttributesSheetContent(
    attributes: List<CredentialAttribute>,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CredentialSheetHeader()
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        CredentialAttributeList(
            attributes = attributes,
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SPACING_MEDIUM.dp),
        )
        WrapStickySecondaryButton(
            text = stringResource(R.string.pid_issuance_digital_id_consent_sec_button),
            paddingValues = PaddingValues(),
            modifier = Modifier.padding(vertical = SPACING_MEDIUM.dp),
            onClick = onCloseClick,
        )
    }
}

@Composable
private fun CredentialSheetHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SPACING_MEDIUM.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
    ) {
        Text(
            text = stringResource(R.string.global_pid_credential_name),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.overview_initial_pid_teaser_pid_issuer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CredentialAttributesSheetContentPreview() {
    PreviewTheme {
        CredentialAttributesSheetContent(
            attributes = listOf(
                CredentialAttribute("Family name", "MUSTERMANN"),
                CredentialAttribute("Birth name", "GABLER"),
                CredentialAttribute("Given name(s)", "GABRIELA"),
                CredentialAttribute("Address", "HEIDESTRASSE 17\n51147 KÖLN\nDEUTSCHLAND"),
            ),
            onCloseClick = {},
        )
    }
}