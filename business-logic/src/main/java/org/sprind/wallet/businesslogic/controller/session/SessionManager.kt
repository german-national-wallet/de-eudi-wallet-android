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

package org.sprind.wallet.businesslogic.controller.session

import eu.europa.ec.businesslogic.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SessionManager {
    val isUnlocked: StateFlow<Boolean>
    fun lock()
    fun unlock()
}

class SessionManagerImpl : SessionManager {
    private val _isUnlocked = MutableStateFlow(false)
    @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
    override val isUnlocked: StateFlow<Boolean> =
    if (!BuildConfig.ENABLE_EAA_BIOMETRICS && BuildConfig.FLAVOR == BuildConfig.FLAVOR_NAME_DEV) {
        MutableStateFlow(true).asStateFlow()
    } else {
        _isUnlocked.asStateFlow()
    }

    override fun lock() {
        _isUnlocked.value = false
    }

    override fun unlock() {
        _isUnlocked.value = true
    }
}
