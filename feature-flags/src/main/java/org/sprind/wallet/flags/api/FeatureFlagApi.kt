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
package org.sprind.wallet.flags.api

import org.sprind.wallet.flags.models.FeatureFlagOverrides
import retrofit2.Response
import retrofit2.http.GET

interface FeatureFlagApi {

    @GET("/features")
    suspend fun fetchFeatureFlags(): Response<List<FeatureFlagOverrides>>
}

interface FeatureFlagApiClient {
    suspend fun fetchFeatureFlags(): Result<List<FeatureFlagOverrides>>
}

class FeatureFlagApiClientImpl(private val featureFlagApi: FeatureFlagApi) : FeatureFlagApiClient {
    override suspend fun fetchFeatureFlags(): Result<List<FeatureFlagOverrides>> {
        return try {
            // Fetch FeatureFlags from API, try to parse them. If the response is a valid
            // FeatureFlag response, return them to caller.
            val response = featureFlagApi.fetchFeatureFlags()
            val featureFlags = response.body() as List<FeatureFlagOverrides>

            if (response.isSuccessful) {
                Result.success(featureFlags)
            } else {
                Result.failure(IllegalStateException("Unable to fetch feature flags from API: ${response.errorBody()}"))
            }

        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}