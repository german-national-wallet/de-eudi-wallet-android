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

@file:OptIn(ExperimentalMaterial3Api::class)

package org.sprind.wallet.dashboardfeature.ui.documentdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.parseCssColor
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.content.ContentPidTopBarConfig
import eu.europa.ec.uilogic.component.content.ContentScreenWithPidTopBar
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.component.wrap.WrapText
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.EaaDocumentCard


@Composable
fun DashboardDocumentDetailScreen(
    navHostController: NavController,
    viewModel: DashboardDocumentDetailViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Init)
    }

    MainScreen(
        state = state,
        onEventSend = viewModel::setEvent
    )

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> handleNavigationEffect(effect, navHostController)
            }
        }.collect()
    }
}

@Composable
private fun MainScreen(
    state: State,
    onEventSend: (Event) -> Unit,
) {
    ContentScreenWithPidTopBar(
        isLoading = state.isLoading,
        contentErrorConfig = state.error,
        contentPidTopBarConfig = ContentPidTopBarConfig(
            title = state.documentType,
            onBackIconPress = { onEventSend(Event.OnBackPressed) },
            backgroundColor = parseCssColor(
                state.topBarBackgroundColor,
                ThemeColors.primaryPid
            ),
            backgroundImageUri = state.topBarBackgroundImageUri,
            textColor = parseCssColor(
                state.topBarTextColor,
                ThemeColors.onPrimaryPid
            )
        ),
        onBack = { onEventSend(Event.OnBackPressed) },
        stickyBottom = { paddingValues ->
            WrapStickyBottomContent(
                stickyBottomModifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = SPACING_MEDIUM.dp,
                        bottom = paddingValues.calculateBottomPadding()
                    )
                    .padding(horizontal = SPACING_MEDIUM.dp),
                stickyBottomConfig = StickyBottomConfig(
                    showDivider = false,
                    type = StickyBottomType.OneButton(
                        config = ButtonConfig(
                            enabled = true,
                            type = ButtonType.SECONDARY,
                            contentPadding = PaddingValues(
                                vertical = SPACING_EXTRA_MEDIUM.dp,
                                horizontal = SPACING_LARGE.dp
                            ),
                            onClick = { onEventSend(Event.DeleteDocument) }
                        )
                    )
                )
            ) {
                WrapText(
                    text = stringResource(R.string.pid_inspection_pid_details_sec_button),
                    textConfig = TextConfig(
                        style = ThemeTextStyles.onSecondaryButton,
                        color = ThemeColors.onSecondaryButton,
                        textAlign = TextAlign.Center
                    ),
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            state.eaaCardData?.let { cardData ->
                EaaDocumentCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SPACING_MEDIUM.dp),
                    data = cardData,
                )
            }
            DetailTopItems(
                modifier = Modifier.padding(SPACING_MEDIUM.dp),
                docId = state.docId,
                onEventSend = onEventSend,
            )
            Spacer(Modifier.weight(1f))
            DetailBottomItems(
                modifier = Modifier.padding(horizontal = SPACING_MEDIUM.dp),
                validTill = state.validTill,
                createdOn = state.createdOn,
            )
        }
    }
}

@Composable
private fun DetailTopItems(
    modifier: Modifier,
    docId: String,
    onEventSend: (Event) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        DetailItem(
            modifier = Modifier
                .fillMaxWidth(),
            icon = AppIcons.UserBw,
            value = stringResource(R.string.pid_inspection_pid_details_list_1),
            onClick = { onEventSend(Event.GoToDocumentDetails(docId)) }
        )
        DetailItem(
            modifier = Modifier
                .fillMaxWidth(),
            icon = AppIcons.Apartment,
            value = stringResource(R.string.pid_inspection_pid_details_list_2),
            onClick = { onEventSend(Event.GoToIssuerDetails) }
        )
        DetailItem(
            modifier = Modifier
                .fillMaxWidth(),
            icon = AppIcons.History,
            value = stringResource(R.string.pid_inspection_pid_details_list_3),
            onClick = { onEventSend(Event.GoToActivities) }
        )
    }
}

@Composable
private fun DetailBottomItems(
    modifier: Modifier,
    createdOn: String,
    validTill: String,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        DetailDateItem(
            modifier = Modifier
                .fillMaxWidth(),
            icon = AppIcons.Event,
            label = stringResource(R.string.pid_inspection_pid_details_label_1),
            value = createdOn
        )
        DetailDateItem(
            modifier = Modifier
                .fillMaxWidth(),
            icon = AppIcons.Card,
            label = stringResource(R.string.pid_inspection_pid_details_label_2),
            value = validTill
        )
    }
}

@Composable
private fun DetailItem(
    modifier: Modifier = Modifier,
    icon: IconData,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        WrapIcon(
            modifier = Modifier.weight(0.10f),
            iconData = icon,
            customTint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WrapText(
            modifier = Modifier.weight(0.80f),
            text = value,
            textConfig = TextConfig(
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        WrapIcon(
            modifier = Modifier.weight(0.10f),
            iconData = AppIcons.PlayArrow,
            customTint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DetailDateItem(
    modifier: Modifier = Modifier,
    icon: IconData,
    label: String,
    value: String,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WrapIcon(
            modifier = Modifier.weight(0.10f),
            iconData = icon,
        )
        Column(modifier = Modifier.weight(0.90f)) {
            WrapText(
                text = label,
                textConfig = TextConfig(
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            WrapText(
                modifier = Modifier.padding(top = 1.dp),
                text = value,
                textConfig = TextConfig(
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute)
        }

        is Effect.Navigation.Pop -> {
            navController.popBackStack()
        }

        Effect.Navigation.Restart -> restartApp(navController)
    }
}

/**
 * Resets the app to the startup flow after a terminal state change (e.g. PID deletion), which
 * re-routes to onboarding when no PID is present. This is a soft navigation reset — the whole back
 * stack is popped and screen ViewModels are recreated — rather than a process restart.
 */
fun restartApp(navController: NavController) {
    navController.navigate(ModuleRoute.StartupModule.route) {
        popUpTo(navController.graph.id) {
            inclusive = true
        }
        launchSingleTop = true
    }
}


@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun DocumentDetailScreenPreview() {
    PreviewTheme {
        MainScreen(
            state = State(
                documentType = stringResource(R.string.pid_inspection_pid_details_title),
                validTill = "12.03.2026",
                createdOn = "12.03.2026",
                physicalDocumentName = stringResource(R.string.pid_inspection_pid_details_title)
            ),
        ) {}
    }
}