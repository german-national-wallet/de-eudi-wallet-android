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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock

class UserRuntimeConfigImplTest {

    @Test
    fun `a build without the simulated eID card keeps the card type physical`() {
        val subject = subject(simulatedEidCardEnabled = false)

        subject.eidCardType = EidCardType.VIRTUAL

        Assert.assertEquals(EidCardType.PHYSICAL, subject.eidCardType)
    }

    @Test
    fun `the card type starts physical even when the simulated eID card is enabled`() {
        val subject = subject(simulatedEidCardEnabled = true)

        Assert.assertEquals(EidCardType.PHYSICAL, subject.eidCardType)
    }

    @Test
    fun `a build with the simulated eID card accepts a virtual card type`() {
        val subject = subject(simulatedEidCardEnabled = true)

        subject.eidCardType = EidCardType.VIRTUAL

        Assert.assertEquals(EidCardType.VIRTUAL, subject.eidCardType)
    }

    @Test
    fun `switching the simulated card on makes a pseudonym key`() {
        val subject = subject(simulatedEidCardEnabled = true)

        Assert.assertNull(subject.simulatedEidCardPseudonymKey)
        subject.eidCardType = EidCardType.VIRTUAL

        Assert.assertEquals("key-1", subject.simulatedEidCardPseudonymKey)
    }

    @Test
    fun `the simulated card keeps its key while it stays on`() {
        val subject = subject(simulatedEidCardEnabled = true)

        subject.eidCardType = EidCardType.VIRTUAL
        subject.eidCardType = EidCardType.VIRTUAL

        Assert.assertEquals("key-1", subject.simulatedEidCardPseudonymKey)
    }

    @Test
    fun `switching the simulated card off drops the key and on again makes a new one`() {
        val subject = subject(simulatedEidCardEnabled = true)

        subject.eidCardType = EidCardType.VIRTUAL
        subject.eidCardType = EidCardType.PHYSICAL
        Assert.assertNull(subject.simulatedEidCardPseudonymKey)

        subject.eidCardType = EidCardType.VIRTUAL
        Assert.assertEquals("key-2", subject.simulatedEidCardPseudonymKey)
    }

    @Test
    fun `a build without the simulated eID card makes no pseudonym key`() {
        val subject = subject(simulatedEidCardEnabled = false)

        subject.eidCardType = EidCardType.VIRTUAL

        Assert.assertNull(subject.simulatedEidCardPseudonymKey)
    }

    @Test
    fun `a failed key generation keeps the card physical`() {
        val keyGenerator = mock<SimulatedEidCardKeyGenerator> {
            on { generatePseudonymKeyHex() } doThrow SimulatedEidCardKeyException("no key")
        }
        val subject = UserRuntimeConfigImpl(configLogic(simulatedEidCardEnabled = true), keyGenerator)

        Assert.assertThrows(SimulatedEidCardKeyException::class.java) {
            subject.eidCardType = EidCardType.VIRTUAL
        }

        Assert.assertEquals(EidCardType.PHYSICAL, subject.eidCardType)
        Assert.assertNull(subject.simulatedEidCardPseudonymKey)
    }

    private fun subject(simulatedEidCardEnabled: Boolean): UserRuntimeConfigImpl {
        var generated = 0
        val keyGenerator = mock<SimulatedEidCardKeyGenerator> {
            on { generatePseudonymKeyHex() } doAnswer { "key-${++generated}" }
        }
        return UserRuntimeConfigImpl(configLogic(simulatedEidCardEnabled), keyGenerator)
    }

    private fun configLogic(simulatedEidCardEnabled: Boolean) = object : ConfigLogic {
        override val environmentConfig: EnvironmentConfig = EnvironmentConfigImpl(LogConfigImpl())
        override val isSimulatedEidCardEnabled: Boolean = simulatedEidCardEnabled
    }
}
