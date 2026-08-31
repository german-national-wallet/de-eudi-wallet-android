/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.issuancefeature.ui.document.code

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.wrap.WrapStickyPrimaryButton
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.designsystem.typography.CustomTypography
import org.sprind.wallet.uilogic.component.CodeEntryBody
import org.sprind.wallet.uilogic.component.CodeEntryConfig
import org.sprind.wallet.uilogic.component.codeEntryStateForPreview
import org.sprind.wallet.uilogic.component.CodeVisibility
import org.sprind.wallet.walletpinfeature.ui.document.pinset.view.WalletPinSetSuccessView

@Composable
fun DocumentOfferCodeScreen(
    navController: NavController,
    viewModel: DocumentOfferCodeViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> handleNavigationEffect(effect, navController)
            }
        }.collect()
    }

    ContentScreen(
        isLoading = state.isLoading,
        contentErrorConfig = state.error,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { navController.popBackStack() },
        toolBarConfig = ToolbarConfig(
            title = "",
            actions = listOf(
                ToolbarAction(
                    icon = AppIcons.Close,
                    onClick = { navController.popBackStack() }
                )
            )
        ),
        stickyBottom = { paddingValues ->
            if (!state.isSuccess) {
                WrapStickyPrimaryButton(
                    text = stringResource(R.string.eaa_issuance_transaction_code_entry_prim_button),
                    enabled = state.codeState.isValid,
                    paddingValues = paddingValues,
                    onClick = {
                        viewModel.setEvent(
                            Event.OnSendData(navController.context)
                        )
                    },
                )
            }
        },
        bodyContent = { paddingValues ->
            when {
                state.isSuccess -> WalletPinSetSuccessView(
                    title = stringResource(R.string.eaa_issuance_success_title),
                    isLoading = false,
                )

                else -> CodeEntryBody(
                    modifier = Modifier.padding(paddingValues),
                    title = stringResource(R.string.eaa_issuance_transaction_code_entry_title),
                    state = state.codeState,
                    onCodeChange = { viewModel.setEvent(Event.OnPinChange) },
                    config = CodeEntryConfig(visibility = CodeVisibility.ALWAYS_VISIBLE),
                    titleTextStyle = CustomTypography.titleMedium,
                )
            }
        }
    )
}


private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(IssuanceScreens.DocumentOfferCode.screenRoute) {
                    inclusive = true
                }
            }
        }

        is Effect.Navigation.Pop -> {
            navController.popBackStack()
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun DocumentOfferCodeScreenEmptyPreview() {
    PreviewTheme {
        ContentScreen(
            navigatableAction = ScreenNavigateAction.BACKABLE,
            onBack = {},
            stickyBottom = { paddingValues ->
                WrapStickyPrimaryButton(
                    text = stringResource(R.string.eaa_issuance_transaction_code_entry_prim_button),
                    enabled = false,
                    paddingValues = paddingValues,
                    onClick = {},
                )
            },
        ) { paddingValues ->
            CodeEntryBody(
                modifier = Modifier.padding(paddingValues),
                title = stringResource(R.string.eaa_issuance_transaction_code_entry_title),
                state = codeEntryStateForPreview(capacity = 6),
                onCodeChange = {},
                config = CodeEntryConfig(
                    visibility = CodeVisibility.ALWAYS_VISIBLE,
                    focusOnCreate = false,
                ),
                titleTextStyle = CustomTypography.titleMedium,
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun DocumentOfferCodeScreenFilledPreview() {
    PreviewTheme {
        ContentScreen(
            navigatableAction = ScreenNavigateAction.BACKABLE,
            onBack = {},
            stickyBottom = { paddingValues ->
                WrapStickyPrimaryButton(
                    text = stringResource(R.string.eaa_issuance_transaction_code_entry_prim_button),
                    enabled = true,
                    paddingValues = paddingValues,
                    onClick = {},
                )
            },
        ) { paddingValues ->
            CodeEntryBody(
                modifier = Modifier.padding(paddingValues),
                title = stringResource(R.string.eaa_issuance_transaction_code_entry_title),
                state = codeEntryStateForPreview(capacity = 6, code = "141131"),
                onCodeChange = {},
                config = CodeEntryConfig(
                    visibility = CodeVisibility.ALWAYS_VISIBLE,
                    focusOnCreate = false,
                ),
                titleTextStyle = CustomTypography.titleMedium,
            )
        }
    }
}
