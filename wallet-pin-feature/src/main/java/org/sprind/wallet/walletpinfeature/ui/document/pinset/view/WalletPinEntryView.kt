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

package org.sprind.wallet.walletpinfeature.ui.document.pinset.view

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.wrap.WrapStickyPrimaryButton
import org.sprind.wallet.uilogic.component.CodeEntryBody
import org.sprind.wallet.uilogic.component.CodeEntryState
import org.sprind.wallet.uilogic.component.CodeLength
import org.sprind.wallet.uilogic.component.codeEntryStateForPreview

/**
 * The parts of a wallet-PIN entry screen that change between the "choose a PIN" and "confirm your
 * PIN" steps. Grouped rather than passed individually to keep the composable's parameter list
 * within the project's limit.
 *
 * @property title the screen headline.
 * @property primaryButtonText label of the confirming button.
 * @property isLoading whether the screen is busy; the toolbar loses its back and close actions
 *   while it is, so the user cannot abandon a half-finished registration.
 * @property errorDialog an error to surface over the screen, or `null`.
 */
@Immutable
data class WalletPinEntryConfig(
    val title: String,
    val primaryButtonText: String,
    val isLoading: Boolean = false,
    val errorDialog: GenericErrorDialogConfig? = null,
)

/**
 * The wallet-PIN entry screen, used for both steps of setting a PIN.
 *
 * This replaces the pair of near-identical screens the flow used to carry — `PinSetView` for the
 * first entry and `WalletPinSetConfirmView` for the second — which differed only in their title,
 * their button label, and whether they carried loading and error state. All three are now
 * [WalletPinEntryConfig].
 *
 * Per the design the PIN is masked with an eye to reveal it, and there is no hero illustration:
 * the phone-security image the old screens showed was decoration rather than instruction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletPinEntryView(
    config: WalletPinEntryConfig,
    state: CodeEntryState,
    onCodeChange: () -> Unit,
    onPrimaryButtonClick: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit = {},
) {
    val loadingToolbar: (@Composable () -> Unit)? = if (config.isLoading) {
        {
            // An empty bar while busy, so there is no back or close affordance to interrupt
            // registration with.
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    } else {
        null
    }

    ContentScreen(
        isLoading = config.isLoading,
        topBar = loadingToolbar,
        navigatableAction = if (config.isLoading) {
            ScreenNavigateAction.NONE
        } else {
            ScreenNavigateAction.BACKABLE
        },
        onBack = onBack,
        genericErrorDialogConfig = config.errorDialog,
        toolBarConfig = ToolbarConfig(
            title = "",
            actions = listOf(ToolbarAction(icon = AppIcons.Close, onClick = onClose)),
        ),
        stickyBottom = { paddingValues ->
            WrapStickyPrimaryButton(
                text = config.primaryButtonText,
                enabled = state.isValid,
                paddingValues = paddingValues,
                onClick = onPrimaryButtonClick,
            )
        },
    ) { paddingValues ->
        CodeEntryBody(
            modifier = Modifier.padding(paddingValues),
            title = config.title,
            state = state,
            onCodeChange = onCodeChange,
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WalletPinEntrySetPreview() {
    PreviewTheme {
        WalletPinEntryView(
            config = WalletPinEntryConfig(
                title = stringResource(R.string.pid_issuance_wallet_pin_setup_title),
                primaryButtonText = stringResource(R.string.pid_issuance_wallet_pin_setup_prim_button),
            ),
            state = codeEntryStateForPreview(capacity = CodeLength.WALLET_PIN, code = "123"),
            onCodeChange = {},
            onPrimaryButtonClick = {},
            onBack = {},
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WalletPinEntryConfirmPreview() {
    PreviewTheme {
        WalletPinEntryView(
            config = WalletPinEntryConfig(
                title = stringResource(R.string.pid_issuance_wallet_pin_reenter_title),
                primaryButtonText = stringResource(R.string.pid_issuance_wallet_pin_reenter_prim_button),
            ),
            state = codeEntryStateForPreview(capacity = CodeLength.WALLET_PIN, code = "123456"),
            onCodeChange = {},
            onPrimaryButtonClick = {},
            onBack = {},
        )
    }
}
