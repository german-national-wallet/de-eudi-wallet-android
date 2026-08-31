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

package org.sprind.wallet.cardreaderfeature.ui.document.privacy

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import kotlinx.serialization.Serializable
import org.sprind.wallet.uilogic.component.SecureWebView

/**
 * Route to [PrivacyPolicyScreen].
 *
 * [url] is what the screen reads. The issuance policy page is still to be published, so the flow
 * passes [BLANK_PAGE] for now and the screen opens empty.
 */
@Serializable
data class PrivacyPolicyRoute(val url: String) {
    companion object {
        const val BLANK_PAGE = "about:blank"
    }
}

/** The privacy policy, read inside the app rather than handed to a browser. */
@Composable
fun PrivacyPolicyScreen(
    navController: NavController,
    url: String,
) {
    ContentScreen(
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { navController.popBackStack() },
    ) { paddingValues ->
        SecureWebView(
            url = url,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        )
    }
}