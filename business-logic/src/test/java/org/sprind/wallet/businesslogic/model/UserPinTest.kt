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

package org.sprind.wallet.businesslogic.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPinTest {

    @Test
    fun `getAndClear returns correct characters`() {
        val pin = UserPinImpl("1234")

        pin.getAndClear().use {
            assertArrayEquals(charArrayOf('1', '2', '3', '4'), it.chars)
        }
    }

    @Test
    fun `getAndClear throws IllegalStateException on subsequent calls`() {
        val pin = UserPinImpl("1234567")

        val highlySensitivePinData = pin.getAndClear()
        assertThrows(IllegalStateException::class.java) {
            pin.getAndClear()
        }
        assertThrows(IllegalStateException::class.java) {
            pin.getAndClear()
        }
        highlySensitivePinData.close()
    }

    @Test
    fun `empty pin works`() {
        val pin = UserPinImpl("")

        pin.getAndClear().use { result ->
            assertTrue(result.chars.isEmpty())
        }
    }

    @Test
    fun `supports alphanumeric characters`() {
        val pin = UserPinImpl("123aBc0")

        pin.getAndClear().use { result ->
            assertArrayEquals(charArrayOf('1', '2', '3', 'a', 'B', 'c', '0'), result.chars)
        }
    }

    @Test
    fun `supports arbitrary characters including unicode`() {
        val pin = UserPinImpl("Ťėşŧ_(π")

        pin.getAndClear().use { result ->
            assertArrayEquals(charArrayOf('Ť', 'ė', 'ş', 'ŧ', '_', '(', 'π'), result.chars)
        }
    }

    @Test
    fun `toString does not include the pin test`() {
        val pin = UserPinImpl("12345")

        val result = pin.toString()

        assertFalse(result.contains("12345"))
    }

    @Test
    fun `equals is based on content`() {
        val pinA = UserPinImpl("123456")
        val pinB = UserPinImpl("123456")
        val pinC = UserPinImpl("654321")

        assertEquals(pinA, pinB)
        assertNotEquals(pinA, pinC)
    }

    @Test
    fun `hashCode is not based on content`() {
        val pin = UserPinImpl("123456")

        assertEquals(0, pin.hashCode())
    }
}