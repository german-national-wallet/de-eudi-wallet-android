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

package org.sprind.wallet.cardreaderfeature.ui.document.issuance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.WEIGHT_1
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import org.sprind.wallet.uilogic.component.ContentIllustrationPlacement
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.DocumentCard
import org.sprind.wallet.uilogic.component.NavigationTopAction
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle
import org.sprind.wallet.uilogic.component.TopActionRow

@Composable
fun IssuanceConsentView(
    issuerName: String,
    onShowCredentialDataClick: () -> Unit,
    onIssuerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleLarge,
        ),
    ) {
        ContentTemplateBody(
            modifier = modifier,
            templateConfig = ContentTemplateConfig(
                verticalSpacing = SPACING_MEDIUM.dp,
                illustrationPlacement = ContentIllustrationPlacement.BELOW_TEXT,
            ),
            title = { Text(text = stringResource(R.string.pid_issuance_add_credential_title)) },
            illustration = { DocumentCard(modifier = Modifier.fillMaxWidth()) },
            extraContent = {
                Column(verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_MEDIUM.dp)) {
                    TopActionRow(
                        action = NavigationTopAction(
                            text = stringResource(R.string.pid_issuance_add_credential_show_data_button),
                            icon = AppIcons.Visibility,
                            onClick = onShowCredentialDataClick,
                        ),
                    )
                    IssuerSection(name = issuerName, onClick = onIssuerClick)
                }
            },
        )
    }
}

@Composable
private fun IssuerSection(
    name: String,
    onClick: () -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.pid_issuance_add_credential_issuer_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        IssuerRow(name = name, onClick = onClick)
    }
}

@Composable
private fun IssuerRow(
    name: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(onClick = onClick, onClickLabel = name)
            .heightIn(min = ISSUER_ROW_MIN_HEIGHT)
            .padding(vertical = SPACING_MEDIUM.dp),
        horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(ISSUER_MARK_SIZE),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Box(contentAlignment = Alignment.Center) {
                WrapIcon(
                    iconData = AppIcons.Building,
                    modifier = Modifier.size(SIZE_LARGE.dp),
                    customTint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            modifier = Modifier.weight(WEIGHT_1),
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val ISSUER_ROW_MIN_HEIGHT = 48.dp
private val ISSUER_MARK_SIZE = 40.dp

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun IssuanceConsentViewPreview() {
    PreviewTheme {
        IssuanceConsentView(
            modifier = Modifier
                .fillMaxSize()
                .padding(SPACING_MEDIUM.dp),
            issuerName = "Bundesministerium des Innern",
            onShowCredentialDataClick = {},
            onIssuerClick = {},
        )
    }
}