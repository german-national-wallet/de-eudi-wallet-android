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

package org.sprind.wallet.dashboardfeature.ui.activities

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import org.sprind.wallet.uilogic.component.BottomNavigationBar
import org.sprind.wallet.uilogic.component.BottomNavigationTab
import org.sprind.wallet.uilogic.component.navigateToBottomNavigationTab

/**
 * The activities tab. Its body is still empty — the credential activity list arrives with WD-534.
 */
@Composable
fun ActivitiesScreen(
    navHostController: NavController,
) {
    MainScreen(
        onBottomNavigationTabSelected = navHostController::navigateToBottomNavigationTab,
    )
}

@Composable
private fun MainScreen(
    onBottomNavigationTabSelected: (BottomNavigationTab) -> Unit,
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.NONE,
        // A tab is never stacked on another tab, so back always leads to the overview.
        onBack = { onBottomNavigationTabSelected(BottomNavigationTab.OVERVIEW) },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = BottomNavigationTab.ACTIVITIES,
                onTabSelected = onBottomNavigationTabSelected,
            )
        }
    ) {
        // Intentionally empty, see WD-534.
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun ActivitiesScreenPreview() {
    PreviewTheme {
        MainScreen(
            onBottomNavigationTabSelected = {},
        )
    }
}
