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

package org.sprind.wallet.dashboardfeature.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import kotlinx.serialization.Serializable
import org.sprind.wallet.uilogic.component.WebPageScreen

/**
 * Route to the page a settings menu entry opens — the support page and the legal documents.
 *
 * None of these documents is published yet, so the config hands over
 * [org.sprind.wallet.businesslogic.config.SettingsLinks.BLANK_PAGE] for now and the page opens
 * empty.
 *
 * @property url the page to read.
 * @property titleRes the label of the entry that led here, which names the screen in the toolbar.
 */
@Serializable
data class SettingsWebPageRoute(
    val url: String,
    @StringRes val titleRes: Int,
)

/** A settings document, read inside the app rather than handed to a browser. */
@Composable
fun SettingsWebPageScreen(
    navController: NavController,
    route: SettingsWebPageRoute,
) {
    WebPageScreen(
        url = route.url,
        onBack = { navController.popBackStack() },
        title = stringResource(route.titleRes),
    )
}