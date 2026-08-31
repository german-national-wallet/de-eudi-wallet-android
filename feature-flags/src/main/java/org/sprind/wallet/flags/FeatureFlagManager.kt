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
package org.sprind.wallet.flags

import org.sprind.wallet.flags.models.FeatureFlag
import org.sprind.wallet.flags.models.decodeFlagsFromString

interface FeatureFlagManager {
    fun <T> getFlagValue(flag: FeatureFlag<T>): T

    /**
     * Reloads the in-memory override cache from storage. Call this once after
     * [FeatureFlagUpdateService.refreshFlagsIfNeeded] so a freshly fetched value
     * (e.g. minimum_app_version) is reflected on the same app resume, without
     * re-parsing storage on every [getFlagValue] read.
     */
    fun reloadFlags()
}

/**
 * Implementation of [FeatureFlagManager] that retrieves flag values from storage.
 *
 * @constructor Creates a FeatureFlagManagerImpl with the given storage.
 * @param featureFlagStorage Storage backend for retrieving persisted feature flags.
 */
class FeatureFlagManagerImpl(private val featureFlagStorage: FeatureFlagStorage) : FeatureFlagManager {

    private lateinit var flagOverrides: Map<String, FeatureFlag<*>>

    init {
        reloadFlags()
        featureFlagStorage.setOnFlagsChangedListener { newValue ->
            setFlagOverridesFromString(newValue)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getFlagValue(flag: FeatureFlag<T>): T {
        return (flagOverrides[flag.key]?.value ?: flag.value) as T
    }

    override fun reloadFlags() {
        setFlagOverridesFromString(featureFlagStorage.getStoredFlags())
    }

    private fun setFlagOverridesFromString(jsonString: String?) {
        flagOverrides = if (jsonString.isNullOrEmpty()) {
            emptyMap()
        } else {
            decodeFlagsFromString(jsonString)
        }
    }
}

