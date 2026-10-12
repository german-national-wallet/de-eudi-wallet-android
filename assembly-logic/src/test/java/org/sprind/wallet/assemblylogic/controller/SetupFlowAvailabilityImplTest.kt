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
import org.sprind.wallet.flags.FeatureFlagManager
import org.sprind.wallet.flags.models.FeatureFlag
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupFlowAvailabilityImplTest {

    @Test
    fun `given the flag is not set then the setup flow is disabled`() {
        assertFalse(availability().isEnabled())
    }

    @Test
    fun `given the flag is on then the setup flow is enabled`() {
        assertTrue(availability(true).isEnabled())
    }

    @Test
    fun `given the flag is off then the setup flow is disabled`() {
        assertFalse(availability(false).isEnabled())
    }

    @Test
    fun `given a non-boolean flag value then the setup flow is disabled`() {
        assertFalse(availability("true").isEnabled())
    }

    private fun availability(value: Any? = null) =
        SetupFlowAvailabilityImpl(SingleFlagManager(FeatureFlag.appSetupScreens.key, value))
}

private class SingleFlagManager(
    private val key: String,
    private val value: Any?,
) : FeatureFlagManager {

    @Suppress("UNCHECKED_CAST")
    override fun <T> getFlagValue(flag: FeatureFlag<T>): T =
        (if (flag.key == key && value != null) value else flag.value) as T

    override fun reloadFlags() = Unit
}
