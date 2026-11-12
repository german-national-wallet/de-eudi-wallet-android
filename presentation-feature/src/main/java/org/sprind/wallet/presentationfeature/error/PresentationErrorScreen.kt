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

package org.sprind.wallet.presentationfeature.error

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.extension.openUrl
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.sprind.wallet.uilogic.component.ArtworkBlockingScreen
import org.sprind.wallet.uilogic.component.ExternalLinkButton
import org.sprind.wallet.uilogic.component.dialog.RedirectInfoDialog
import org.sprind.wallet.uilogic.component.dialog.RedirectInfoDialogConfig

/**
 * Full-screen blocking error state (WD-4270): shown when a presentation cannot
 * proceed at all — e.g. the relying party could not be trusted
 * or requested more data than allowed, or the wallet lacks the credential it asks for.
 * Sharing data is impossible, so the user may only report the problem, add the missing
 * Digital ID, or leave.
 *
 * The content comes from [PresentationErrorViewModel]'s [State]; button presses are
 * sent as [Event]s and the resulting navigation arrives as [Effect]s.
 */
@Composable
fun PresentationErrorScreen(
    navController: NavController,
    viewModel: PresentationErrorViewModel,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    PresentationErrorContent(
        state = state,
        onEvent = viewModel::setEvent,
    )

    state.pendingReportUrl?.let { url ->
        RedirectInfoDialog(
            config = RedirectInfoDialogConfig(
                target = url.toUri(),
                onConfirm = { viewModel.setEvent(Event.ReportRedirectConfirmed) },
                onDismiss = { viewModel.setEvent(Event.ReportRedirectDismissed) },
            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                Effect.Navigation.Pop -> navController.popBackStack()
                Effect.Navigation.ToDashboard ->
                    navController.leavePresentationFor(DashboardScreens.Dashboard.screenRoute)

                // The reader opens a fresh overview when it is left, like it does when the
                // overview itself starts it, so nothing is kept underneath.
                is Effect.Navigation.ToPidIssuance ->
                    navController.leavePresentationFor(effect.screenRoute)

                is Effect.Navigation.OpenUrl -> context.openUrl(effect.url.toUri())
            }
        }
    }
}

/** Leaves the presentation flow for [route], dropping the whole flow from the back stack. */
private fun NavController.leavePresentationFor(route: String) {
    navigate(route) {
        popUpTo(ModuleRoute.PresentationModule.route) {
            inclusive = true
        }
    }
}

/**
 * Stateless rendering of [state]; shared with the previews. Follows the Figma "blocking"
 * layout (WD-4274) via [ArtworkBlockingScreen]. The trace ID is not shown here.
 */
@Composable
internal fun PresentationErrorContent(
    state: State,
    onEvent: (Event) -> Unit,
) {
    val config = state.config

    ArtworkBlockingScreen(
        artwork = (config.blockingReason ?: BlockingErrorReason.TRUST_FAILURE).artwork(),
        titleRes = config.titleRes,
        bodyRes = config.bodyRes,
        onClose = { onEvent(Event.ClosePressed) },
        isLoading = state.isLoading,
        // The locked Digital ID screen has no close button in Figma; back still leaves.
        showClose = config.blockingReason != BlockingErrorReason.PID_LOCKED,
    ) {
        ActionButton(
            action = config.primaryAction,
            type = if (config.primaryAction.outlined) ButtonType.SECONDARY else ButtonType.PRIMARY,
            onClick = { onEvent(Event.PrimaryActionPressed) },
        )
        config.secondaryAction?.let { action ->
            ActionButton(
                action = action,
                type = ButtonType.SECONDARY,
                onClick = { onEvent(Event.SecondaryActionPressed) },
            )
        }
    }
}

/** Actions that leave the app carry the external-link icon, as in Figma. */
@Composable
private fun ActionButton(
    action: PresentationErrorAction,
    type: ButtonType,
    onClick: () -> Unit,
) {
    if (action.opensExternalLink) {
        ExternalLinkButton(type = type, textRes = action.labelRes, onClick = onClick)
    } else {
        WrapButton(
            modifier = Modifier.fillMaxWidth(),
            buttonConfig = ButtonConfig(type = type, onClick = onClick),
        ) {
            Text(stringResource(action.labelRes))
        }
    }
}

/** The header artwork per reason (Figma `1:1_assets_width_360` topics). */
private fun BlockingErrorReason.artwork(): IconData = when (this) {
    BlockingErrorReason.TRUST_FAILURE,
    BlockingErrorReason.OVERASKING,
        -> AppIcons.PhoneSecurityShield

    BlockingErrorReason.MISSING_CREDENTIAL -> AppIcons.MissingCredential

    BlockingErrorReason.MISSING_PID -> AppIcons.MissingPid

    BlockingErrorReason.PID_LOCKED -> AppIcons.PidLocked
}
