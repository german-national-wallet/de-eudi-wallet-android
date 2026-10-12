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

import org.sprind.wallet.corelogic.config.PidConfigurationOverrides
import org.sprind.wallet.corelogic.config.PidConfigurationOverridesProvider
import org.sprind.wallet.flags.FeatureFlagManager
import org.sprind.wallet.flags.getNonBlankStringValue
import org.sprind.wallet.flags.models.FeatureFlag

class PidConfigurationOverridesProviderImpl(
    private val featureFlagManager: FeatureFlagManager,
) : PidConfigurationOverridesProvider {

    override fun overrides() = PidConfigurationOverrides(
        mdoc = read(FeatureFlag.pidMsoMdocConfigId(fallback = "")),
        sdJwt = read(FeatureFlag.pidSdJwtConfigId(fallback = "")),
    )

    private fun read(flag: FeatureFlag<String>): String? =
        featureFlagManager.getNonBlankStringValue(flag).ifEmpty { null }
}
