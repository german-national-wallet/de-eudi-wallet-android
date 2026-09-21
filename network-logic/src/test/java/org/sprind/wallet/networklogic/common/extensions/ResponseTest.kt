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

package org.sprind.wallet.networklogic.common.extensions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.common.model.WithTraceId
import retrofit2.Response

private const val HTTP_BAD_REQUEST = 400
private const val JSON_MEDIA_TYPE = "application/json"

private const val FALLBACK_CODE = "UNKNOWN"
private const val DECODED_CODE = "SOME_ERROR"
private const val DECODED_DESCRIPTION = "something went wrong"
private const val DECODED_BODY = """{"code":"SOME_ERROR","description":"something went wrong"}"""
private const val DECODED_BODY_MINIMAL = """{"code":"SOME_ERROR"}"""
private const val MINIMAL_BODY = """{"code":"ERR"}"""
private const val MALFORMED_BODY = "not json"
private const val MALFORMED_BODY_LONG = "totally not json"
private const val IRRELEVANT_BODY = """{"unrelated":"field"}"""
private const val HEADER_TRACE_ABC = "abc-123"
private const val HEADER_TRACE_1 = "trace-1"
private const val HEADER_TRACE_2 = "trace-2"
private const val HEADER_TRACE_3 = "trace-3"
private const val HEADER_TRACE_4 = "trace-4"

@Serializable
private data class TestError(
    val code: String,
    val description: String? = null,
    @SerialName("trace_id") val traceId: String? = null,
) : WithTraceId<TestError> {
    override fun withTraceId(traceId: String?): TestError = copy(traceId = traceId)
}

private val errorFactory: (String?) -> TestError = { raw ->
    TestError(code = FALLBACK_CODE, description = raw)
}

class ResponseTest {

    @Test
    fun `traceId - returns header value when X-trace-Id present`() {
        val response = errorResponseWithHeader(traceId = HEADER_TRACE_ABC, body = MINIMAL_BODY)

        assertEquals(HEADER_TRACE_ABC, response.traceId())
    }

    @Test
    fun `traceId - returns null when X-trace-Id absent`() {
        val response = Response.error<TestSuccess>(
            HTTP_BAD_REQUEST,
            MINIMAL_BODY.toResponseBody(JSON_MEDIA_TYPE.toMediaType())
        )

        assertNull(response.traceId())
    }

    @Test
    fun `toApiResult - success with non-null body yields Success with body`() {
        val body = TestSuccess("value-1")
        val response = Response.success(body)

        assertEquals(ApiResult.Success(body), response.toApiResult(errorFactory))
    }

    @Test
    fun `toApiResult - success with Unit type yields Success of Unit`() {
        val response = Response.success<Unit>(null)

        val result = response.toApiResult<Unit, TestError>(errorFactory)
        assertTrue(result is ApiResult.Success)
        assertEquals(Unit, (result as ApiResult.Success).response)
    }

    @Test
    fun `toApiResult - success with null body and non-Unit type yields Failure from errorFactory with null`() {
        val response = Response.success<TestSuccess>(null)

        val result = response.toApiResult(errorFactory) as ApiResult.Failure
        assertEquals(FALLBACK_CODE, result.error.code)
        assertNull(result.error.description)
        assertNull(result.error.traceId)
    }

    @Test
    fun `toApiResult - error with valid JSON body yields Failure with decoded fields and attached traceId`() {
        val response = errorResponseWithHeader(
            traceId = HEADER_TRACE_1,
            body = DECODED_BODY,
        )

        val result = response.toApiResult(errorFactory) as ApiResult.Failure
        assertEquals(DECODED_CODE, result.error.code)
        assertEquals(DECODED_DESCRIPTION, result.error.description)
        assertEquals(HEADER_TRACE_1, result.error.traceId)
    }

    @Test
    fun `toApiResult - error with valid JSON body and no trace header yields decoded Failure with null traceId`() {
        val response = Response.error<TestSuccess>(
            HTTP_BAD_REQUEST,
            DECODED_BODY_MINIMAL.toResponseBody(JSON_MEDIA_TYPE.toMediaType())
        )

        val result = response.toApiResult(errorFactory) as ApiResult.Failure
        assertEquals(DECODED_CODE, result.error.code)
        assertNull(result.error.traceId)
    }

    @Test
    fun `toApiResult - error with malformed JSON body yields Failure from errorFactory with raw text and traceId`() {
        val response = errorResponseWithHeader(traceId = HEADER_TRACE_2, body = MALFORMED_BODY)

        val result = response.toApiResult(errorFactory) as ApiResult.Failure
        assertEquals(FALLBACK_CODE, result.error.code)
        assertEquals(MALFORMED_BODY, result.error.description)
        assertEquals(HEADER_TRACE_2, result.error.traceId)
    }

    @Test
    fun `toApiResult - error with empty error body yields Failure from errorFactory with empty text and traceId`() {
        val response = errorResponseWithHeader(traceId = HEADER_TRACE_3, body = "")

        val result = response.toApiResult(errorFactory) as ApiResult.Failure
        assertEquals(FALLBACK_CODE, result.error.code)
        assertEquals("", result.error.description)
        assertEquals(HEADER_TRACE_3, result.error.traceId)
    }

    @Test
    fun `toApiResult - error with JSON missing required code field yields fallback Failure from errorFactory`() {
        val response = errorResponseWithHeader(traceId = HEADER_TRACE_4, body = IRRELEVANT_BODY)

        val result = response.toApiResult(errorFactory) as ApiResult.Failure
        assertEquals(FALLBACK_CODE, result.error.code)
        assertEquals(IRRELEVANT_BODY, result.error.description)
        assertEquals(HEADER_TRACE_4, result.error.traceId)
    }

    @Test
    fun `toApiResult - error without X-trace-Id header attaches null traceId to fallback error`() {
        val response = Response.error<TestSuccess>(
            HTTP_BAD_REQUEST,
            MALFORMED_BODY_LONG.toResponseBody(JSON_MEDIA_TYPE.toMediaType())
        )

        val result = response.toApiResult(errorFactory) as ApiResult.Failure
        assertEquals(FALLBACK_CODE, result.error.code)
        assertEquals(MALFORMED_BODY_LONG, result.error.description)
        assertNull(result.error.traceId)
    }

    private fun errorResponseWithHeader(traceId: String, body: String): Response<TestSuccess> {
        val rawResponse = okhttp3.Response.Builder()
            .code(HTTP_BAD_REQUEST)
            .message("Bad Request")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header(TRACE_ID_HEADER, traceId)
            .build()
        return Response.error(
            body.toResponseBody(JSON_MEDIA_TYPE.toMediaType()),
            rawResponse
        )
    }
}

@Serializable
private data class TestSuccess(val value: String)