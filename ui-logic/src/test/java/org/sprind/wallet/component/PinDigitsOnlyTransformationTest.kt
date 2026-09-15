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

package org.sprind.wallet.component

import androidx.compose.foundation.text.input.TextFieldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PinDigitsOnlyTransformationTest {

    @Test
    fun `negative pinLength throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            PinDigitsOnlyTransformation(-1)
        }
    }

    @Test
    fun `pinLength 0 truncates to empty`() {
        val actual = transform("1a2b3c", pinLength = 0)

        assertEquals("", actual)
    }

    @Test
    fun `transformInput removes non-digits`() {
        val actual = transform("1a2b3c", pinLength = 6)

        assertEquals("123", actual)
    }

    @Test
    fun `transformInput truncates to max length`() {
        val actual = transform("123456789", pinLength = 6)

        assertEquals("123456", actual)
    }

    @Test
    fun `transformInput handles mixed overflow and non-digits`() {
        // "123" + "abc" + "4567" = length 10, of which 7 digits 1234567.
        // Should remove letters then truncate to length 6 (not the other way around)
        val actual = transform("123abc4567", pinLength = 6)

        assertEquals("123456", actual)
    }

    private fun transform(input: String, pinLength: Int): String {
        val transformation = PinDigitsOnlyTransformation(pinLength)
        val state = TextFieldState()
        state.edit {
            append(input)
            with(transformation) { transformInput() }
        }
        return state.text.toString()
    }
}