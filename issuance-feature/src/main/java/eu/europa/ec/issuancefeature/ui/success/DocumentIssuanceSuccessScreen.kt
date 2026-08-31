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

package eu.europa.ec.issuancefeature.ui.success

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.ui.document_success.DocumentSuccessScreen
import eu.europa.ec.commonfeature.ui.document_success.Effect
import eu.europa.ec.commonfeature.ui.success.SuccessView
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.extension.cacheDeepLink
import kotlinx.coroutines.flow.collectLatest

@Composable
fun DocumentIssuanceSuccessScreen(
    navController: NavController,
    viewModel: DocumentIssuanceSuccessViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, navController) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is Effect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.screenRoute) {
                        effect.popUpRoute?.let { popUpRoute ->
                            popUpTo(popUpRoute) {
                                inclusive = true
                            }
                        }
                    }
                }

                is Effect.Navigation.PopBackStackUpTo -> {
                    navController.popBackStack(
                        route = effect.screenRoute, inclusive = effect.inclusive
                    )
                }

                is Effect.Navigation.DeepLink -> {
                    context.cacheDeepLink(effect.link)
                    effect.routeToPop?.let {
                        navController.popBackStack(
                            route = it, inclusive = false
                        )
                    } ?: navController.popBackStack()
                }

                is Effect.Navigation.Pop -> navController.popBackStack()
            }
        }
    }

    if (viewModel.shouldUseSimpleSuccessView()) {
        ContentScreen(
            isLoading = state.isLoading,
            navigatableAction = ScreenNavigateAction.NONE,
        ) { paddingValues ->
            SuccessView(
                title = viewModel.getSimpleSuccessTitle(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }

        LaunchedEffect(viewModel) {
            viewModel.scheduleAutoNavigation()
        }
    } else {
        // this success screen will be re-design
        DocumentSuccessScreen(
            navController = navController,
            viewModel = viewModel,
        )
    }
}
