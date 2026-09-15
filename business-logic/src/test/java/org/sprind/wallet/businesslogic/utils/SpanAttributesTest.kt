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

package org.sprind.wallet.businesslogic.utils

import org.junit.Test
import org.sprind.wallet.businesslogic.util.SpanAttributes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpanAttributesTest {

    @Test
    fun `EMPTY is a shared instance and constructors return it for empty input`() {
        val empty1 = SpanAttributes.EMPTY
        val empty2 = SpanAttributes.fromMap(emptyMap())
        val empty3 = SpanAttributes.of()

        assertSame(empty1, empty2)
        assertSame(empty1, empty3)
    }

    @Test
    fun `fromMap copies defensively and preserves insertion order`() {
        val src = mutableMapOf("a" to "1", "b" to "2", "c" to "3")
        val attrs = SpanAttributes.fromMap(src)

        // mutate the original source AFTER creation
        src["a"] = "4"

        // snapshot should not change
        assertNull(attrs["d"])
        assertEquals(listOf("a" to "1", "b" to "2", "c" to "3"), attrs.toList())
    }

    @Test
    fun `of preserves pair order`() {
        val attrs = SpanAttributes.of(
            "platform" to "android",
            "version" to "1.54.1",
            "commit" to "8baf29"
        )

        assertEquals(listOf("platform" to "android", "version" to "1.54.1", "commit" to "8baf29"), attrs.toList())
    }

    @Test
    fun `contains and get work as expected`() {
        val attrs = SpanAttributes.of("env" to "dev", "region" to "eu-west-1")

        assertTrue(attrs.contains("env"))
        assertEquals("dev", attrs["env"])
        assertFalse(attrs.contains("missing"))
        assertNull(attrs["missing"])
    }

    @Test
    fun `withAttribute returns new instance when value changes and same instance when identical`() {
        val base = SpanAttributes.of("env" to "dev")

        val same = base.withAttribute("env", "dev")
        assertSame(base, same, "No-op should return the same instance")

        val updated = base.withAttribute("env", "prod")
        assertNotSame(base, updated)
        assertEquals("prod", updated["env"])
        // order is preserved—existing key keeps its original position
        assertEquals(listOf("env" to "prod"), updated.toList())
    }

    @Test
    fun `plus merges attributes, right side wins on key conflicts, and preserves insertion order semantics`() {
        val left = SpanAttributes.of(
            "a" to "1",
            "b" to "2"
        )
        val right = SpanAttributes.of(
            "b" to "B",     // overwrite
            "c" to "3"      // new key appended at the end
        )

        val merged = left + right

        // Values
        assertEquals("1", merged["a"])
        assertEquals("B", merged["b"])
        assertEquals("3", merged["c"])

        // Order: existing keys keep their original position; new keys from right are appended
        assertEquals(listOf("a" to "1", "b" to "B", "c" to "3"), merged.toList())
    }

    @Test
    fun `plus with EMPTY returns the same instance when possible`() {
        val base = SpanAttributes.of("k" to "v")

        val leftPlusEmpty = base + SpanAttributes.EMPTY
        val emptyPlusRight = SpanAttributes.EMPTY + base

        assertSame(base, leftPlusEmpty)
        // `EMPTY + base` creates a copy (implementation detail), but content must match
        assertEquals(base.toList(), emptyPlusRight.toList())
    }

    @Test
    fun `toMap is read-only view from caller perspective`() {
        val attrs = SpanAttributes.of("k" to "v")
        val view: Map<String, String> = attrs.toMap()

        assertEquals(mapOf("k" to "v"), view)
        // Can't mutate via Map interface; uncommenting below should not compile:
        //view["k"] = "X"
    }

    // --- helpers ---

    private fun SpanAttributes.toList(): List<Pair<String, String>> =
        this.map { it.key to it.value }
}