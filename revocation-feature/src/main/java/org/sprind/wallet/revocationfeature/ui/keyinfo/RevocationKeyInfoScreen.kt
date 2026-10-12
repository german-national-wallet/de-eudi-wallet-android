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

package org.sprind.wallet.revocationfeature.ui.keyinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.Banner
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import org.sprind.wallet.revocationfeature.ui.RevocationCode
import org.sprind.wallet.revocationfeature.ui.shareText
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig

@Composable
fun RevocationKeyInfoScreen(
    navController: NavController,
    viewModel: RevocationKeyInfoViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    OneTimeLaunchedEffect { viewModel.setEvent(Event.Init) }

    Content(
        revocationCode = state.revocationCode,
        onBack = { navController.popBackStack() },
    )
}

@Composable
private fun Content(
    revocationCode: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    ContentScreen(onBack = onBack) { paddingValues ->
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_LARGE.dp),
            title = {
                Text(
                    text = stringResource(R.string.settings_revocation_key_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            body = {
                Text(
                    text = stringResource(R.string.settings_revocation_key_paragraph),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            extraContent = {
                Banner(
                    body = stringResource(R.string.settings_revocation_key_banner),
                    icon = AppIcons.Warning,
                )

                Column(verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { heading() },
                            text = stringResource(R.string.settings_revocation_key_headline_key),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        RevocationCode(revocationCode = revocationCode)
                    }

                    WrapButton(
                        modifier = Modifier.fillMaxWidth(),
                        buttonConfig = ButtonConfig(
                            enabled = revocationCode != null,
                            type = ButtonType.SECONDARY,
                            onClick = {
                                revocationCode?.let { shareText(context = context, text = it) }
                            },
                        ),
                    ) {
                        WrapIcon(
                            iconData = AppIcons.Share,
                            modifier = Modifier
                                .size(SIZE_MEDIUM_LARGE.dp)
                                .clearAndSetSemantics { },
                        )
                        Spacer(modifier = Modifier.width(SPACING_SMALL.dp))
                        Text(
                            text = stringResource(R.string.settings_revocation_key_sec_button),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            },
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun RevocationKeyInfoPreview() {
    PreviewTheme {
        Content(
            revocationCode = "rev1 dl5r vzak 5x72 6vht dgyf ra79 7n4x uzra w3ps t927 ytdp",
            onBack = {},
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun RevocationKeyInfoWithoutCodePreview() {
    PreviewTheme {
        Content(
            revocationCode = null,
            onBack = {},
        )
    }
}