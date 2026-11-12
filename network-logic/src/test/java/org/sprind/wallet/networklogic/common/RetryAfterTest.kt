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

package org.sprind.wallet.networklogic.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-24T10:00:00Z")
private const val NOW_HTTP_DATE = "Sat, 24 Oct 2026 10:00:00 GMT"

class RetryAfterTest {

    @Test
    fun `delay-seconds is used as is`() {
        assertEquals(120.seconds, parseRetryAfter("120", date = null, now = NOW))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(5.seconds, parseRetryAfter(" 5 ", date = null, now = NOW))
    }

    @Test
    fun `zero seconds means retry right away`() {
        assertEquals(Duration.ZERO, parseRetryAfter("0", date = null, now = NOW))
    }

    @Test
    fun `an IMF-fixdate is resolved against the Date header of the response`() {
        // The device clock is an hour behind the server; only the Date header matters.
        val skewedNow = NOW - 60.minutes

        val delay = parseRetryAfter(
            retryAfter = "Sat, 24 Oct 2026 10:05:00 GMT",
            date = NOW_HTTP_DATE,
            now = skewedNow,
        )

        assertEquals(5.minutes, delay)
    }

    @Test
    fun `an IMF-fixdate falls back to the device clock when there is no Date header`() {
        assertEquals(
            30.seconds,
            parseRetryAfter("Sat, 24 Oct 2026 10:00:30 GMT", date = null, now = NOW),
        )
    }

    @Test
    fun `an IMF-fixdate falls back to the device clock when the Date header is malformed`() {
        assertEquals(
            30.seconds,
            parseRetryAfter("Sat, 24 Oct 2026 10:00:30 GMT", date = "yesterday", now = NOW),
        )
    }

    @Test
    fun `a date in the past means retry right away`() {
        assertEquals(
            Duration.ZERO,
            parseRetryAfter("Sat, 24 Oct 2026 09:00:00 GMT", date = NOW_HTTP_DATE, now = NOW),
        )
    }

    @Test
    fun `a missing or blank header gives no delay`() {
        assertNull(parseRetryAfter(null, date = NOW_HTTP_DATE, now = NOW))
        assertNull(parseRetryAfter("  ", date = NOW_HTTP_DATE, now = NOW))
    }

    @Test
    fun `malformed values give no delay`() {
        listOf("-5", "+5", "1.5", "soon", "Saturday, 24-Oct-26 10:05:00 GMT").forEach {
            assertNull(it, parseRetryAfter(it, date = NOW_HTTP_DATE, now = NOW))
        }
    }

    @Test
    fun `long delays are capped`() {
        assertEquals(MAX_RETRY_AFTER, parseRetryAfter("86400", date = null, now = NOW))
        // Does not fit in a Long.
        assertEquals(MAX_RETRY_AFTER, parseRetryAfter("99999999999999999999", date = null, now = NOW))
        assertEquals(
            MAX_RETRY_AFTER,
            parseRetryAfter("Sun, 25 Oct 2026 10:00:00 GMT", date = NOW_HTTP_DATE, now = NOW),
        )
    }

    /** The lenient parser accepts a nine-digit year, which overflows epoch milliseconds. */
    @Test
    fun `a date with a huge year is capped instead of throwing`() {
        assertEquals(
            MAX_RETRY_AFTER,
            parseRetryAfter("24 Oct 999999999 10:00:00 GMT", date = NOW_HTTP_DATE, now = NOW),
        )
    }

    @Test
    fun `a Date header with a huge year does not throw`() {
        assertEquals(
            Duration.ZERO,
            parseRetryAfter(NOW_HTTP_DATE, date = "24 Oct 999999999 10:00:00 GMT", now = NOW),
        )
    }
}
