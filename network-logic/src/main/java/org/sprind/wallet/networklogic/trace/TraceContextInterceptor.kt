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
import okhttp3.Response
import org.sprind.wallet.analyticslogic.controller.Telemetry
import java.io.IOException

/**
 * Response header under which the client-generated trace ID is exposed to the layers that build
 * error state, used as a fallback when the backend does not return one of its own.
 */
const val CLIENT_TRACE_ID_HEADER: String = "X-Client-Trace-Id"

private const val TRACEPARENT_HEADER = "traceparent"

/**
 * Makes a client-generated trace ID available for every request, so that an error shown to the user
 * can always be correlated with telemetry.
 *
 * This must be registered as an *application* interceptor rather than a network interceptor:
 * network interceptors run only once a connection has been established, so they are skipped
 * entirely when the backend cannot be reached — precisely the case that needs a trace ID.
 *
 * A `traceparent` header is added when nothing upstream has set one, so a request made outside an
 * active telemetry span is still correlatable on the backend. Failures are rethrown as
 * [TracedIOException] to carry the ID back to the caller.
 */
class TraceContextInterceptor(
    private val telemetry: Telemetry,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val traceParent = telemetry.nextRequestTraceParent()
        val request = chain.request()

        val tracedRequest = if (request.header(TRACEPARENT_HEADER) == null) {
            request.newBuilder()
                .header(TRACEPARENT_HEADER, traceParent.headerValue)
                .build()
        } else {
            request
        }

        val response = try {
            chain.proceed(tracedRequest)
        } catch (e: IOException) {
            throw TracedIOException(traceId = traceParent.traceId, cause = e)
        }

        return response.newBuilder()
            .header(CLIENT_TRACE_ID_HEADER, traceParent.traceId)
            .build()
    }
}