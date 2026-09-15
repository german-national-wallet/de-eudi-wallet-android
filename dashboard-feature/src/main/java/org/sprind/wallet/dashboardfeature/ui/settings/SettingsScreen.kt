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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemLeadingContentData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.SectionTitle
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.WrapListItem
import org.sprind.wallet.uilogic.component.BottomNavigationBar
import org.sprind.wallet.uilogic.component.BottomNavigationTab
import org.sprind.wallet.uilogic.component.navigateToBottomNavigationTab

private const val SETTINGS_LOG_EXPORT = "settings_log_export"

/**
 * The settings tab. Apart from the debug menu it took over from the dashboard's slide menu, its
 * body is still empty — the settings themselves arrive with WD-3959.
 */
@Composable
fun SettingsScreen(
    navHostController: NavController,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    MainScreen(
        state = state,
        onExportLogs = { viewModel.setEvent(Event.ExportLogs(context)) },
        onBottomNavigationTabSelected = navHostController::navigateToBottomNavigationTab,
    )
}

@Composable
private fun MainScreen(
    state: State,
    onExportLogs: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        ) {
            // The debug menu is limited to the flavors carrying one; log export additionally needs
            // the log files the log writer of those flavors leaves behind.
            if (state.isDebugMenuEnabled && state.isLogWriterEnabled) {
                SectionTitle(
                    modifier = Modifier,
                    text = stringResource(R.string.dashboard_drawer_title),
                )

                WrapListItem(
                    item = ListItemData(
                        itemId = SETTINGS_LOG_EXPORT,
                        mainContentData = ListItemMainContentData.Text(
                            text = stringResource(R.string.dashboard_drawer_log_export)
                        ),
                        leadingContentData = ListItemLeadingContentData.Icon(
                            iconData = AppIcons.Edit
                        ),
                    ),
                    onItemClick = { onExportLogs() },
                )
            }
        }
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun SettingsScreenPreview() {
    PreviewTheme {
        MainScreen(
            state = State(
                isDebugMenuEnabled = true,
                isLogWriterEnabled = true,
            ),
            onExportLogs = {},
            onBottomNavigationTabSelected = {},
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun SettingsScreenWithoutDebugMenuPreview() {
    PreviewTheme {
        MainScreen(
            state = State(
                isDebugMenuEnabled = false,
                isLogWriterEnabled = false,
            ),
            onExportLogs = {},
            onBottomNavigationTabSelected = {},
        )
    }
}
