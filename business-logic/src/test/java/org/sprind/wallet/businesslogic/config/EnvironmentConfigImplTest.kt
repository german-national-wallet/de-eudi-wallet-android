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

package org.sprind.wallet.businesslogic.config

import org.junit.Assert
import org.junit.Test
import java.net.URI

class EnvironmentConfigImplTest {
    /**
     * This URL is used as the `baseUrl` for Retrofit, which requires the URL to end in '/'.
     */
    @Test
    fun `featureFlagApiBaseUrl ends in slash`() {
        Assert.assertTrue(
            EnvironmentConfigImpl().featureFlagApiBaseUrl.endsWith("/")
        )
    }

    /**
     * Runs against whichever flavor is being tested, so it holds every flavor - including
     * ones added later - to the rule that log files must stay exportable: they can only be
     * shared through the debug menu, so writing them without one would leave them stranded
     * on the user's device.
     */
    @Test
    fun `a flavor writing log files also carries the debug menu to export them`() {
        val config = EnvironmentConfigImpl()

        if (config.enableLogWriter) {
            Assert.assertTrue(config.enableDebugMenu)
        }
    }

    @Test
    fun `the wallet backend host is covered by a pinned pattern`() {
        val config = EnvironmentConfigImpl()
        val host = URI(config.serverHostURL).host

        val isPinned = config.certificatePinnerSpecs.any { spec ->
            val suffix = spec.pattern.removePrefix("**.").removePrefix("*.")
            host == suffix || host.endsWith(".$suffix")
        }

        Assert.assertTrue("No pinned pattern covers $host", isPinned)
    }
}
