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

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TraceParent
import org.sprind.wallet.networklogic.trace.TraceContextInterceptor
import org.sprind.wallet.networklogic.trace.TracedIOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-24T10:00:00Z")
private const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"

class HttpFailureInterceptorTest {

    private val server = MockWebServer()
    private val clock = object : Clock {
        override fun now(): Instant = NOW
    }
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(HttpFailureInterceptor(clock))
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `other responses are passed through untouched`() {
        listOf(200, 400, 500, 503).forEach { code ->
            server.enqueue(MockResponse.Builder().code(code).body("body").build())

            client.newCall(request()).execute().use { response ->
                assertEquals(code, response.code)
                assertEquals("body", response.body.string())
            }
        }
    }

    @Test
    fun `408 from the gateway becomes a request timeout`() {
        enqueueOkHttpRetry(MockResponse.Builder().code(408).setHeader("server", "envoy").build())

        val thrown = assertThrows(HttpRequestTimeoutException::class.java) { execute() }

        assertEquals(408, thrown.statusCode)
        assertTrue(thrown.fromGateway)
        assertEquals(NetworkFailure.Timeout, thrown.toNetworkFailure())
    }

    @Test
    fun `408 from another server is a request timeout too`() {
        enqueueOkHttpRetry(MockResponse.Builder().code(408).build())

        val thrown = assertThrows(HttpRequestTimeoutException::class.java) { execute() }

        assertFalse(thrown.fromGateway)
    }

    @Test
    fun `429 with delay-seconds carries when to retry on the device clock`() {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .setHeader("x-envoy-ratelimited", "true")
                .setHeader("Retry-After", "90")
                .build()
        )

        val thrown = assertThrows(HttpRateLimitedException::class.java) { execute() }

        assertEquals(429, thrown.statusCode)
        assertTrue(thrown.fromGateway)
        assertEquals(NOW + 90.seconds, thrown.retryAt)
        assertEquals(NetworkFailure.RateLimited(NOW + 90.seconds), thrown.toNetworkFailure())
    }

    @Test
    fun `429 with an HTTP-date is resolved against the Date header, not the device clock`() {
        // The server is an hour ahead of the device; the user still waits 2 minutes.
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .setHeader("server", "envoy")
                .setHeader("Date", "Sat, 24 Oct 2026 11:00:00 GMT")
                .setHeader("Retry-After", "Sat, 24 Oct 2026 11:02:00 GMT")
                .build()
        )

        val thrown = assertThrows(HttpRateLimitedException::class.java) { execute() }

        assertEquals(NOW + 2.minutes, thrown.retryAt)
    }

    @Test
    fun `429 without Retry-After has no retry time`() {
        server.enqueue(MockResponse.Builder().code(429).build())

        val thrown = assertThrows(HttpRateLimitedException::class.java) { execute() }

        assertNull(thrown.retryAt)
        assertFalse(thrown.fromGateway)
    }

    /** RWSCA's PIN backoff: the caller needs the error code and try_allowed_after. */
    @Test
    fun `a 429 with a JSON error body is passed through to the caller`() {
        val body = """{"code":"PIN_RETRY_BLOCKED","try_allowed_after":"2026-10-24T10:05:00Z"}"""
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .setHeader("Content-Type", "application/json")
                .body(body)
                .build()
        )

        client.newCall(request()).execute().use { response ->
            assertEquals(429, response.code)
            assertEquals(body, response.body.string())
        }
    }

    @Test
    fun `a 408 with a problem+json body is passed through to the caller`() {
        enqueueOkHttpRetry(
            MockResponse.Builder()
                .code(408)
                .setHeader("Content-Type", "application/problem+json; charset=utf-8")
                .body("""{"title":"Request Timeout"}""")
                .build()
        )

        client.newCall(request()).execute().use { assertEquals(408, it.code) }
    }

    @Test
    fun `a 429 from the gateway with a plain text body is converted`() {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .setHeader("server", "envoy")
                .setHeader("Content-Type", "text/plain")
                .body("local_rate_limited")
                .build()
        )

        assertThrows(HttpRateLimitedException::class.java) { execute() }
    }

    @Test
    fun `an absurd Retry-After is capped`() {
        server.enqueue(MockResponse.Builder().code(429).setHeader("Retry-After", "999999999").build())

        val thrown = assertThrows(HttpRateLimitedException::class.java) { execute() }

        assertEquals(NOW + MAX_RETRY_AFTER, thrown.retryAt)
    }

    @Test
    fun `a 429 is not retried`() {
        server.enqueue(MockResponse.Builder().code(429).build())
        server.enqueue(MockResponse.Builder().code(200).build())

        assertThrows(HttpRateLimitedException::class.java) { execute() }

        assertEquals(1, server.requestCount)
    }

    /** OkHttp retries a 408 once on its own; only a second 408 reaches the interceptor. */
    @Test
    fun `a 408 that succeeds on the OkHttp retry is not a failure`() {
        server.enqueue(MockResponse.Builder().code(408).build())
        server.enqueue(MockResponse.Builder().code(200).build())

        client.newCall(request()).execute().use { assertEquals(200, it.code) }
        assertEquals(2, server.requestCount)
    }

    /** Guards the interceptor order in `provideBaseOkHttpClient`. */
    @Test
    fun `inside the trace interceptor, the failure carries the trace id and is still classified`() {
        val telemetry: Telemetry = mock()
        whenever(telemetry.nextRequestTraceParent()).thenReturn(
            TraceParent(traceId = TRACE_ID, headerValue = "00-$TRACE_ID-00f067aa0ba902b7-01")
        )
        val tracedClient = OkHttpClient.Builder()
            .addInterceptor(TraceContextInterceptor(telemetry))
            .addInterceptor(HttpFailureInterceptor(clock))
            .build()
        server.enqueue(MockResponse.Builder().code(429).setHeader("Retry-After", "10").build())

        val thrown = assertThrows(TracedIOException::class.java) {
            tracedClient.newCall(request()).execute()
        }

        assertEquals(TRACE_ID, thrown.traceId)
        assertEquals(NetworkFailure.RateLimited(NOW + 10.seconds), thrown.toNetworkFailure())
    }

    private fun request(): Request = Request.Builder().url(server.url("/challenge")).build()

    /** Enqueues [response] twice, as OkHttp retries a 408 once before giving up. */
    private fun enqueueOkHttpRetry(response: MockResponse) {
        repeat(2) { server.enqueue(response) }
    }

    private fun execute() {
        client.newCall(request()).execute().close()
    }
}
