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

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import eu.europa.ec.uilogic.extension.findActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps the window out of screenshots, screen recordings and the recent-apps thumbnail for as long
 * as the calling composable is on screen.
 *
 * Used by [CodeEntryField] while it is asking for a secret. Masked cells alone are not enough: the
 * eye control makes the digits legible on demand, and a recording keeps whatever was on screen when
 * it was pressed.
 *
 * Callers nest and overlap — a navigation transition has the outgoing and incoming screens composed
 * at the same time — so the flag is reference counted rather than set and cleared per caller.
 * Without that, the screen being left would clear the flag out from under the one being entered.
 */
@Composable
fun SecureScreenEffect() {
    // A preview renders without an Activity to take the flag, and has no screen to protect anyway.
    if (LocalInspectionMode.current) return

    val window = LocalContext.current.findActivity().window

    DisposableEffect(window) {
        if (secureRequests.getAndIncrement() == 0) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        onDispose {
            if (secureRequests.decrementAndGet() == 0) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

/** How many composables currently want the window protected. */
private val secureRequests = AtomicInteger(0)