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

package org.sprind.wallet.onboardingfeature.setup.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.koin.androidx.compose.koinViewModel
import org.sprind.wallet.onboardingfeature.setup.model.SetupStep
import org.sprind.wallet.onboardingfeature.setup.notifications.NotificationConsentScreen
import org.sprind.wallet.onboardingfeature.setup.platformauth.PlatformAuthSetupScreen
import org.sprind.wallet.onboardingfeature.setup.privacypolicy.PrivacyPolicySetupScreen
import org.sprind.wallet.onboardingfeature.setup.privacypolicy.SetupWebPageRoute
import org.sprind.wallet.onboardingfeature.setup.privacypolicy.SetupWebPageScreen
import org.sprind.wallet.onboardingfeature.setup.success.SetupSuccessScreen
import org.sprind.wallet.onboardingfeature.setup.ui.SetupStepRoute

/**
 * The app setup steps. They are entered through the navigation guards (each step's screen route is
 * navigated to directly), so [ModuleRoute.SetupModule]'s start destination is only the fallback.
 */
fun NavGraphBuilder.featureSetupGraph(navController: NavController) {
    navigation(
        startDestination = SetupStep.entries.first().screen.screenRoute,
        route = ModuleRoute.SetupModule.route,
    ) {
        SetupStep.entries.forEach { step ->
            composable(route = step.screen.screenRoute) {
                SetupStepRoute(
                    navController = navController,
                    step = step,
                    viewModel = koinViewModel(),
                ) { onComplete, onBack ->
                    when (step) {
                        SetupStep.NOTIFICATIONS -> NotificationConsentScreen(
                            viewModel = koinViewModel(),
                            onComplete = onComplete,
                            onBack = onBack,
                        )

                        SetupStep.PLATFORM_AUTH -> PlatformAuthSetupScreen(
                            viewModel = koinViewModel(),
                            onComplete = onComplete,
                            onBack = onBack,
                        )

                        SetupStep.PRIVACY_POLICY -> PrivacyPolicySetupScreen(
                            viewModel = koinViewModel(),
                            navController = navController,
                            onComplete = onComplete,
                            onBack = onBack,
                        )

                        SetupStep.SUCCESS -> SetupSuccessScreen(
                            onComplete = onComplete,
                            onBack = onBack,
                        )
                    }
                }
            }
        }

        composable<SetupWebPageRoute> { backStackEntry ->
            SetupWebPageScreen(
                navController = navController,
                route = backStackEntry.toRoute<SetupWebPageRoute>(),
            )
        }
    }
}
