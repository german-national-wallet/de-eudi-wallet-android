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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.extension.openUrl
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.sprind.wallet.uilogic.component.ArtworkCloseAction
import org.sprind.wallet.uilogic.component.ArtworkTopBar
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ExternalLinkButton
import org.sprind.wallet.uilogic.component.dialog.RedirectInfoDialog
import org.sprind.wallet.uilogic.component.dialog.RedirectInfoDialogConfig

/**
 * Full-screen blocking error state (WD-4270): shown when a presentation cannot
 * proceed at all — e.g. the relying party could not be trusted
 * or requested more data than allowed. Sharing data is impossible, so the user may
 * only report the problem or leave.
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
                    navController.navigate(DashboardScreens.Dashboard.screenRoute) {
                        popUpTo(ModuleRoute.PresentationModule.route) {
                            inclusive = true
                        }
                    }

                is Effect.Navigation.OpenUrl -> context.openUrl(effect.url.toUri())
            }
        }
    }
}

/**
 * Stateless rendering of [state]; shared with the previews. Follows the Figma "blocking"
 * layout (WD-4274): full-width artwork with a round close button, title and body on the
 * background, and the actions pinned to the bottom. The trace ID is not shown here.
 */
@Composable
internal fun PresentationErrorContent(
    state: State,
    onEvent: (Event) -> Unit,
) {
    val config = state.config
    val artwork = (config.blockingReason ?: BlockingErrorReason.TRUST_FAILURE).artwork()

    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        topBar = {
            ArtworkTopBar(artwork = artwork) {
                ArtworkCloseAction(onClick = { onEvent(Event.ClosePressed) })
            }
        },
        stickyBottom = { paddingValues ->
            Column(
                modifier = Modifier.padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
            ) {
                ActionButton(
                    action = config.primaryAction,
                    type = ButtonType.PRIMARY,
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
        },
    ) { paddingValues ->
        ContentTemplateBody(
            modifier = Modifier
                .padding(paddingValues)
                .padding(top = SPACING_MEDIUM.dp),
            // Hint at text hidden behind the pinned actions.
            templateConfig = ContentTemplateConfig(showScrollFade = true),
            title = {
                Text(
                    text = stringResource(config.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            body = { Text(stringResource(config.bodyRes)) },
        )
    }
}

/** Actions that leave the app carry the external-link icon, as in Figma. */
@Composable
private fun ActionButton(
    action: PresentationErrorAction,
    type: ButtonType,
    onClick: () -> Unit,
) {
    if (action.kind == PresentationErrorAction.Kind.REPORT_ISSUE) {
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
}
