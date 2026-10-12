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

package eu.europa.ec.presentationfeature.router

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.presentationfeature.BuildConfig
import eu.europa.ec.presentationfeature.interactor.RequestRejectionReason
import eu.europa.ec.presentationfeature.ui.loading.PresentationLoadingScreen
import org.sprind.wallet.presentationfeature.error.BlockingErrorReason
import org.sprind.wallet.presentationfeature.error.PresentationErrorScreen
import org.sprind.wallet.presentationfeature.nodocument.PresentationNoDocumentScreen
import org.sprind.wallet.presentationfeature.rejected.PresentationRequestRejectedScreen
import eu.europa.ec.presentationfeature.ui.request.PresentationRequestScreen
import eu.europa.ec.presentationfeature.ui.success.PresentationSuccessScreen
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.PresentationScreens
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

const val RP_INFO_ARG = "rpInfo"

fun NavGraphBuilder.presentationGraph(navController: NavController) {
    navigation(
        startDestination = PresentationScreens.PresentationRequest.screenRoute,
        route = ModuleRoute.PresentationModule.route
    ) {
        composable(
            route = PresentationScreens.PresentationLoading.screenRoute,
        ) {
            PresentationLoadingScreen(
                navController,
                koinViewModel()
            )
        }

        composable(
            route = PresentationScreens.PresentationRequest.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + PresentationScreens.PresentationRequest.screenRoute
                }
            ),
            arguments = listOf(
                navArgument(RequestUriConfig.serializedKeyName) {
                    type = NavType.StringType
                },
            )
        ) {
            PresentationRequestScreen(
                navController,
                koinViewModel(
                    parameters = {
                        parametersOf(
                            it.arguments?.getString(RequestUriConfig.serializedKeyName).orEmpty()
                        )
                    }
                )
            )
        }

        composable(
            route = PresentationScreens.PresentationNoDocument.screenRoute,
        ) {
            PresentationNoDocumentScreen(navController)
        }

        composable(
            route = PresentationScreens.PresentationRequestRejected.screenRoute,
            arguments = listOf(
                navArgument(PresentationScreens.PresentationRequestRejected.ParamKey.REJECTION_REASON) {
                    type = NavType.StringType
                },
                navArgument(PresentationScreens.PresentationRequestRejected.ParamKey.REPORT_URI) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(PresentationScreens.PresentationRequestRejected.ParamKey.REDIRECT_URI) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            )
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            val rawReason = arguments
                ?.getString(PresentationScreens.PresentationRequestRejected.ParamKey.REJECTION_REASON)
            PresentationRequestRejectedScreen(
                navController = navController,
                fallbackReportUrl = koinInject<ConfigLogic>().environmentConfig.dataProtectionComplaintUrl,
                reason = rawReason
                    ?.let { name -> runCatching { RequestRejectionReason.valueOf(name) }.getOrNull() }
                    ?: RequestRejectionReason.NOT_REGISTERED,
                reportUri = arguments
                    ?.getString(PresentationScreens.PresentationRequestRejected.ParamKey.REPORT_URI)
                    ?.takeIf { it.isNotBlank() },
                redirectUri = arguments
                    ?.getString(PresentationScreens.PresentationRequestRejected.ParamKey.REDIRECT_URI)
                    ?.takeIf { it.isNotBlank() },
            )
        }

        composable(
            route = PresentationScreens.PresentationError.screenRoute,
            arguments = listOf(
                navArgument(PresentationScreens.PresentationError.ParamKey.REASON) {
                    type = NavType.StringType
                },
                navArgument(PresentationScreens.PresentationError.ParamKey.TRACE_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            )
        ) { entry ->
            val paramKey = PresentationScreens.PresentationError.ParamKey
            val reason = entry.arguments?.getString(paramKey.REASON)
                ?.let { name -> BlockingErrorReason.entries.firstOrNull { it.name == name } }
                ?: BlockingErrorReason.TRUST_FAILURE

            PresentationErrorScreen(
                navController = navController,
                viewModel = koinViewModel(
                    parameters = {
                        parametersOf(reason, entry.arguments?.getString(paramKey.TRACE_ID).orEmpty())
                    }
                ),
            )
        }

        composable(
            route = PresentationScreens.PresentationSuccess.screenRoute,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern =
                        BuildConfig.DEEPLINK + PresentationScreens.PresentationSuccess.screenRoute
                }
            ),
        ) {
            PresentationSuccessScreen(
                navController,
                koinViewModel()
            )
        }
    }
}
