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

package eu.europa.ec.presentationfeature.ui.loading

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.uilogic.component.SystemBroadcastReceiver
import eu.europa.ec.commonfeature.ui.loading.LoadingScreen

@Composable
fun PresentationLoadingScreen(
    navController: NavController,
    viewModel: PresentationLoadingViewModel
) {
    SystemBroadcastReceiver(
        actions = listOf(CoreActions.VCI_RESUME_ACTION)
    ) {
        if (it?.action == CoreActions.VCI_RESUME_ACTION) {
            it.extras?.getString("uri")?.let(viewModel::resumeOpenId4VciWithAuthorization)
        }
    }

    LoadingScreen(
        navController = navController,
        viewModel = viewModel
    )
}