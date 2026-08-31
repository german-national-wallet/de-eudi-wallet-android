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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sprind.wallet.flags.api.FeatureFlagApiClient
import org.sprind.wallet.flags.models.flagFormatter
import timber.log.Timber
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

const val FEATURE_FLAGS_LAST_UPDATE = "FEATURE_FLAGS_LAST_UPDATE"
const val MAX_AGE_FLAGS_IN_HOURS = 24

fun interface FeatureFlagUpdateService {
    /**
     * Fetches flags when nothing has been stored yet or the stored flags are older than
     * [MAX_AGE_FLAGS_IN_HOURS], otherwise does nothing and the stored values stay in use.
     */
    suspend fun refreshFlagsIfNeeded()
}

/**
 * Implementation of [FeatureFlagUpdateService] that updates feature flags from the API.
 *
 * @constructor Creates a FeatureFlagUpdateServiceImpl with the given dependencies.
 * @param featureFlagStorage Storage backend for persisting fetched flags and update timestamps.
 * @param featureFlagApiClient Client for fetching flags from the remote API.
 * @param ioDispatcher Coroutine dispatcher for background network operations.
 */
class FeatureFlagUpdateServiceImpl(
    val featureFlagStorage: FeatureFlagStorage,
    val featureFlagApiClient: FeatureFlagApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FeatureFlagUpdateService {

    override suspend fun refreshFlagsIfNeeded() {
        if (isStale()) {
            fetchAndStoreFeatureFlags()
        }
    }

    private fun isStale(): Boolean {
        val storedTime = featureFlagStorage.getLastUpdateTime()
        if (storedTime.isEmpty()) {
            return true
        }
        val lastUpdate = runCatching { Instant.parse(storedTime) }.getOrNull() ?: return true
        return Clock.System.now() - lastUpdate >= MAX_AGE_FLAGS_IN_HOURS.hours
    }

    private suspend fun fetchAndStoreFeatureFlags() = withContext(ioDispatcher) {
        // get latest feature flags from API Endpoint
        featureFlagApiClient
            .fetchFeatureFlags()
            .onSuccess { featureFlagResult ->
                Timber.d("FeatureFlags from API: $featureFlagResult")
                // do not save an empty value but delete overrides
                if (featureFlagResult.isEmpty()) {
                    featureFlagStorage.storeFlags("")
                } else {
                    featureFlagStorage.storeFlags(flagFormatter.encodeToString(featureFlagResult))
                }
                // an empty result is a successful fetch too, so the next one is due in 24 hours
                featureFlagStorage.storeUpdateTime(Clock.System.now().toString())
            }
            .onFailure { exception ->
                Timber.e("Error getting FeatureFlags from API: ${exception.message}")
                Timber.d("Error getting FeatureFlags from API: $exception")
            }
    }
}
