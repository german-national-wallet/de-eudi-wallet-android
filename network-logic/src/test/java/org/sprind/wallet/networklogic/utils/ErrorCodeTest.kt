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

package org.sprind.wallet.networklogic.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sprind.wallet.networklogic.common.HttpRateLimitedException
import org.sprind.wallet.networklogic.common.HttpRequestTimeoutException
import org.sprind.wallet.networklogic.common.RequestSigningException
import org.sprind.wallet.networklogic.trace.TracedIOException
import java.net.UnknownHostException
import java.security.InvalidKeyException

class ErrorCodeTest {

    @Test
    fun `an unreachable backend is reported as NO_INTERNET`() {
        val traced = TracedIOException(traceId = "trace", cause = UnknownHostException("no dns"))

        assertEquals(NO_INTERNET_ERROR_CODE, traced.getErrorCode())
    }

    @Test
    fun `a signing failure is not reported as NO_INTERNET even when wrapped`() {
        val traced = TracedIOException(
            traceId = "trace",
            cause = RequestSigningException(InvalidKeyException("key invalidated")),
        )

        assertEquals(UNKNOWN_ERROR_CODE, traced.getErrorCode())
    }

    @Test
    fun `a rate limit is reported as RATE_LIMITED, not NO_INTERNET`() {
        val traced = TracedIOException(
            traceId = "trace",
            cause = HttpRateLimitedException(retryAt = null, fromGateway = true),
        )

        assertEquals(RATE_LIMITED_ERROR_CODE, traced.getErrorCode())
    }

    @Test
    fun `a request timeout is reported as NO_INTERNET, like other timeouts`() {
        val traced = TracedIOException(
            traceId = "trace",
            cause = HttpRequestTimeoutException(fromGateway = true),
        )

        assertEquals(NO_INTERNET_ERROR_CODE, traced.getErrorCode())
    }

    @Test
    fun `a non IO failure is reported as UNKNOWN`() {
        assertEquals(UNKNOWN_ERROR_CODE, IllegalStateException("bug").getErrorCode())
    }
}