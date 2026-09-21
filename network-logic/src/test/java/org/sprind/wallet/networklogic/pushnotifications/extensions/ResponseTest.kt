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

package org.sprind.wallet.networklogic.pushnotifications.extensions

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.common.extensions.TRACE_ID_HEADER
import org.sprind.wallet.networklogic.pushnotifications.model.PushNotificationsChallengeResponse
import retrofit2.Response

private const val HTTP_UNAUTHORIZED = 401
private const val JSON_MEDIA_TYPE = "application/json"

private const val HEADER_TRACE_ID = "header-trace-id"
private const val TOKEN_FAILURE_CODE = "MDVM_TOKEN_VERIFICATION_FAILURE"
private const val TOKEN_FAILURE_DESCRIPTION = "Malformed MDVM token"

/**
 * Pin test for [Response.toPushNotificationsApiResult]: verify that the real
 * [PushNotificationsErrorResponse] decodes a backend-shaped error body end-to-end
 * through [Response.toApiResult], mirroring `PnsErrorResponse` on the wire.
 *
 * Branch coverage of [Response.toApiResult] itself lives in
 * `org.sprind.wallet.networklogic.common.extensions.ResponseTest`.
 */
class ResponseTest {

    @Test
    fun `toPushNotificationsApiResult - Real backend-shaped error body decodes with code and trace_id`() {
        val rawBody = """
            {
              "code": "$TOKEN_FAILURE_CODE",
              "description": "$TOKEN_FAILURE_DESCRIPTION",
              "timestamp": "2026-09-16T10:15:30Z",
              "trace_id": "1e7187ed-385a-4b98-9d98-a1f5f0d44ad8"
            }
        """.trimIndent()
        val rawResponse = okhttp3.Response.Builder()
            .code(HTTP_UNAUTHORIZED).message("Unauthorized")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header(TRACE_ID_HEADER, HEADER_TRACE_ID)
            .build()
        val response = Response.error<PushNotificationsChallengeResponse>(
            rawBody.toResponseBody(JSON_MEDIA_TYPE.toMediaType()),
            rawResponse
        )

        val result = response.toPushNotificationsApiResult() as ApiResult.Failure
        assertEquals(TOKEN_FAILURE_CODE, result.error.errorCode)
        assertEquals(TOKEN_FAILURE_DESCRIPTION, result.error.description)
        assertEquals(HEADER_TRACE_ID, result.error.traceId)
    }
}