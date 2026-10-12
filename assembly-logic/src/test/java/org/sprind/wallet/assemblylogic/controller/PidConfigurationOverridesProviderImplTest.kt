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

import org.junit.Test
import org.sprind.wallet.corelogic.config.PidConfigurationOverrides
import org.sprind.wallet.flags.FeatureFlagManager
import org.sprind.wallet.flags.models.FeatureFlag
import kotlin.test.assertEquals

class PidConfigurationOverridesProviderImplTest {

    @Test
    fun `overrides are null when the flags are not set`() {
        assertEquals(PidConfigurationOverrides(), provider().overrides())
    }

    @Test
    fun `overrides carry the trimmed flag values`() {
        val provider = provider(
            "credential_configuration_id_mdoc" to " pid-mso-mdoc_3 ",
            "credential_configuration_id_sdjwt" to "pid-sd-jwt_3",
        )

        assertEquals(
            PidConfigurationOverrides(mdoc = "pid-mso-mdoc_3", sdJwt = "pid-sd-jwt_3"),
            provider.overrides(),
        )
    }

    @Test
    fun `blank or non-string flag values are no override`() {
        val provider = provider(
            "credential_configuration_id_mdoc" to "   ",
            "credential_configuration_id_sdjwt" to 42,
        )

        assertEquals(PidConfigurationOverrides(), provider.overrides())
    }

    @Test
    fun `empty flag values are no override`() {
        val provider = provider(
            "credential_configuration_id_mdoc" to "",
            "credential_configuration_id_sdjwt" to "",
        )

        assertEquals(PidConfigurationOverrides(), provider.overrides())
    }

    private fun provider(vararg flags: Pair<String, Any>) =
        PidConfigurationOverridesProviderImpl(MapFeatureFlagManager(flags.toMap()))
}

private class MapFeatureFlagManager(private val flags: Map<String, Any>) : FeatureFlagManager {

    @Suppress("UNCHECKED_CAST")
    override fun <T> getFlagValue(flag: FeatureFlag<T>): T = (flags[flag.key] ?: flag.value) as T

    override fun reloadFlags() = Unit
}
