/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.businesslogic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateHelperTest {

    @Test
    fun `toDateFormatted should format ISO 8601 with milliseconds using enGB locale`() {
        val result = "2024-03-15T10:30:45.123".toDateFormatted("en-GB")

        assertEquals("15 Mar 2024", result)
    }

    @Test
    fun `toDateFormatted should format ISO 8601 basic using enGB locale`() {
        val result = "2024-03-15T10:30:45".toDateFormatted("en-GB")

        assertEquals("15 Mar 2024", result)
    }

    @Test
    fun `toDateFormatted should format ISO 8601 with milliseconds and timezone using enGB locale`() {
        val result = "2024-03-15T10:30:45.123GMT".toDateFormatted("en-GB")

        assertEquals("15 Mar 2024", result)
    }

    @Test
    fun `toDateFormatted should format ISO 8601 with timezone using enGB locale`() {
        val result = "2024-03-15T10:30:45GMT".toDateFormatted("en-GB")

        assertEquals("15 Mar 2024", result)
    }

    @Test
    fun `toDateFormatted should format ISO date using enGB locale`() {
        val result = "2024-03-15".toDateFormatted("en-GB")

        assertEquals("15 Mar 2024", result)
    }

    @Test
    fun `toDateFormatted should format using German locale with dots`() {
        val result = "2024-03-15T10:30:45.123".toDateFormatted("de-DE")

        assertEquals("15.03.2024", result)
    }

    @Test
    fun `toDateFormatted should format ISO date using German locale with dots`() {
        val result = "2024-03-15".toDateFormatted("de-DE")

        assertEquals("15.03.2024", result)
    }

    @Test
    fun `toDateFormatted should return null for invalid date string`() {
        val result = "not-a-date".toDateFormatted("en-GB")

        assertNull(result)
    }

    @Test
    fun `toDateFormatted should return null for empty string`() {
        val result = "".toDateFormatted("en-GB")

        assertNull(result)
    }

    @Test
    fun `toDateFormatted should return null for blank string`() {
        val result = "   ".toDateFormatted("en-GB")

        assertNull(result)
    }

    @Test
    fun `toDateFormatted should use default locale when not specified`() {
        val result = "2024-12-25".toDateFormatted()

        assertEquals("25 Dec 2024", result)
    }

    @Test
    fun `toLocalDate should parse ISO 8601 with milliseconds`() {
        val result = "2024-03-15T10:30:45.123".toLocalDate("en-GB")

        assertEquals(LocalDate.of(2024, 3, 15), result)
    }

    @Test
    fun `toLocalDate should parse ISO 8601 basic`() {
        val result = "2024-03-15T10:30:45".toLocalDate("en-GB")

        assertEquals(LocalDate.of(2024, 3, 15), result)
    }

    @Test
    fun `toLocalDate should parse ISO date`() {
        val result = "2024-03-15".toLocalDate("en-GB")

        assertEquals(LocalDate.of(2024, 3, 15), result)
    }

    @Test
    fun `toLocalDate should return null for invalid date string`() {
        val result = "not-a-date".toLocalDate("en-GB")

        assertNull(result)
    }

    @Test
    fun `toLocalDate should return null for empty string`() {
        val result = "".toLocalDate("en-GB")

        assertNull(result)
    }

    @Test
    fun `toLocalDate should use default locale when not specified`() {
        val result = "2024-12-25".toLocalDate()

        assertEquals(LocalDate.of(2024, 12, 25), result)
    }
}
