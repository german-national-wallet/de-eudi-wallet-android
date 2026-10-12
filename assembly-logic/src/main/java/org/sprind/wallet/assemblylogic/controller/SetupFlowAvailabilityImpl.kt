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
package org.sprind.wallet.assemblylogic.controller

import org.sprind.wallet.flags.FeatureFlagManager
import org.sprind.wallet.flags.models.FeatureFlag
import org.sprind.wallet.onboardingfeature.setup.provider.SetupFlowAvailability

/** Enables the app setup flow through the [FeatureFlag.appSetupScreens] remote flag. */
class SetupFlowAvailabilityImpl(
    private val featureFlagManager: FeatureFlagManager,
) : SetupFlowAvailability {

    // FeatureHub may deliver a non-boolean value for a misconfigured flag; read it untyped so that
    // it counts as disabled instead of failing a cast.
    override fun isEnabled(): Boolean {
        @Suppress("UNCHECKED_CAST")
        val raw: Any? = featureFlagManager.getFlagValue(FeatureFlag.appSetupScreens as FeatureFlag<Any?>)
        return raw == true
    }
}
