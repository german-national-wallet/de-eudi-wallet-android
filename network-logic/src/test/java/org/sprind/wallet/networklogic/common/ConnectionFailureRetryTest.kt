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

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sprind.wallet.networklogic.trace.TracedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ConnectionFailureRetryTest {

    private val attempts = AtomicInteger()
    private val server = MockWebServer()

    /** Whether every attempt fails as a refused connection, before it reaches [server]. */
    @Volatile
    private var refuseConnections = false

    private val client = HttpClient(OkHttp) {
        engine {
            preconfigured = OkHttpClient.Builder()
                .readTimeout(200, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .addInterceptor { chain ->
                    attempts.incrementAndGet()
                    if (refuseConnections) throw ConnectException("Connection refused")
                    chain.proceed(chain.request())
                }
                .build()
        }
        expectSuccess = false
        retryConnectionFailures(delayMillis = 1)
    }

    @After
    fun tearDown() {
        client.close()
        server.close()
    }

    @Test
    fun `a request that cannot connect is sent twice more before it fails`() {
        server.start()
        // Refused in the interceptor rather than on a freed port, which something else could bind.
        refuseConnections = true

        val failure = runCatching { post(server.url("/response").toString()) }.exceptionOrNull()

        assertTrue("$failure", failure?.isConnectionFailure() == true)
        assertEquals(1 + CONNECTION_FAILURE_RETRIES, attempts.get())
    }

    @Test
    fun `a request that connects again is not failed`() {
        server.start()
        server.enqueue(MockResponse.Builder().code(500).build())

        val response = post(server.url("/response").toString())

        assertEquals(500, response.status.value)
        assertEquals(1, attempts.get())
    }

    @Test
    fun `a request that timed out is not sent again`() {
        server.start()
        // The verifier may have processed the request, so it must not be sent twice.
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())

        val failure = runCatching { post(server.url("/response").toString()) }.exceptionOrNull()

        assertEquals(NetworkFailure.Timeout, failure?.toNetworkFailure())
        assertEquals(1, attempts.get())
    }

    @Test
    fun `connection failures are recognised through wrappers`() {
        assertTrue(ConnectException("refused").isConnectionFailure())
        assertFalse(NoRouteToHostException("no route").isConnectionFailure())
        // What Android throws when the device has no network at all.
        assertFalse(
            ConnectException("failed to connect: connect failed: ENETUNREACH (Network is unreachable)")
                .isConnectionFailure()
        )
        assertTrue(
            IllegalStateException(TracedIOException("trace", ConnectException("refused")))
                .isConnectionFailure()
        )
        assertFalse(SocketTimeoutException("read timed out").isConnectionFailure())
        assertFalse(UnknownHostException("offline").isConnectionFailure())
    }

    private fun post(url: String): HttpResponse = runBlocking {
        client.submitForm(url, parameters { append("vp_token", "token") })
    }
}
