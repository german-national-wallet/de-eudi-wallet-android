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

package org.sprind.wallet.onboardingfeature.setup.privacypolicy

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import kotlinx.serialization.Serializable
import org.sprind.wallet.uilogic.component.WebPageScreen

/**
 * Route to a legal document opened from a setup step, the terms of use or the privacy policy.
 *
 * The documents are not published yet, so the config hands over
 * [org.sprind.wallet.businesslogic.config.SettingsLinks.BLANK_PAGE] for now and the page opens
 * empty.
 *
 * @property url the page to read.
 * @property titleRes names the document in the toolbar.
 */
@Serializable
data class SetupWebPageRoute(
    val url: String,
    @param:StringRes val titleRes: Int,
)

/** A legal document, read inside the app; back returns to the setup step. */
@Composable
fun SetupWebPageScreen(
    navController: NavController,
    route: SetupWebPageRoute,
) {
    WebPageScreen(
        url = route.url,
        onBack = { navController.popBackStack() },
        title = stringResource(route.titleRes),
    )
}
