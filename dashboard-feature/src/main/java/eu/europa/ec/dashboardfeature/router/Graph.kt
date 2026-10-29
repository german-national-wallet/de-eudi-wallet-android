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

package eu.europa.ec.dashboardfeature.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import eu.europa.ec.dashboardfeature.BuildConfig
import eu.europa.ec.dashboardfeature.ui.dashboard.DashboardScreen
import org.sprind.wallet.dashboardfeature.ui.activities.ActivitiesScreen
import org.sprind.wallet.dashboardfeature.ui.activities.ActivityDetailRoute
import org.sprind.wallet.dashboardfeature.ui.activities.CredentialActivitiesRoute
import org.sprind.wallet.dashboardfeature.ui.activities.detail.ActivityDetailScreen
import org.sprind.wallet.dashboardfeature.ui.documentdetail.DashboardDocumentDetailScreen
import org.sprind.wallet.dashboardfeature.ui.settings.SettingsScreen
import org.sprind.wallet.dashboardfeature.ui.settings.SettingsWebPageRoute
import org.sprind.wallet.dashboardfeature.ui.settings.SettingsWebPageScreen
import org.sprind.wallet.uilogic.component.bottomNavigationTabDestination
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import org.sprind.wallet.dashboardfeature.ui.settings.debugconfig.DebugConfigScreen

fun NavGraphBuilder.featureDashboardGraph(navController: NavController) {
    navigation(
        startDestination = DashboardScreens.Dashboard.screenRoute,
        route = ModuleRoute.DashboardModule.route
    ) {
        bottomNavigationTabDestination(
            route = DashboardScreens.Dashboard.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + DashboardScreens.Dashboard.screenRoute
                }
            ),
        ) {

            DashboardScreen(
                navHostController = navController,
                viewModel = koinViewModel()
            )
        }

        bottomNavigationTabDestination(route = DashboardScreens.Activities.screenRoute) {
            ActivitiesScreen(
                navHostController = navController,
                viewModel = koinViewModel(parameters = { parametersOf("") }),
            )
        }

        composable<CredentialActivitiesRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<CredentialActivitiesRoute>()
            ActivitiesScreen(
                navHostController = navController,
                viewModel = koinViewModel(parameters = { parametersOf(route.documentId) }),
            )
        }

        composable<ActivityDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ActivityDetailRoute>()
            ActivityDetailScreen(
                navHostController = navController,
                viewModel = koinViewModel(parameters = { parametersOf(route.activityId) }),
            )
        }

        bottomNavigationTabDestination(route = DashboardScreens.Settings.screenRoute) {
            SettingsScreen(
                navHostController = navController,
                viewModel = koinViewModel()
            )
        }

        composable<SettingsWebPageRoute> { backStackEntry ->
            SettingsWebPageScreen(
                navController = navController,
                route = backStackEntry.toRoute<SettingsWebPageRoute>(),
            )
        }

        composable(
            route = DashboardScreens.DebugConfig.screenRoute
        ) {
            DebugConfigScreen(
                navController = navController,
                viewModel = koinViewModel(),
            )
        }

        composable(
            route = DashboardScreens.DashboardDocumentDetails.screenRoute,
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                },
            )
        ) {
            DashboardDocumentDetailScreen(
                navHostController = navController,
                viewModel =
                    koinViewModel(
                        parameters = {
                            parametersOf(
                                it.arguments?.getString("documentId").orEmpty(),
                            )
                        }
                    )
            )
        }
    }
}
