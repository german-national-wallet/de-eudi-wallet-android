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

package org.sprind.wallet.dashboardfeature.ui.settings.debugconfig

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.Banner
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapTextField
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig

@Composable
fun DebugConfigScreen(
    viewModel: DebugConfigViewModel,
    navController: NavController,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    Content(
        overrides = state.overrides,
        showApplyingDialog = state.showApplyingDialog,
        onWalletHostUrlChanged = { viewModel.setEvent(Event.OnWalletHostUrlChanged(it)) },
        onWalletApiKeyChanged = { viewModel.setEvent(Event.OnWalletApiKeyChanged(it)) },
        onOtlpHostUrlChanged = { viewModel.setEvent(Event.OnOtlpHostUrlChanged(it)) },
        onOtlpAuthTokenChanged = { viewModel.setEvent(Event.OnOtlpAuthTokenChanged(it)) },
        onPidProviderUrlChanged = { viewModel.setEvent(Event.OnPidProviderUrlChanged(it)) },
        onReset = { viewModel.setEvent(Event.OnReset) },
        onApply = { viewModel.setEvent(Event.OnApply) },
        onApplyKeepWalletData = { viewModel.setEvent(Event.OnApplyKeepWalletData) },
        onApplyDeleteWalletData = { viewModel.setEvent(Event.OnApplyDeleteWalletData) },
        onDismissApplyingDialog = { viewModel.setEvent(Event.OnDismissApplyingDialog) },
        onBack = { navController.popBackStack() }
    )
    val activity = LocalActivity.current
    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                Effect.Navigation.Restart -> with(activity ?: return@onEach) {
                    startActivity(Intent.makeRestartActivityTask(componentName))
                }
            }
        }.collect()
    }
}

@Composable
private fun Content(
    overrides: DebugConfigOverridesState,
    showApplyingDialog: Boolean,
    onWalletHostUrlChanged: (String) -> Unit,
    onWalletApiKeyChanged: (String) -> Unit,
    onOtlpHostUrlChanged: (String) -> Unit,
    onOtlpAuthTokenChanged: (String) -> Unit,
    onPidProviderUrlChanged: (String) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onApplyKeepWalletData: () -> Unit,
    onApplyDeleteWalletData: () -> Unit,
    onDismissApplyingDialog: () -> Unit,
    onBack: () -> Unit,
) {
    ContentScreen(
        onBack = onBack,
        genericErrorDialogConfig = GenericErrorDialogConfig(
            iconData = AppIcons.Info,
            titleRes = R.string.settings_dialog_debug_config_title,
            bodyTextRes = R.string.settings_dialog_debug_config_paragraph,
            primaryButtonTextRes = R.string.settings_dialog_debug_config_prim_button,
            secondaryButtonTextRes = R.string.settings_dialog_debug_config_sec_button,
            onDismiss = onDismissApplyingDialog,
            onPrimaryButtonClick = onApplyKeepWalletData,
            onSecondaryButtonClick = onApplyDeleteWalletData,
        ).takeIf { showApplyingDialog },
    ) { paddingValues ->
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_LARGE.dp),
            title = {
                Text(
                    text = stringResource(R.string.settings_debug_config_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            body = {
                Banner(
                    body = stringResource(R.string.settings_debug_config_banner),
                )
            },
            extraContent = {

                Column(verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)) {
                    WrapTextField(
                        value = overrides.walletHostUrl,
                        onValueChange = onWalletHostUrlChanged,
                        label = { Text(stringResource(R.string.settings_debug_config_input_backend_host)) },
                        placeholder = { Text(stringResource(R.string.settings_debug_config_input_backend_host_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    WrapTextField(
                        value = overrides.walletApiKey,
                        onValueChange = onWalletApiKeyChanged,
                        label = { Text(stringResource(R.string.settings_debug_config_input_api_key)) },
                        placeholder = { Text(stringResource(R.string.settings_debug_config_input_api_key_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    WrapTextField(
                        value = overrides.otlpHostUrl,
                        onValueChange = onOtlpHostUrlChanged,
                        label = { Text(stringResource(R.string.settings_debug_config_input_telemetry_host)) },
                        placeholder = { Text(stringResource(R.string.settings_debug_config_input_telemetry_host_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    WrapTextField(
                        value = overrides.otlpAuthToken,
                        onValueChange = onOtlpAuthTokenChanged,
                        label = { Text(stringResource(R.string.settings_debug_config_input_telemetry_token)) },
                        placeholder = { Text(stringResource(R.string.settings_debug_config_input_telemetry_token_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    WrapTextField(
                        value = overrides.pidProviderUrl,
                        onValueChange = onPidProviderUrlChanged,
                        label = { Text(stringResource(R.string.settings_debug_config_input_pid_provider)) },
                        placeholder = { Text(stringResource(R.string.settings_debug_config_input_pid_provider_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    WrapButton(
                        modifier = Modifier.fillMaxWidth(),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.PRIMARY,
                            onClick = onApply,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_debug_config_prim_button),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    WrapButton(
                        modifier = Modifier.fillMaxWidth(),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.SECONDARY,
                            onClick = onReset,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_debug_config_sec_button),
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
private fun DebugConfigScreenPreview() {
    PreviewTheme {
        Content(
            overrides = DebugConfigOverridesState(
                walletHostUrl = "",
                walletApiKey = "",
                otlpHostUrl = "",
                otlpAuthToken = "",
                pidProviderUrl = "",
            ),
            showApplyingDialog = false,
            onWalletHostUrlChanged = { },
            onWalletApiKeyChanged = { },
            onOtlpHostUrlChanged = { },
            onOtlpAuthTokenChanged = { },
            onPidProviderUrlChanged = { },
            onReset = { },
            onApply = { },
            onApplyKeepWalletData = { },
            onApplyDeleteWalletData = { },
            onDismissApplyingDialog = { },
            onBack = { },
        )
    }
}
