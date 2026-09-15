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

package org.sprind.wallet.networklogic.mdvm.utils

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse
import org.sprind.wallet.networklogic.mdvm.model.response.MdvmRegisterResponse
import retrofit2.Response

private const val HTTP_BAD_REQUEST = 400

class UtilsTest {

    @Test
    fun `toMdvmApiResult - Success with body yields MdvmApiResult Success`() {
        val body = MdvmRegisterResponse("fake mdvm_wi_id for test", "fake mdvm_token for test")
        val response = Response.success(body)

        assertEquals(ApiResult.Success(body), response.toMdvmApiResult())
    }

    @Test
    fun `toMdvmApiResult - Success without body yields Failure with UNKNOWN code`() {
        val response = Response.success<MdvmRegisterResponse>(null)

        assertEquals(ApiResult.Failure(MdvmErrorResponse("UNKNOWN")), response.toMdvmApiResult())
    }

    @Test
    fun `toMdvmApiResult - Error returns Failure with parsed error fields`() {
        val response = Response.error<MdvmRegisterResponse>(
            HTTP_BAD_REQUEST,
            """{"code":"SOME_ERROR","description":"something went wrong"}"""
                .toResponseBody("application/json".toMediaType())
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals("SOME_ERROR", result.error.code)
        assertEquals("something went wrong", result.error.description)
    }

    @Test
    fun `toMdvmApiResult - Error with X-trace-Id header attaches traceId to Failure`() {
        val rawResponse = okhttp3.Response.Builder()
            .code(HTTP_BAD_REQUEST).message("Bad Request")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header("X-trace-Id", "abc-123")
            .build()
        val response = Response.error<MdvmRegisterResponse>(
            """{"code":"ERR"}""".toResponseBody("application/json".toMediaType()),
            rawResponse
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals("abc-123", result.error.trace_id)
    }

    @Test
    fun `toMdvmApiResult - Error without X-trace-Id header yields null traceId`() {
        val response = Response.error<MdvmRegisterResponse>(
            HTTP_BAD_REQUEST,
            """{"code":"ERR"}""".toResponseBody("application/json".toMediaType())
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertNull(result.error.trace_id)
    }

    @Test
    fun `toMdvmApiResult - Malformed JSON error body yields UNKNOWN and preserves raw text`() {
        val rawBody = "not json"
        val response = Response.error<MdvmRegisterResponse>(
            HTTP_BAD_REQUEST,
            rawBody.toResponseBody("application/json".toMediaType())
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals("UNKNOWN", result.error.code)
        assertEquals(rawBody, result.error.description)
    }

    @Test
    fun `toMdvmApiResult - JSON object with blank code yields UNKNOWN fallback`() {
        val response = Response.error<MdvmRegisterResponse>(
            HTTP_BAD_REQUEST,
            """{"code":"","description":"ignored"}""".toResponseBody("application/json".toMediaType())
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals("UNKNOWN", result.error.code)
        // description from the parsed-but-rejected body is NOT preserved; raw text is used instead
        assertEquals(
            """{"code":"","description":"ignored"}""",
            result.error.description
        )
    }

    @Test
    fun `toMdvmApiResult - JSON object without code field yields UNKNOWN fallback`() {
        val rawBody = """{"unrelated":"field"}"""
        val response = Response.error<MdvmRegisterResponse>(
            HTTP_BAD_REQUEST,
            rawBody.toResponseBody("application/json".toMediaType())
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals("UNKNOWN", result.error.code)
        assertEquals(rawBody, result.error.description)
    }

    @Test
    fun `toMdvmApiResult - Malformed JSON body with X-trace-Id header still attaches traceId`() {
        val rawResponse = okhttp3.Response.Builder()
            .code(HTTP_BAD_REQUEST).message("Bad Request")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header("X-trace-Id", "trace-xyz")
            .build()
        val response = Response.error<MdvmRegisterResponse>(
            "totally not json".toResponseBody("application/json".toMediaType()),
            rawResponse
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals("UNKNOWN", result.error.code)
        assertEquals("totally not json", result.error.description)
        assertEquals("trace-xyz", result.error.trace_id)
    }

    @Test
    fun `toMdvmApiResult - Empty error body yields UNKNOWN with empty description`() {
        val response = Response.error<MdvmRegisterResponse>(
            HTTP_BAD_REQUEST,
            "".toResponseBody("application/json".toMediaType())
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals("UNKNOWN", result.error.code)
        assertEquals("", result.error.description)
    }
}
