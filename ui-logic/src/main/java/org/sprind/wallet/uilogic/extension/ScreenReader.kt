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

package org.sprind.wallet.uilogic.extension

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether a screen reader that explores by touch (TalkBack) is on, kept up to date while the
 * composable is in the composition.
 *
 * Meant for timing, e.g. leaving a screen only after a message has been spoken. It is not a reason
 * to change what the screen offers: anything a screen reader user needs must also work without it.
 */
@Composable
fun rememberIsScreenReaderEnabled(): Boolean {
    val accessibilityManager = LocalContext.current.getSystemService(AccessibilityManager::class.java)
    var isEnabled by remember { mutableStateOf(accessibilityManager.isTouchExplorationEnabled) }

    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { isEnabled = it }
        accessibilityManager.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibilityManager.removeTouchExplorationStateChangeListener(listener) }
    }

    return isEnabled
}
