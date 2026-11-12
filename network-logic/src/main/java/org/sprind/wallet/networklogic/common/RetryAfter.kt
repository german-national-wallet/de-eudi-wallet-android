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

import java.time.DateTimeException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The longest delay [parseRetryAfter] returns. The header can come from any server the wallet talks
 * to, including relying parties, so an absurd value must not lock the user out of a flow.
 */
internal val MAX_RETRY_AFTER: Duration = 1.hours

/**
 * Parses an RFC 9110 `Retry-After` header value into the delay to wait from the moment the response
 * was received, or `null` when [retryAfter] is missing or malformed.
 *
 * Both forms are supported:
 * - delay-seconds, a non-negative decimal integer;
 * - an HTTP-date in IMF-fixdate form (what Envoy and Myra send; the obsolete RFC 850 and asctime
 *   forms are not supported).
 *
 * A date is resolved against the [date] header of the same response, so a skewed device clock does
 * not change the delay. Only when that header is missing or unparseable is [now] used instead.
 * The result is clamped to between [Duration.ZERO] (a date in the past) and [MAX_RETRY_AFTER].
 */
internal fun parseRetryAfter(retryAfter: String?, date: String?, now: Instant): Duration? {
    val value = retryAfter?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    val delay = if (value.all { it.isDigit() }) {
        // Too many digits for a Long is still a valid, just very long, delay.
        value.toLongOrNull()?.seconds ?: Duration.INFINITE
    } else {
        val retryAt = value.toHttpDate() ?: return null
        retryAt - (date?.toHttpDate() ?: now)
    }
    return delay.coerceIn(Duration.ZERO, MAX_RETRY_AFTER)
}

/**
 * The parser is lenient and accepts years of up to nine digits, so this goes through epoch seconds,
 * which cannot overflow, rather than epoch milliseconds, which can.
 */
private fun String.toHttpDate(): Instant? = try {
    Instant.fromEpochSeconds(
        ZonedDateTime.parse(trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
    )
} catch (_: DateTimeException) {
    null
}
