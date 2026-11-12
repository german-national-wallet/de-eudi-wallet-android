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
import org.sprind.wallet.networklogic.trace.TracedIOException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.Instant

class NetworkFailureTest {

    @Test
    fun `client-side timeouts are a timeout`() {
        assertEquals(NetworkFailure.Timeout, SocketTimeoutException("read timed out").toNetworkFailure())
        assertEquals(NetworkFailure.Timeout, InterruptedIOException("timeout").toNetworkFailure())
    }

    @Test
    fun `HTTP 408 is a timeout`() {
        assertEquals(NetworkFailure.Timeout, HttpRequestTimeoutException(fromGateway = true).toNetworkFailure())
    }

    @Test
    fun `HTTP 429 is rate limited with its retry time`() {
        val retryAt = Instant.parse("2026-10-24T10:05:00Z")

        assertEquals(
            NetworkFailure.RateLimited(retryAt),
            HttpRateLimitedException(retryAt, fromGateway = true).toNetworkFailure(),
        )
    }

    @Test
    fun `the failure is found when wrapped`() {
        val wrapped = IllegalStateException(
            "wallet-core wrapper",
            TracedIOException(traceId = "trace", cause = SocketTimeoutException()),
        )

        assertEquals(NetworkFailure.Timeout, wrapped.toNetworkFailure())
    }

    @Test
    fun `other failures are not classified`() {
        listOf(
            UnknownHostException("no dns"),
            ConnectException("refused"),
            InterruptedIOException("Canceled"),
            IOException("closed"),
            IllegalStateException("bug"),
        ).forEach {
            assertNull(it.toString(), it.toNetworkFailure())
        }
    }
}
