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

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sprind.wallet.networklogic.trace.CLIENT_TRACE_ID_HEADER
import retrofit2.Response

private const val BACKEND_TRACE_ID = "backend-trace-id"
private const val CLIENT_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
private const val HTTP_BAD_REQUEST = 400

class TraceIdPrecedenceTest {

    @Test
    fun `prefers the backend trace id when the backend returned one`() {
        val response = errorResponseWith(
            mapOf(
                "X-trace-Id" to BACKEND_TRACE_ID,
                CLIENT_TRACE_ID_HEADER to CLIENT_TRACE_ID,
            )
        )

        assertEquals(BACKEND_TRACE_ID, response.traceId())
    }

    @Test
    fun `falls back to the client trace id when the backend returned none`() {
        val response = errorResponseWith(mapOf(CLIENT_TRACE_ID_HEADER to CLIENT_TRACE_ID))

        assertEquals(CLIENT_TRACE_ID, response.traceId())
    }

    @Test
    fun `is null when neither header is present`() {
        assertNull(errorResponseWith(emptyMap()).traceId())
    }

    private fun errorResponseWith(headers: Map<String, String>): Response<Unit> {
        val rawResponse = okhttp3.Response.Builder()
            .code(HTTP_BAD_REQUEST)
            .message("Bad Request")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()

        return Response.error(
            """{"code":"ERR"}""".toResponseBody("application/json".toMediaType()),
            rawResponse,
        )
    }
}