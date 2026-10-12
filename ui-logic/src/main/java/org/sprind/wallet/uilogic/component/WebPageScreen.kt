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

package org.sprind.wallet.uilogic.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.content.ToolbarConfig
import eu.europa.ec.uilogic.component.loader.LoadingIndicator

/**
 * One web page read inside the app rather than handed to a browser: the app's toolbar above a
 * [SecureWebView], so a legal document or a support page stays part of the wallet and cannot turn
 * into a browsing session.
 *
 * @param url the page to read. [SecureWebView] refuses anything but https pages on its host.
 * @param onBack leaves the page; the toolbar's back action.
 * @param title names the page in the toolbar, so the screen has a name a screen reader can
 *   announce before the page itself has loaded. `null` leaves the toolbar bare for the pages whose
 *   own heading is the only title they need.
 */
@Composable
fun WebPageScreen(
    url: String,
    onBack: () -> Unit,
    title: String? = null,
) {
    var isLoading by remember(url) { mutableStateOf(true) }

    ContentScreen(
        toolBarConfig = title?.let { ToolbarConfig(title = it) },
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = onBack,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            SecureWebView(
                url = url,
                modifier = Modifier.fillMaxSize(),
                onLoadingChanged = { isLoading = it },
            )

            if (isLoading) {
                LoadingIndicator()
            }
        }
    }
}