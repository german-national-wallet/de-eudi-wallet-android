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

package org.sprind.wallet.networklogic.mdvm.extensions

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.common.extensions.TRACE_ID_HEADER
import org.sprind.wallet.networklogic.mdvm.extensions.UNKNOWN_MDVM_ERROR_CODE
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse
import org.sprind.wallet.networklogic.mdvm.model.response.MdvmRegisterResponse
import retrofit2.Response

private const val HTTP_BAD_REQUEST = 400
private const val JSON_MEDIA_TYPE = "application/json"

private const val MALFORMED_BODY = "not json"
private const val MALFORMED_BODY_LONG = "totally not json"
private const val HEADER_TRACE_XYZ = "trace-xyz"
private const val HEADER_TRACE_ID = "header-trace-id"
private const val TOKEN_FAILURE_CODE = "MDVM_TOKEN_VERIFICATION_FAILURE"
private const val TOKEN_FAILURE_DESCRIPTION = "Malformed MDVM token"

/**
 * Pin tests for [Response.toMdvmApiResult]: verify that its [errorFactory] builds an
 * [MdvmErrorResponse] with `code = "Unknown MDVM Error"` and the raw error text as
 * `description`, and that the real [MdvmErrorResponse] serialization + [WithTraceId.withTraceId]
 * integration works end-to-end through [Response.toApiResult].
 *
 * Branch coverage of [Response.toApiResult] itself lives in
 * `org.sprind.wallet.networklogic.common.extensions.ResponseTest`.
 */
class ResponseTest {

    @Test
    fun `toMdvmApiResult - Success without body yields Failure with Unknown MDVM Error code and null description`() {
        val response = Response.success<MdvmRegisterResponse>(null)

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals(MdvmErrorResponse(code = UNKNOWN_MDVM_ERROR_CODE, description = null), result.error)
    }

    @Test
    fun `toMdvmApiResult - Malformed JSON error body yields Unknown MDVM Error and preserves raw text as description`() {
        val response = Response.error<MdvmRegisterResponse>(
            HTTP_BAD_REQUEST,
            MALFORMED_BODY.toResponseBody(JSON_MEDIA_TYPE.toMediaType())
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals(MdvmErrorResponse(code = UNKNOWN_MDVM_ERROR_CODE, description = MALFORMED_BODY), result.error)
        assertNull(result.error.traceId)
    }

    @Test
    fun `toMdvmApiResult - Malformed JSON body with X-trace-Id header still attaches traceId`() {
        val rawResponse = okhttp3.Response.Builder()
            .code(HTTP_BAD_REQUEST).message("Bad Request")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header(TRACE_ID_HEADER, HEADER_TRACE_XYZ)
            .build()
        val response = Response.error<MdvmRegisterResponse>(
            MALFORMED_BODY_LONG.toResponseBody(JSON_MEDIA_TYPE.toMediaType()),
            rawResponse
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals(UNKNOWN_MDVM_ERROR_CODE, result.error.code)
        assertEquals(MALFORMED_BODY_LONG, result.error.description)
        assertEquals(HEADER_TRACE_XYZ, result.error.traceId)
    }

    @Test
    fun `toMdvmApiResult - Real backend-shaped error body decodes with code and trace_id`() {
        val rawBody = """
            {
              "code": "$TOKEN_FAILURE_CODE",
              "description": "$TOKEN_FAILURE_DESCRIPTION",
              "timestamp": "2026-09-16T10:15:30.123456",
              "trace_id": "1e7187ed-385a-4b98-9d98-a1f5f0d44ad8"
            }
        """.trimIndent()
        val rawResponse = okhttp3.Response.Builder()
            .code(HTTP_BAD_REQUEST).message("Bad Request")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header(TRACE_ID_HEADER, HEADER_TRACE_ID)
            .build()
        val response = Response.error<MdvmRegisterResponse>(
            rawBody.toResponseBody(JSON_MEDIA_TYPE.toMediaType()),
            rawResponse
        )

        val result = response.toMdvmApiResult() as ApiResult.Failure
        assertEquals(TOKEN_FAILURE_CODE, result.error.code)
        assertEquals(TOKEN_FAILURE_DESCRIPTION, result.error.description)
        assertEquals(HEADER_TRACE_ID, result.error.traceId)
    }
}