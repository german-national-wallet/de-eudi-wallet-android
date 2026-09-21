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

import kotlinx.serialization.json.Json
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.common.model.WithTraceId
import org.sprind.wallet.networklogic.trace.CLIENT_TRACE_ID_HEADER
import retrofit2.Response

/**
 * Name of the HTTP header that carries the request trace id used to correlate errors with the
 * request that produced them (see [toApiResult]).
 */
internal const val TRACE_ID_HEADER = "X-trace-Id"

/**
 * The [Json] instance used to decode error bodies in [toApiResult].
 *
 * Lenient with `ignoreUnknownKeys`, matching the convention used for the app's other backend JSON
 * decoding (e.g. `NetworkModule`) and the API contract rule that "clients must tolerate unknown
 * fields" — the backend may add optional fields to error responses at any time.
 */
internal val errorBodyJson:Json by lazy {
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}

/**
 * Returns the trace id to report for this response, or `null` when neither header is present.
 *
 * The backend's own id ([TRACE_ID_HEADER]) is preferred, since that is the one it logs against.
 * When the backend does not return one, the client-generated id added by
 * [org.sprind.wallet.networklogic.trace.TraceContextInterceptor] is used, so that an error shown to
 * the user always carries something traceable.
 */
internal fun Response<*>.traceId(): String? =
    headers()[TRACE_ID_HEADER] ?: headers()[CLIENT_TRACE_ID_HEADER]

/**
 * Converts this Retrofit [Response] into an [ApiResult], attaching the response trace id to
 * any error produced.
 *
 * On success ([Response.isSuccessful] is `true`):
 * - When [SuccessType] is `Unit`, returns `ApiResult.Success(Unit)`.
 * - When the body is non-null, returns `ApiResult.Success` with the body.
 * - When the body is null, falls back to `ApiResult.Failure(errorFactory(null))`.
 *
 * On failure, reads the trace id via [traceId] and the raw error body, then attempts to
 * JSON-decode the body into [ErrorType] and tag it with the trace id through [WithTraceId.withTraceId].
 * If decoding fails (or the body is empty), an error is built from [errorFactory] using the raw
 * error text and tagged with the trace id instead.
 *
 * @param SuccessType The successful body type declared by the Retrofit endpoint.
 * @param ErrorType The error type to produce on failure. Must implement [WithTraceId] so the
 *                  trace id can be attached, and must be decodable from JSON via kotlinx.serialization.
 * @param errorFactory Builds a fallback [ErrorType] from the raw error text (or `null` when the
 *                      successful body was unexpectedly null), before the trace id is attached.
 */
internal inline fun <reified SuccessType, reified ErrorType: WithTraceId<ErrorType>> Response<SuccessType>.toApiResult(
    errorFactory: (String?) -> ErrorType
): ApiResult<SuccessType, ErrorType> =
    if (isSuccessful) {
        val body: SuccessType? = body()
        when {
            SuccessType::class == Unit::class -> ApiResult.Success(Unit as SuccessType)
            body != null ->  ApiResult.Success(body)
            else -> ApiResult.Failure(errorFactory(null))
        }
    } else {
        val traceId = traceId()
        val errorText = errorBody()?.string()
        errorText
            ?.let {
                runCatching {
                    errorBodyJson.decodeFromString<ErrorType>(it).withTraceId(traceId)
                }
            }
            ?.map { ApiResult.Failure(it) }
            ?.getOrNull()
            ?: ApiResult.Failure(errorFactory(errorText).withTraceId(traceId))
    }