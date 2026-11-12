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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.sprind.wallet.networklogic.common.NetworkFailure
import kotlin.time.Instant

class PidIssuanceErrorDialogTest {

    private val calls = mutableListOf<String>()

    @Test
    fun `an expired authorization offers to start again or cancel`() {
        val config = authorizationExpiredDialogConfig(
            onStartAgain = { calls += "start again" },
            onCancel = { calls += "cancel" },
        )

        assertEquals(R.string.pid_issuance_timeout_par_request_expired_title, config.titleRes)
        assertEquals(R.string.pid_issuance_timeout_par_request_expired_paragraph, config.bodyTextRes)
        assertEquals(R.string.pid_issuance_timeout_par_request_expired_prim_button, config.primaryButtonTextRes)
        assertEquals(R.string.pid_issuance_timeout_par_request_expired_sec_button, config.secondaryButtonTextRes)
        assertNull(config.errorCode)
        assertNull(config.traceId)

        assertFalse(config.dismissable)

        config.onPrimaryButtonClick()
        config.onSecondaryButtonClick()
        assertEquals(listOf("start again", "cancel"), calls)
    }

    @Test
    fun `a timeout during PID issuance also offers to cancel`() {
        val config = NetworkFailure.Timeout.toPidIssuanceErrorDialogConfig(
            onRetry = { calls += "retry" },
            onCancel = { calls += "cancel" },
            onDismiss = { calls += "dismiss" },
        )

        assertEquals(R.string.global_timeout_slow_internet_error_title, config.titleRes)
        assertEquals(R.string.pid_issuance_timeout_par_request_expired_sec_button, config.secondaryButtonTextRes)
        assertFalse(config.dismissable)

        config.onPrimaryButtonClick()
        config.onSecondaryButtonClick()
        assertEquals(listOf("retry", "cancel"), calls)
    }

    @Test
    fun `a rate limit during PID issuance only offers to retry later`() {
        val config = NetworkFailure.RateLimited(
            retryAt = Instant.parse("2026-10-24T10:05:00Z"),
        ).toPidIssuanceErrorDialogConfig(
            onRetry = { calls += "retry" },
            onCancel = { calls += "cancel" },
            onDismiss = { calls += "dismiss" },
        )

        assertEquals(R.string.global_timeout_too_many_requests_title, config.titleRes)
        assertNull(config.secondaryButtonTextRes)
        assertFalse(config.dismissable)

        config.onPrimaryButtonClick()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `a rate limit without a retry time during PID issuance offers to retry`() {
        val config = NetworkFailure.RateLimited(retryAt = null).toPidIssuanceErrorDialogConfig(
            onRetry = { calls += "retry" },
            onCancel = { calls += "cancel" },
            onDismiss = { calls += "dismiss" },
        )

        assertEquals(R.string.global_error_title, config.titleRes)
        assertNull(config.secondaryButtonTextRes)

        config.onPrimaryButtonClick()
        assertEquals(listOf("retry"), calls)
    }
}
