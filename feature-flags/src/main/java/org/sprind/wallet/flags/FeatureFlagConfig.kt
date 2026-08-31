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

/**
 * Configuration holder for the feature-flags module.
 *
 * Provides runtime configuration required by feature-flags functionality without introducing
 * dependencies on business-logic or Android-specific components. This allows the feature-flags
 * module to remain independent and testable.
 *
 * @property isHttpLoggingEnabled True if feature flag requests and responses may be logged to
 *   logcat. Only the flavors we develop and test on log anything, since the bodies end up on
 *   the device of whoever runs the app.
 * @property featureFlagApiBaseUrl Base URL for the feature flag API endpoint. The actual API requests
 *   append query parameters (e.g., apiKey) to this base URL.
 */
class FeatureFlagConfig(
    val isHttpLoggingEnabled: Boolean,
    val featureFlagApiBaseUrl: String
)
