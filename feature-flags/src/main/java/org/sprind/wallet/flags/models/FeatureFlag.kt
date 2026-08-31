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
package org.sprind.wallet.flags.models

const val SAVED_FEATURE_FLAGS_OVERRIDE = "SAVED_FEATURE_FLAGS_OVERRIDE"

class FeatureFlag<T>(
    val key: String,
    val value: T,
) {
    companion object {
        val minimumAppVersion = FeatureFlag(
            key = "minimum_app_version",
            value = "0.0.0",
        )
    }
}
