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

package org.sprind.wallet.revocationfeature.ui

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.CheckboxWithContent
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.CheckboxData
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.ContentTemplateBody

@Composable
fun RevocationSaveCodeScreen(
    navController: NavController,
    viewModel: RevocationSaveCodeViewModel,
) {

    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    Content(
        revocationCode = state.revocationCode,
        savedCodeConfirmation = state.savedCodeConfirmation,
        onUserHasSavedCodeChanged = {
            viewModel.setEvent(Event.OnUserHasSavedCodeChanged(value = it))
        },
        onNext = {
            navController.navigate(ModuleRoute.DashboardModule.route) {
                popUpTo(ModuleRoute.RevocationModule.route)
            }
        },
        onBack = { navController.popBackStack() }
    )
}

@Composable
private fun Content(
    revocationCode: String?,
    savedCodeConfirmation: Boolean,
    onUserHasSavedCodeChanged: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    ContentScreen(
        onBack = onBack,
        stickyBottom = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                CheckboxWithContent(
                    checkboxData = CheckboxData(
                        isChecked = savedCodeConfirmation,
                        onCheckedChange = onUserHasSavedCodeChanged
                    )
                ) {
                    Text(
                        text = stringResource(R.string.app_onboarding_wallet_revocation_save_key_checkbox_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                WrapButton(
                    buttonConfig = ButtonConfig(
                        enabled = savedCodeConfirmation,
                        type = ButtonType.PRIMARY,
                        onClick = onNext
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    TextAndIcon(
                        modifier = Modifier,
                        iconModifier = Modifier.size(SIZE_MEDIUM_LARGE.dp),
                        textModifier = Modifier.padding(end = SPACING_SMALL.dp),
                        textValue = stringResource(R.string.app_onboarding_wallet_revocation_save_key_prim_button),
                        textConfig = TextConfig(
                            style = ThemeTextStyles.onPrimaryButton,
                            color = ThemeColors.onPrimaryButton,
                        ),
                        customTint = MaterialTheme.colorScheme.onPrimary,
                        rightIconData = AppIcons.ArrowForward,
                    )
                }
            }
        },
    ) { paddingValues ->
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            title = {
                Text(
                    text = stringResource(R.string.app_onboarding_wallet_revocation_save_key_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            extraContent = {
                Column(verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth(),
                        text = stringResource(R.string.app_onboarding_wallet_revocation_save_key_headline_1),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    RevocationCode(revocationCode)
                    WrapButton(
                        modifier = Modifier.fillMaxWidth(),
                        buttonConfig = ButtonConfig(
                            enabled = revocationCode != null,
                            type = ButtonType.SECONDARY,
                            onClick = {
                                revocationCode?.let {
                                    shareText(context = context, text = revocationCode)
                                }
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
                            text = stringResource(R.string.app_onboarding_wallet_revocation_save_key_button_3),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            },
        )
    }
}

fun shareText(context: Context, text: String) {
    val sendIntent: Intent = Intent().apply {
        action = ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }

    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun RevocationSaveCodePreview() {
    PreviewTheme {
        Content(
            revocationCode =
                "rev1 dl5r vzak 5x72 6vht dgyf ra79 7n4x uzra w3ps t927 ytdp e5s7 g2us 8xra ur ",
            savedCodeConfirmation = false,
            onUserHasSavedCodeChanged = { },
            onNext = { },
            onBack = { },
        )
    }
}
