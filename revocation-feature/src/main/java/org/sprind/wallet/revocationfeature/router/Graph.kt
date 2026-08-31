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

package org.sprind.wallet.revocationfeature.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.RevocationScreens
import org.koin.androidx.compose.koinViewModel
import org.sprind.wallet.revocationfeature.ui.RevocationIntroScreen
import org.sprind.wallet.revocationfeature.ui.RevocationSaveCodeScreen

fun NavGraphBuilder.featureRevocationGraph(navController: NavController) {
    navigation(
        startDestination = RevocationScreens.Intro.screenRoute,
        route = ModuleRoute.RevocationModule.route
    ) {
        // Intro
        composable(route = RevocationScreens.Intro.screenRoute) {
            RevocationIntroScreen(navController)
        }

        // Save Code
        composable(route = RevocationScreens.SaveCode.screenRoute) {
            RevocationSaveCodeScreen(navController, koinViewModel())
        }
    }
}