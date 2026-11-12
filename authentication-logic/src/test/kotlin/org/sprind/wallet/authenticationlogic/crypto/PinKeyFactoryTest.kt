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
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.junit.Test
import java.math.BigInteger
import java.security.interfaces.ECPrivateKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `generatePinKeys derives PBKDF2 of pin and salt with counter zero`() {
        // Same vector as the iOS SecureEnclaveControllerTests
        val keyPair = subject.generatePinKeys(makePin(), "000102030405060708090a0b0c0d0e0f".decodeHex())

        assertEquals(
            BigInteger("f607e1d743ea31b36e4de666151e9f909bb14fab632ed11f8e6f5570706af9f1", 16),
            (keyPair.private as ECPrivateKey).s,
        )
    }

    @Test
    fun `generatePinKeys re-derives with an incremented counter while the candidate is out of range`() {
        val valid = ByteArray(32).also { it[31] = 7 }
        val candidates = listOf(ByteArray(32), P256_ORDER.toByteArray().takeLast(32).toByteArray(), valid)
        val saltsSeen = mutableListOf<ByteString>()
        val subject = PinKeyFactoryImpl { _, salt ->
            saltsSeen.add(salt.toByteString())
            candidates[saltsSeen.size - 1].copyOf()
        }

        val keyPair = subject.generatePinKeys(makePin(), salt)

        assertEquals(BigInteger.valueOf(7), (keyPair.private as ECPrivateKey).s)
        assertEquals(
            listOf("00000000", "00000001", "00000002").map { salt + it.decodeHex() },
            saltsSeen,
        )
    }

    @Test
    fun `generatePinKeys accepts n minus 1 as the first candidate`() {
        val nMinusOne = P256_ORDER - BigInteger.ONE
        var attempts = 0
        val subject = PinKeyFactoryImpl { _, _ ->
            attempts++
            nMinusOne.toByteArray().takeLast(32).toByteArray()
        }

        val keyPair = subject.generatePinKeys(makePin(), salt)

        assertEquals(nMinusOne, (keyPair.private as ECPrivateKey).s)
        assertEquals(1, attempts)
    }

    @Test
    fun `generatePinKeys throws PinKeyDerivationException after the bounded number of attempts`() {
        var attempts = 0
        val subject = PinKeyFactoryImpl { _, _ ->
            attempts++
            ByteArray(32) { 0xFF.toByte() }
        }
        val pin = makePin()

        assertFailsWith<PinKeyDerivationException> { subject.generatePinKeys(pin, salt) }
        assertEquals(3, attempts)
        assertEquals("XXXXXX", String(pin.lastCharsReturned()!!))
    }

    private operator fun ByteString.plus(other: ByteString) = Buffer().write(this).write(other).readByteString()

    private fun makePin() = TestUserPin("123456")
}

private val P256_ORDER = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
