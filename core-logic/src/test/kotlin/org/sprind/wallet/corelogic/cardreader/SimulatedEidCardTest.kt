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
package org.sprind.wallet.corelogic.cardreader

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sprind.wallet.businesslogic.config.SimulatedEidCardKeyGenerator

class SimulatedEidCardTest {

    @Test
    fun `given a pseudonym key, when creating the card, then it carries the default files and key 2`() {
        val card = SimulatedEidCard.create("abcd")

        assertEquals(SimulatedEidCard.defaultFiles, card.files)
        val key = requireNotNull(card.keys).single()
        assertEquals(SimulatedEidCardKeyGenerator.PSEUDONYM_KEY_ID, key.id)
        assertEquals("abcd", key.content)
    }
}