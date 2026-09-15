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

package org.sprind.wallet.uilogic.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeEntryBufferTest {

    @Test
    fun `a code must have at least one digit`() {
        assertThrows(IllegalArgumentException::class.java) { CodeEntryBuffer(0) }
    }

    @Test
    fun `typed digits are readable per cell`() {
        val buffer = CodeEntryBuffer(6).apply { type("123") }

        assertEquals(3, buffer.length)
        assertFalse(buffer.isComplete)
        assertEquals('1', buffer.digitAt(0))
        assertEquals('3', buffer.digitAt(2))
        assertNull(buffer.digitAt(3))
        assertNull(buffer.digitAt(-1))
    }

    @Test
    fun `digits past the capacity are ignored`() {
        val buffer = CodeEntryBuffer(4).apply { type("123456") }

        assertEquals(4, buffer.length)
        assertTrue(buffer.isComplete)
        assertNull(buffer.digitAt(4))
    }

    @Test
    fun `wiping leaves nothing to read`() {
        val buffer = CodeEntryBuffer(6).apply { type("123456") }

        buffer.wipe()

        assertEquals(0, buffer.length)
        assertNull(buffer.digitAt(0))
    }

    @Test
    fun `a shorter code does not leave the tail of a longer one behind`() {
        val buffer = CodeEntryBuffer(6)
        buffer.set("123456")

        buffer.set("12")

        assertEquals(2, buffer.length)
        assertNull(buffer.digitAt(2))
        // Reading the code out is the only window onto the array, so a surviving tail would show up
        // here as digits the user has already deleted.
        buffer.consumeAsPin().getAndClear().use { assertEquals("12", String(it.chars)) }
    }

    @Test
    fun `every change bumps the revision, so that in-place edits are observable`() {
        val buffer = CodeEntryBuffer(6)
        val initial = buffer.revision

        buffer.set("123456")
        val afterFill = buffer.revision
        buffer.set("999999")
        val afterOverwrite = buffer.revision

        assertTrue(afterFill > initial)
        assertTrue("an edit that keeps the length must still be visible", afterOverwrite > afterFill)
    }

    @Test
    fun `setting the same digits changes nothing`() {
        val buffer = CodeEntryBuffer(6)
        buffer.set("123456")
        val revision = buffer.revision

        buffer.set("123456")

        assertEquals(revision, buffer.revision)
    }

    @Test
    fun `buffers holding the same digits are content equal`() {
        val entry = CodeEntryBuffer(6).apply { type("123456") }
        val confirmation = CodeEntryBuffer(6).apply { type("123456") }
        val mismatch = CodeEntryBuffer(6).apply { type("123455") }
        val prefix = CodeEntryBuffer(6).apply { type("1234") }

        assertTrue(entry.contentEquals(confirmation))
        assertFalse(entry.contentEquals(mismatch))
        assertFalse(entry.contentEquals(prefix))
    }

    @Test
    fun `copyFrom replaces the digits with another buffer's`() {
        val source = CodeEntryBuffer(6).apply { type("123456") }
        val target = CodeEntryBuffer(6).apply { type("99") }

        target.copyFrom(source)

        assertTrue(target.contentEquals(source))
        assertEquals(6, target.length)
    }

    @Test
    fun `consuming yields the code and empties the buffer`() {
        val buffer = CodeEntryBuffer(6).apply { type("123456") }

        val pin = buffer.consumeAsPin()

        assertEquals(0, buffer.length)
        assertEquals(6, pin.length)
        pin.getAndClear().use { assertEquals("123456", String(it.chars)) }
    }

    @Test
    fun `taking a pin without consuming leaves the buffer usable`() {
        val buffer = CodeEntryBuffer(6).apply { type("123456") }

        val first = buffer.toPin()
        val second = buffer.consumeAsPin()

        first.getAndClear().use { assertEquals("123456", String(it.chars)) }
        second.getAndClear().use { assertEquals("123456", String(it.chars)) }
        assertEquals(0, buffer.length)
    }

    @Test
    fun `toString does not disclose the digits`() {
        val buffer = CodeEntryBuffer(6).apply { type("123456") }

        assertFalse(buffer.toString().contains("123456"))
    }
}