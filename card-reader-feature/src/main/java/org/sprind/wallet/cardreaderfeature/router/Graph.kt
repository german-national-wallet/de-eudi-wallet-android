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

package org.sprind.wallet.cardreaderfeature.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.toRoute
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.ui.issuer_details.model.IssuerInfo
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.uilogic.navigation.CardReaderScreens
import eu.europa.ec.uilogic.navigation.ModuleRoute
import kotlinx.serialization.json.Json
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import org.sprind.wallet.cardreaderfeature.ui.document.issuer_details.IssuerDetailsScreen
import org.sprind.wallet.cardreaderfeature.ui.document.privacy.PrivacyPolicyRoute
import org.sprind.wallet.cardreaderfeature.ui.document.privacy.PrivacyPolicyScreen
import org.sprind.wallet.cardreaderfeature.ui.document.read.ReadCardScreen

const val CredentialTypesArg = "credentialTypes"

fun NavGraphBuilder.featureCardReaderGraph(navController: NavController) {
    navigation(
        startDestination = CardReaderScreens.Reader.screenRoute,
        route = ModuleRoute.CardReadModule.route
    ) {
        // Card reader
        composable(
            route = CardReaderScreens.Reader.screenRoute,
            arguments = listOf(
                navArgument(CredentialTypesArg) {
                    type = NavType.StringType
                })
        ) {
            ReadCardScreen(
                navController,
                koinViewModel {
                    parametersOf(
                        IssuanceFlowUiConfig.fromString(
                            it.arguments?.getString("flowType").orEmpty(),
                        ),
                        it.arguments?.getString(CredentialTypesArg)?.let { arg ->
                            Json.decodeFromString<Set<CredentialConfigurationIdentifier>>(arg)
                        }.orEmpty()
                    )
                }
            )
        }

        /**
         * Improvement to our navigation system
         *
         * The route for the IssuerDetailsScreen
         * @Serializable
         * data class IssuerDetailsRoute(val issuerInfo: IssuerInfoUi)
         *
         * // The route for the PrivacyPolicyScreen
         * @Serializable
         * data class PrivacyPolicyRoute(val issuerInfo: IssuerInfoUi)
         *
         * then we define this in the NavHost
         *
         * NavHost(navController = navController, startDestination = "...") {
         *
         *     // Composable for the IssuerDetailsScreen
         *     composable<IssuerDetailsRoute> { backStackEntry ->
         *         val detailsRoute = backStackEntry.toRoute<IssuerDetailsRoute>()
         *         IssuerDetailsScreen(
         *             navController,
         *             getViewModel { parametersOf(detailsRoute.issuerInfo) }
         *         )
         *     }
         *
         *     // Composable for the PrivacyPolicyScreen
         *     composable<PrivacyPolicyRoute> { backStackEntry ->
         *         val privacyRoute = backStackEntry.toRoute<PrivacyPolicyRoute>()
         *         PrivacyPolicyScreen(
         *             navController,
         *             // You can pass the IssuerInfoUi object to a ViewModel here if needed
         *             getViewModel { parametersOf(privacyRoute.issuerInfo) }
         *         )
         *     }
         * }
         *
         * and finally we can navigate with ease
         *
         * // Navigate to the Issuer Details Screen
         * val issuerInfo = IssuerInfoUi(...)
         * navController.navigate(IssuerDetailsRoute(issuerInfo))
         *
         * // Navigate to the Privacy Policy Screen
         * val issuerInfo = IssuerInfoUi(...)
         * navController.navigate(PrivacyPolicyRoute(issuerInfo))
         *
         * this new approach avoids manual serialization that currently is in place
         */
        composable<IssuerInfo> { backStackEntry ->

            val issuerInfo = backStackEntry.toRoute<IssuerInfo>()

            IssuerDetailsScreen(
                navController,
                koinViewModel { parametersOf(issuerInfo) }
            )
        }

        composable<PrivacyPolicyRoute> { backStackEntry ->
            PrivacyPolicyScreen(
                navController = navController,
                url = backStackEntry.toRoute<PrivacyPolicyRoute>().url,
            )
        }
    }
}