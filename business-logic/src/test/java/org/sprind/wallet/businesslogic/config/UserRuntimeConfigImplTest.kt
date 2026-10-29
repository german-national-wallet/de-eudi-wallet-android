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

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.config.EnvironmentConfig
import eu.europa.ec.businesslogic.config.LogConfigImpl
import org.junit.Assert
import org.junit.Test

class UserRuntimeConfigImplTest {

    @Test
    fun `a build without the simulated eID card keeps the card type physical`() {
        val subject = UserRuntimeConfigImpl(configLogic(simulatedEidCardEnabled = false))

        subject.eidCardType = EidCardType.VIRTUAL

        Assert.assertEquals(EidCardType.PHYSICAL, subject.eidCardType)
    }

    @Test
    fun `the card type starts physical even when the simulated eID card is enabled`() {
        val subject = UserRuntimeConfigImpl(configLogic(simulatedEidCardEnabled = true))

        Assert.assertEquals(EidCardType.PHYSICAL, subject.eidCardType)
    }

    @Test
    fun `a build with the simulated eID card accepts a virtual card type`() {
        val subject = UserRuntimeConfigImpl(configLogic(simulatedEidCardEnabled = true))

        subject.eidCardType = EidCardType.VIRTUAL

        Assert.assertEquals(EidCardType.VIRTUAL, subject.eidCardType)
    }

    private fun configLogic(simulatedEidCardEnabled: Boolean) = object : ConfigLogic {
        override val environmentConfig: EnvironmentConfig = EnvironmentConfigImpl(LogConfigImpl())
        override val isSimulatedEidCardEnabled: Boolean = simulatedEidCardEnabled
    }
}
