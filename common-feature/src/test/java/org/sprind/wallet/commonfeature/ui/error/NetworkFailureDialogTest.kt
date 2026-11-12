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

package org.sprind.wallet.commonfeature.ui.error

import eu.europa.ec.resourceslogic.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sprind.wallet.networklogic.common.NetworkFailure
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Instant

private val RETRY_AT = Instant.parse("2026-10-24T10:05:00Z")
private val BERLIN = ZoneId.of("Europe/Berlin")

class NetworkFailureDialogTest {

    private var retries = 0
    private var dismissals = 0

    @Test
    fun `a timeout shows the slow internet dialog, whose button retries`() {
        val config = NetworkFailure.Timeout.toConfig()

        assertEquals(R.string.global_timeout_slow_internet_error_title, config.titleRes)
        assertEquals(R.string.global_timeout_slow_internet_error_paragraph, config.bodyTextRes)
        assertTrue(config.bodyTextArgs.isEmpty())
        assertEquals(R.string.global_timeout_slow_internet_error_prim_button, config.primaryButtonTextRes)
        assertNull(config.secondaryButtonTextRes)
        assertNull(config.errorCode)
        assertNull(config.traceId)

        config.onPrimaryButtonClick()
        config.onDismiss()
        assertEquals(1, retries)
        assertEquals(1, dismissals)
    }

    @Test
    fun `a rate limit gives the local time to retry at, and its button only dismisses`() {
        val config = NetworkFailure.RateLimited(RETRY_AT).toConfig(locale = Locale.GERMANY)

        assertEquals(R.string.global_timeout_too_many_requests_title, config.titleRes)
        assertEquals(R.string.global_timeout_too_many_requests_paragraph, config.bodyTextRes)
        assertEquals(listOf("12:05"), config.bodyTextArgs)
        assertEquals(R.string.global_timeout_too_many_requests_prim_button, config.primaryButtonTextRes)
        assertNull(config.errorCode)
        assertNull(config.traceId)

        config.onPrimaryButtonClick()
        assertEquals(0, retries)
        assertEquals(1, dismissals)
    }

    @Test
    fun `the retry time follows the locale`() {
        val config = NetworkFailure.RateLimited(RETRY_AT).toConfig(locale = Locale.US)

        // The narrow no-break space before "PM" depends on the CLDR version, so only digits and
        // the period are checked.
        val time = config.bodyTextArgs.single() as String
        assertTrue(time, time.startsWith("12:05") && time.endsWith("PM"))
    }

    @Test
    fun `a rate limit without a retry time falls back to the generic error, whose button retries`() {
        val config = NetworkFailure.RateLimited(retryAt = null).toConfig()

        assertEquals(R.string.global_error_title, config.titleRes)
        assertEquals(R.string.global_error_paragraph, config.bodyTextRes)
        assertTrue(config.bodyTextArgs.isEmpty())

        config.onPrimaryButtonClick()

        assertEquals(1, retries)
        assertEquals(0, dismissals)
    }

    private fun NetworkFailure.toConfig(locale: Locale = Locale.GERMANY) = toErrorDialogConfig(
        onRetry = { retries++ },
        onDismiss = { dismissals++ },
        zone = BERLIN,
        locale = locale,
    )
}
