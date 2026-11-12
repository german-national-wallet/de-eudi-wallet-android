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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedEidCardKeyGeneratorTest {

    private val ausweisAppDefaultPseudonymKey =
        "308202050201003081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d0101022100a9fb57dba1eea9bc3e660a909d838d726e3bf623d5" +
            "2620282013481d1f6e5377304404207d5a0975fc2c3057eef67530417affe7fb8055c126dc5c6ce94a4b44f330b5d9042026dc5c6ce94a4b44f330b5" +
            "d9bbd77cbf958416295cf7e1ce6bccdc18ff8c07b60441048bd2aeb9cb7e57cb2c4b482ffc81b7afb9de27e1e3bd23c23a4453bd9ace3262547ef835" +
            "c3dac4fd97f8461a14611dc9c27745132ded8e545c1d54c72f046997022100a9fb57dba1eea9bc3e660a909d838d718c397aa3b561a6f7901e0e8297" +
            "4856a70201010482010f3082010b02010104209ad0ad7f4dfaaa06988339fc31d3a111f4c7964ac7f377373a2454327c43e2ffa081e33081e0020101" +
            "302c06072a8648ce3d0101022100a9fb57dba1eea9bc3e660a909d838d726e3bf623d52620282013481d1f6e5377304404207d5a0975fc2c3057eef6" +
            "7530417affe7fb8055c126dc5c6ce94a4b44f330b5d9042026dc5c6ce94a4b44f330b5d9bbd77cbf958416295cf7e1ce6bccdc18ff8c07b60441048b" +
            "d2aeb9cb7e57cb2c4b482ffc81b7afb9de27e1e3bd23c23a4453bd9ace3262547ef835c3dac4fd97f8461a14611dc9c27745132ded8e545c1d54c72f" +
            "046997022100a9fb57dba1eea9bc3e660a909d838d718c397aa3b561a6f7901e0e82974856a7020101"

    private val ausweisAppDefaultScalarHex =
        "9ad0ad7f4dfaaa06988339fc31d3a111f4c7964ac7f377373a2454327c43e2ff"

    private val ausweisAppDefaultScalar = ausweisAppDefaultScalarHex.hexToByteArray()

    private val curveOrder =
        "a9fb57dba1eea9bc3e660a909d838d718c397aa3b561a6f7901e0e82974856a7".hexToByteArray()

    @Test
    fun `given the default key scalar, when generating, then reproduces the AusweisApp default key`() {
        val sut = SimulatedEidCardKeyGenerator(randomBytes = { ausweisAppDefaultScalar })

        assertEquals(ausweisAppDefaultPseudonymKey, sut.generatePseudonymKeyHex())
    }

    @Test
    fun `given secure random, when generating twice, then only the scalar differs`() {
        val sut = SimulatedEidCardKeyGenerator()

        val first = sut.generatePseudonymKeyHex()
        val second = sut.generatePseudonymKeyHex()

        assertNotEquals(first, second)
        assertEquals(first.length, second.length)
        val scalarStart = ausweisAppDefaultPseudonymKey.indexOf(ausweisAppDefaultScalarHex)
        val scalarEnd = scalarStart + SimulatedEidCardKeyGenerator.SCALAR_LENGTH * 2
        assertEquals(first.take(scalarStart), second.take(scalarStart))
        assertEquals(first.substring(scalarEnd), second.substring(scalarEnd))
    }

    @Test
    fun `given out of range scalars first, when generating, then retries until a valid one`() {
        val scalars = ArrayDeque(
            listOf(
                ByteArray(32),
                curveOrder,
                ByteArray(32) { 0xff.toByte() },
                ByteArray(32) { 0x01 },
            )
        )
        val sut = SimulatedEidCardKeyGenerator(randomBytes = { scalars.removeFirst() })

        val key = sut.generatePseudonymKeyHex()

        assertTrue(scalars.isEmpty())
        assertTrue(key.contains("01".repeat(32)))
    }

    @Test
    fun `given the random source returns the wrong length, when generating, then throws`() {
        val sut = SimulatedEidCardKeyGenerator(randomBytes = { ByteArray(16) { 0x01 } })

        assertThrows(SimulatedEidCardKeyException::class.java) { sut.generatePseudonymKeyHex() }
    }

    @Test
    fun `given the random source never yields a valid scalar, when generating, then throws`() {
        val sut = SimulatedEidCardKeyGenerator(randomBytes = { ByteArray(32) })

        assertThrows(SimulatedEidCardKeyException::class.java) { sut.generatePseudonymKeyHex() }
    }

    @Test
    fun `given order minus one, when validating, then is valid`() {
        val scalar = curveOrder.copyOf().also { it[it.lastIndex] = (it[it.lastIndex] - 1).toByte() }

        assertTrue(SimulatedEidCardKeyGenerator.isValidScalar(scalar))
        assertFalse(SimulatedEidCardKeyGenerator.isValidScalar(curveOrder))
    }
}