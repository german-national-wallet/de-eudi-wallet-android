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

package org.sprind.wallet.dashboardfeature.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import kotlinx.serialization.Serializable
import org.sprind.wallet.uilogic.component.WebPageScreen

/**
 * Route to the credential catalogue.
 *
 * @property url the catalogue page in the app's language.
 */
@Serializable
data class CredentialCatalogueRoute(val url: String)

/**
 * The credential catalogue, read inside the app. The flows it links to run on the issuers' own
 * sites: [WebPageScreen] does not follow those links but asks the user to confirm leaving the app
 * and then hands them to the browser.
 */
@Composable
fun CredentialCatalogueScreen(
    navController: NavController,
    route: CredentialCatalogueRoute,
) {
    WebPageScreen(
        url = route.url,
        onBack = navController::popBackStack,
        title = stringResource(R.string.overview_credential_teaser_button),
    )
}
