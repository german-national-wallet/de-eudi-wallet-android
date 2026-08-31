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

package org.sprind.wallet.walletpinfeature.ui.document.pinset

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import org.sprind.wallet.walletpinfeature.ui.document.pinset.view.WalletPinEntryConfig
import org.sprind.wallet.walletpinfeature.ui.document.pinset.view.WalletPinEntryView
import org.sprind.wallet.walletpinfeature.ui.document.pinset.view.WalletPinSetInfoView
import org.sprind.wallet.walletpinfeature.ui.document.pinset.view.WalletPinSetSuccessView
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach


@Composable
fun WalletPinSetScreen(
    navController: NavController,
    viewModel: WalletPinSetViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> {
                    onNavigationRequested(navController, effect)
                }
            }
        }.collect()
    }

    Content(
        state = state,
        onEventSend = { viewModel.setEvent(it) },
    )

    BackHandler(
        onBack = {
            viewModel.setEvent(Event.OnDismissRequested)
        }
    )
}

private fun onNavigationRequested(
    navController: NavController,
    navigationEffect: Effect.Navigation,
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(IssuanceScreens.AddDocument.screenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }
    }
}

@Composable
private fun Content(
    state: State,
    onEventSend: (Event) -> Unit,
) {
    when (state.currentStep) {
        WalletPinStep.Info -> WalletPinSetInfoView(
            onCloseClick = { onEventSend(Event.OnCloseClicked) },
            onContinueClick = {
                onEventSend(Event.OnContinueAction)
            }
        )

        WalletPinStep.Set -> WalletPinEntryView(
            config = WalletPinEntryConfig(
                title = stringResource(R.string.pid_issuance_wallet_pin_setup_title),
                primaryButtonText = stringResource(R.string.pid_issuance_wallet_pin_setup_prim_button),
            ),
            state = state.pinState,
            onCodeChange = {
                onEventSend(Event.OnPinUpdate)
            },
            onPrimaryButtonClick = {
                onEventSend(Event.OnContinueAction)
            },
            onBack = {
                onEventSend(Event.OnDismissRequested)
            },
            onClose = { onEventSend(Event.OnCloseClicked) },
        )

        WalletPinStep.Confirm -> WalletPinEntryView(
            config = WalletPinEntryConfig(
                title = stringResource(R.string.pid_issuance_wallet_pin_reenter_title),
                primaryButtonText = stringResource(R.string.pid_issuance_wallet_pin_reenter_prim_button),
                isLoading = state.isLoading,
                errorDialog = state.errorDialog,
            ),
            state = state.pinState,
            onCodeChange = {
                onEventSend(Event.OnPinConfirmUpdate)
            },
            onPrimaryButtonClick = {
                onEventSend(Event.OnWalletPinSet)
            },
            onBack = {
                onEventSend(Event.OnDismissRequested)
            },
            onClose = { onEventSend(Event.OnCloseClicked) },
        )

        WalletPinStep.Success -> {
            WalletPinSetSuccessView(state.isLoading)
        }
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun WalletPinSetInfoPreview() {
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                currentStep = WalletPinStep.Info,
            ),
            onEventSend = { },
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun WalletPinSetPreview() {
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                currentStep = WalletPinStep.Set,
            ),
            onEventSend = { },
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun WalletPinSetConfirmPreview() {
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                currentStep = WalletPinStep.Confirm,
            ),
            onEventSend = { },
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun WalletPinSetSuccessPreview() {
    PreviewTheme {
        Content(
            state = State(
                isLoading = false,
                currentStep = WalletPinStep.Success,
            ),
            onEventSend = { },
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun WalletPinSetWithErrorDialogPreview() {
    PreviewTheme {
        Content(
            state = State(
                currentStep = WalletPinStep.Confirm,
                isLoading = false,
                errorDialog = GenericErrorDialogConfig(
                    titleRes = R.string.global_error_title,
                    bodyTextRes = R.string.global_error_paragraph,
                    errorCode = "UNKNOWN",
                    traceId = "34iu234082034u02934j23o420394j32",
                    primaryButtonTextRes = R.string.global_error_prim_button,
                    onDismiss = { },
                    onPrimaryButtonClick = { }
                ),
            ),

            onEventSend = { },
        )
    }
}