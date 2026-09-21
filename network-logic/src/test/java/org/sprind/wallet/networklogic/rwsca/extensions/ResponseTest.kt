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

package org.sprind.wallet.networklogic.rwsca.extensions

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sprind.wallet.networklogic.common.extensions.TRACE_ID_HEADER
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaRegisterResponse
import retrofit2.Response

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_INTERNAL_SERVER_ERROR = 500
private const val JSON_MEDIA_TYPE = "application/json"

private const val MALFORMED_BODY = "not json"
private const val MALFORMED_BODY_LONG = "totally not json"
private const val HEADER_TRACE_XYZ = "trace-xyz"
private const val HEADER_TRACE_ID = "header-trace-id"
private const val PIN_VERIFICATION_CODE = "PIN_VERIFICATION_FAILED"
private const val PIN_VERIFICATION_DESCRIPTION = "Pin verification failed"
private const val PIN_VERIFICATION_BODY_TRACE_ID = "f08b3412-5a46-4b17-c278-cdc48246da9"
private const val PIN_VERIFICATION_TRY_COUNTER = 2
private const val PIN_VERIFICATION_TRY_ALLOWED_AFTER = "2026-09-16T10:16:30Z"
private const val INTERNAL_ERROR_CODE = "INTERNAL_SERVER_ERROR"
private const val INTERNAL_ERROR_DESCRIPTION = "Internal server error"
private const val INTERNAL_ERROR_BODY_TRACE_ID = "body-trace-id"

/**
 * Pin tests for [Response.toRwscaApiResult]: verify that its [errorFactory] builds an
 * [RwscaErrorResponse] with `code = "Unknown RWSCA Error"` and the raw error text as
 * `description`, and that the real [RwscaErrorResponse] serialization + [WithTraceId.withTraceId]
 * integration works end-to-end through [Response.toApiResult].
 *
 * Branch coverage of [Response.toApiResult] itself lives in
 * `org.sprind.wallet.networklogic.common.extensions.ResponseTest`.
 */
class ResponseTest {

    @Test
    fun `toRwscaApiResult - Success without body yields Failure with Unknown RWSCA Error code and null description`() {
        val response = Response.success<RwscaRegisterResponse>(null)

        val result = response.toRwscaApiResult() as ApiResult.Failure
        assertEquals(RwscaErrorResponse(code = UNKNOWN_RWSCA_ERROR_CODE, description = null), result.error)
    }

    @Test
    fun `toRwscaApiResult - Malformed JSON error body yields Unknown RWSCA Error and preserves raw text as description`() {
        val response = Response.error<RwscaRegisterResponse>(
            HTTP_BAD_REQUEST,
            MALFORMED_BODY.toResponseBody(JSON_MEDIA_TYPE.toMediaType())
        )

        val result = response.toRwscaApiResult() as ApiResult.Failure
        assertEquals(RwscaErrorResponse(code = UNKNOWN_RWSCA_ERROR_CODE, description = MALFORMED_BODY), result.error)
        assertNull(result.error.traceId)
    }

    @Test
    fun `toRwscaApiResult - Malformed JSON body with X-trace-Id header still attaches traceId`() {
        val rawResponse = okhttp3.Response.Builder()
            .code(HTTP_BAD_REQUEST).message("Bad Request")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header(TRACE_ID_HEADER, HEADER_TRACE_XYZ)
            .build()
        val response = Response.error<RwscaRegisterResponse>(
            MALFORMED_BODY_LONG.toResponseBody(JSON_MEDIA_TYPE.toMediaType()),
            rawResponse
        )

        val result = response.toRwscaApiResult() as ApiResult.Failure
        assertEquals(UNKNOWN_RWSCA_ERROR_CODE, result.error.code)
        assertEquals(MALFORMED_BODY_LONG, result.error.description)
        assertEquals(HEADER_TRACE_XYZ, result.error.traceId)
    }

    private fun errorResponseWithHeader(status: Int, body: String, traceId: String): Response<RwscaRegisterResponse> {
        val rawResponse = okhttp3.Response.Builder()
            .code(status).message("Error")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header(TRACE_ID_HEADER, traceId)
            .build()
        return Response.error(
            body.toResponseBody(JSON_MEDIA_TYPE.toMediaType()),
            rawResponse
        )
    }

    @Test
    fun `toRwscaApiResult - Real backend-shaped error body decodes with PIN_VERIFICATION_FAILED code`() {
        val rawBody = """
            {
              "code": "$PIN_VERIFICATION_CODE",
              "description": "$PIN_VERIFICATION_DESCRIPTION",
              "timestamp": "2026-09-16T10:15:30.123456",
              "trace_id": "$PIN_VERIFICATION_BODY_TRACE_ID",
              "try_counter": $PIN_VERIFICATION_TRY_COUNTER,
              "try_allowed_after": "$PIN_VERIFICATION_TRY_ALLOWED_AFTER"
            }
        """.trimIndent()
        val response = errorResponseWithHeader(
            HTTP_BAD_REQUEST,
            rawBody,
            HEADER_TRACE_ID
        )

        val result = response.toRwscaApiResult() as ApiResult.Failure
        assertEquals(PIN_VERIFICATION_CODE, result.error.code)
        assertEquals(PIN_VERIFICATION_DESCRIPTION, result.error.description)
        assertEquals(HEADER_TRACE_ID, result.error.traceId)
        assertEquals(PIN_VERIFICATION_TRY_COUNTER, result.error.tryCounter)
        assertEquals(PIN_VERIFICATION_TRY_ALLOWED_AFTER, result.error.tryAllowedAfter)
    }

    @Test
    fun `toRwscaApiResult - Backend body with unknown extra fields still decodes`() {
        val rawBody = """
            {
              "code": "$INTERNAL_ERROR_CODE",
              "description": "$INTERNAL_ERROR_DESCRIPTION",
              "timestamp": "2026-09-16T10:15:30.123456",
              "trace_id": "$INTERNAL_ERROR_BODY_TRACE_ID",
              "some_future_field": { "nested": true }
            }
        """.trimIndent()
        val response = errorResponseWithHeader(
            HTTP_INTERNAL_SERVER_ERROR,
            rawBody,
            HEADER_TRACE_ID
        )

        val result = response.toRwscaApiResult() as ApiResult.Failure
        assertEquals(INTERNAL_ERROR_CODE, result.error.code)
        assertEquals(HEADER_TRACE_ID, result.error.traceId)
    }
}