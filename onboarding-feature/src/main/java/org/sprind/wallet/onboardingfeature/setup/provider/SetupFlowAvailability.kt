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

package org.sprind.wallet.onboardingfeature.setup.provider

/**
 * Whether the app setup flow is shown at all.
 *
 * Backed by a remote feature flag, implemented where the feature flags are available, so this
 * module does not depend on them. When disabled, every setup step guard points straight to the
 * dashboard and the setup screens are never reached.
 */
fun interface SetupFlowAvailability {
    fun isEnabled(): Boolean
}
