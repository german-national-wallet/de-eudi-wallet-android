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

package org.sprind.wallet.authenticationlogic.crypto

import org.sprind.wallet.authenticationlogic.testing.TestUserPin
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinKeyFactoryTest {
    private val subject = PinKeyFactoryImpl()
    private val salt = "fake salt for test".encodeUtf8()

    @Test
    fun `generatePinSalt should return 16 bytes`() {
        val generatedSalt = subject.generatePinSalt()
        assertEquals(16, generatedSalt.size)
    }

    @Test
    fun `generatePinSalt should return different bytes every time`() {
        val generatedSalts = mutableSetOf<ByteString>()
        for (i in 1..100) {
            generatedSalts.add(subject.generatePinSalt())
        }
        assertEquals(100, generatedSalts.size)
    }

    @Test
    fun `geneneratePinKeys should return the same key pair for the same input`() {
        val firstKeyPairWithSameInput = subject.generatePinKeys(makePin(), salt)
        val secondKeyPaitWithSameInput = subject.generatePinKeys(makePin(), salt)

        assertTrue { firstKeyPairWithSameInput.private == secondKeyPaitWithSameInput.private }
        assertTrue { firstKeyPairWithSameInput.public == secondKeyPaitWithSameInput.public }
    }

    @Test
    fun `geneneratePinKeys should return different key pairs for different PIN`() {
        val firstKeyPairWithSameInput = subject.generatePinKeys(TestUserPin("123456"), salt)
        val secondKeyPaitWithSameInput = subject.generatePinKeys(TestUserPin("654321"), salt)

        assertFalse { firstKeyPairWithSameInput.private == secondKeyPaitWithSameInput.private }
        assertFalse { firstKeyPairWithSameInput.public == secondKeyPaitWithSameInput.public }
    }

    @Test
    fun `generatePinKeys should return different key pairs for different salt`() {
        val firstKeyPairWithSameInput = subject.generatePinKeys(makePin(), "bland".encodeUtf8())
        val secondKeyPaitWithSameInput = subject.generatePinKeys(makePin(), "spicy".encodeUtf8())

        assertFalse { firstKeyPairWithSameInput.private == secondKeyPaitWithSameInput.private }
        assertFalse { firstKeyPairWithSameInput.public == secondKeyPaitWithSameInput.public }
    }

    @Test
    fun `geneneratePinKeys should getAndClear the pin and overwrite the characters`() {
        val pin = makePin()

        subject.generatePinKeys(pin, salt)

        assertEquals("XXXXXX", String(pin.lastCharsReturned()!!))
    }

    private fun makePin() = TestUserPin("123456")
}
