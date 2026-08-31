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

package org.sprind.wallet.cardreaderfeature.ui.document.read

import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialog
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialogConfig
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute
import org.sprind.wallet.cardreaderfeature.domain.isNfcPrompt
import java.util.Locale

/**
 * Renders the card-reader flow as route-specific destinations while keeping a
 * single shared [State] and event sink.
 *
 * The nested NavHost is driven from [State.currentRoute]. Screen-to-screen
 * transitions should be expressed by updating state in the ViewModel rather
 * than navigating directly from child composables.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardReaderRouteHost(
    state: State,
    onEventSend: (Event) -> Unit,
) {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val routeNavController = rememberNavController()
    val currentBackStackEntry by routeNavController.currentBackStackEntryAsState()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.currentRoute, currentBackStackEntry?.destination?.route) {
        val currentDestination = currentBackStackEntry?.destination?.route ?: return@LaunchedEffect
        val destination = state.currentRoute.navRoute()
        if (currentDestination == destination) return@LaunchedEffect
        // The nested host mirrors a single logical instance of each reader
        // route. Re-entering an existing destination reuses that entry instead
        // of pushing duplicates onto the child NavHost back stack.
        if (routeNavController.popBackStack(destination, inclusive = false)) return@LaunchedEffect
        routeNavController.navigate(destination) {
            launchSingleTop = true
        }
    }

    ContentScreen(
        navigatableAction = if (state.isLoading) ScreenNavigateAction.NONE else ScreenNavigateAction.BACKABLE,
        topBar = {
            CardReaderToolbar(
                state = state,
                onBackEvent = { onEventSend(Event.Pop) },
                onHelpEvent = { onEventSend(Event.BottomSheet.UpdateBottomSheetState(isOpen = true)) },
                onCloseEvent = { onEventSend(Event.OnCloseButtonClick) }
            )
        },
        genericErrorDialogConfig = state.errorDialog,
        isLoading = state.isLoading,
        // Only show the read percentage while an NFC prompt is active, i.e. while
        // the card is actually being read. The SDK also emits progress during the
        // initial authentication handshake, which would otherwise briefly flash a
        // percentage on the startup loader.
        loadingSubText = state.readingProgress
            ?.takeIf { state.currentRoute.isNfcPrompt }
            ?.let { "$it%" },
        onBack = state.onBackAction ?: { onEventSend(Event.Pop) },
        stickyBottom = { padding ->
            CardReaderStickyButtons(
                padding = padding,
                state = state,
                locale = locale,
                onEventSend = onEventSend
            )
        },
        contentErrorConfig = state.error
    ) { paddingValues ->
        if (state.isCancelFlowDialogVisible) {
            CancelFlowDialog(onEventSend = onEventSend)
        }

        NavHost(
            navController = routeNavController,
            startDestination = state.currentRoute.navRoute(),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding(), bottom = SPACING_SMALL.dp)
                .padding(horizontal = paddingValues.calculateStartPadding(LayoutDirection.Ltr))
        ) {
            CardReaderRoute.entries.forEach { route ->
                composable(route.navRoute()) {
                    CardReaderRouteScreen(
                        route = route,
                        state = state,
                        locale = locale,
                        bottomSheetState = bottomSheetState,
                        onEventSend = onEventSend
                    )
                }
            }
        }
    }
}

/**
 * Asks whether the user really wants to give up the flow, and cancels the issuance only once that is
 * confirmed.
 */
@Composable
private fun CancelFlowDialog(onEventSend: (Event) -> Unit) {
    ConfirmationDialog(
        config = ConfirmationDialogConfig(
            title = stringResource(R.string.pid_issuance_dialog_cancel_title),
            body = stringResource(R.string.pid_issuance_dialog_cancel_paragraph),
            confirmText = stringResource(R.string.pid_issuance_dialog_cancel_prim_button),
            dismissText = stringResource(R.string.pid_issuance_dialog_cancel_sec_button),
            onConfirm = { onEventSend(Event.Close) },
            onDismiss = { onEventSend(Event.DismissCancelFlowDialog) },
        )
    )
}

/**
 * Builds an internal route string for the nested NavHost.
 *
 * This stays private to the host so external navigation keeps using the shared
 * module-level routes from the main graph.
 */
internal fun CardReaderRoute.navRoute(): String = "card_reader_route/${name.lowercase()}"
