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

package org.sprind.wallet.dashboardfeature.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.extension.openAppNotificationSettings
import eu.europa.ec.uilogic.extension.shareLogs
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.uilogic.component.BottomNavigationBar
import org.sprind.wallet.uilogic.component.BottomNavigationTab
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.navigateToBottomNavigationTab

@Composable
fun SettingsScreen(
    navHostController: NavController,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    MainScreen(
        state = state,
        onEventSend = viewModel::setEvent,
        onBottomNavigationTabSelected = navHostController::navigateToBottomNavigationTab,
    )

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.OpenNotificationSettings -> context.openAppNotificationSettings()
                is Effect.ShareLogs -> context.shareLogs(effect.fileUris)
                is Effect.Navigation.SwitchScreen ->
                    navHostController.navigate(effect.screenRoute)

                is Effect.Navigation.OpenWebPage -> navHostController.navigate(effect.route)
            }
        }.collect()
    }
}

@Composable
private fun MainScreen(
    state: State,
    onEventSend: (Event) -> Unit,
    onBottomNavigationTabSelected: (BottomNavigationTab) -> Unit,
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        // A tab is never stacked on another tab, so back always leads to the overview.
        onBack = { onBottomNavigationTabSelected(BottomNavigationTab.OVERVIEW) },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = BottomNavigationTab.SETTINGS,
                onTabSelected = onBottomNavigationTabSelected,
            )
        }
    ) { paddingValues ->
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_MEDIUM.dp),
            title = {
                Text(
                    text = stringResource(R.string.settings_overview_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            extraContent = {
                Column(verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)) {
                    state.menuGroups.forEachIndexed { index, group ->
                        SettingsMenuSection(
                            group = group,
                            onEntryClick = { onEventSend(Event.MenuEntryClicked(it)) },
                        )

                        if (index < state.menuGroups.lastIndex) {
                            HorizontalDivider()
                        }
                    }

                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(
                            R.string.settings_overview_app_version,
                            state.appVersion,
                            state.appVersionCode,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun SettingsScreenPreview() {
    PreviewTheme {
        MainScreen(
            state = State(
                menuGroups = settingsMenuGroups(isLogExportAvailable = true),
                appVersion = "0.52.0-Dev",
                appVersionCode = 875,
            ),
            onEventSend = {},
            onBottomNavigationTabSelected = {},
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun SettingsScreenWithoutLogExportPreview() {
    PreviewTheme {
        MainScreen(
            state = State(
                menuGroups = settingsMenuGroups(isLogExportAvailable = false),
                appVersion = "0.52.0",
                appVersionCode = 875,
            ),
            onEventSend = {},
            onBottomNavigationTabSelected = {},
        )
    }
}