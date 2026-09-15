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

package org.sprind.wallet.networklogic.trace

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TraceParent
import java.net.UnknownHostException

private const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
private const val TRACEPARENT = "00-$TRACE_ID-00f067aa0ba902b7-01"
private const val HTTP_SERVER_ERROR = 500

class TraceContextInterceptorTest {

    private val telemetry: Telemetry = mock()
    private lateinit var interceptor: TraceContextInterceptor

    @Before
    fun setUp() {
        whenever(telemetry.nextRequestTraceParent()).thenReturn(
            TraceParent(traceId = TRACE_ID, headerValue = TRACEPARENT)
        )
        interceptor = TraceContextInterceptor(telemetry = telemetry)
    }

    @Test
    fun `adds traceparent header when the request does not already carry one`() {
        val chain = chainProceeding(requestTo("http://localhost/challenge"))

        interceptor.intercept(chain)

        assertEquals(TRACEPARENT, chain.capturedProceededRequest().header("traceparent"))
    }

    @Test
    fun `keeps a traceparent header that something upstream already set`() {
        val existing = "00-11111111111111111111111111111111-2222222222222222-01"
        val request = requestTo("http://localhost/challenge")
            .newBuilder()
            .header("traceparent", existing)
            .build()
        val chain = chainProceeding(request)

        interceptor.intercept(chain)

        assertEquals(existing, chain.capturedProceededRequest().header("traceparent"))
    }

    @Test
    fun `exposes the client trace id on the response so error mapping can fall back to it`() {
        val chain = chainProceeding(requestTo("http://localhost/challenge"))

        val response = interceptor.intercept(chain)

        assertEquals(TRACE_ID, response.header(CLIENT_TRACE_ID_HEADER))
    }

    @Test
    fun `wraps a failure to reach the backend in a TracedIOException carrying the trace id`() {
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(requestTo("http://localhost/challenge"))
        whenever(chain.proceed(any())).thenThrow(UnknownHostException("no dns"))

        val thrown = assertThrows(TracedIOException::class.java) {
            interceptor.intercept(chain)
        }

        assertEquals(TRACE_ID, thrown.traceId)
        assertEquals(TRACE_ID, thrown.traceId())
        assertEquals(UnknownHostException::class.java, thrown.cause.javaClass)
    }

    /**
     * Guards the reason this is registered as an application interceptor: a network interceptor is
     * never entered when no connection can be established, so it could not report a trace ID for
     * the very case that needs one.
     */
    @Test
    fun `reports a trace id when the connection itself cannot be established`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        // Port 1 is not listening, so this fails before any connection exists.
        val call = client.newCall(requestTo("http://localhost:1/challenge"))

        val thrown = assertThrows(TracedIOException::class.java) { call.execute() }

        assertEquals(TRACE_ID, thrown.traceId)
    }

    @Test
    fun `traceId returns null for an exception that did not come from a traced call`() {
        assertNull(UnknownHostException("no dns").traceId())
    }

    private fun requestTo(url: String): Request = Request.Builder().url(url).build()

    /** A chain that responds with a server error, so responses are built the usual way. */
    private fun chainProceeding(request: Request): Interceptor.Chain {
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(any())).thenAnswer { invocation ->
            val proceeded = invocation.getArgument<Request>(0)
            Response.Builder()
                .request(proceeded)
                .protocol(Protocol.HTTP_1_1)
                .code(HTTP_SERVER_ERROR)
                .message("Internal Server Error")
                .build()
        }
        return chain
    }

    private fun Interceptor.Chain.capturedProceededRequest(): Request {
        val captor = argumentCaptor<Request>()
        org.mockito.Mockito.verify(this).proceed(captor.capture())
        return captor.firstValue
    }
}