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

import org.sprind.wallet.networklogic.common.extensions.toApiResult
import org.sprind.wallet.networklogic.common.model.WithTraceId
import org.sprind.wallet.networklogic.rwsca.model.RwscaApiResult
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import retrofit2.Response

internal const val UNKNOWN_RWSCA_ERROR_CODE = "Unknown RWSCA Error"

/**
 * Converts this Retrofit [Response] into an [RwscaApiResult], using [RwscaErrorResponse] as
 * the error type.
 *
 * Delegates to [Response.toApiResult] with a factory that produces an [RwscaErrorResponse]
 * whose `code` is [UNKNOWN_RWSCA_ERROR_CODE] and whose `description` carries the raw error text
 * (or `null` when the successful body was unexpectedly `null`). The response's `X-trace-Id`
 * header, if present, is attached to the resulting error via [WithTraceId.withTraceId].
 *
 * Branch-level behavior (success vs. failure, JSON decode vs. fallback, trace-id propagation)
 * is documented on [Response.toApiResult] and tested by
 * `org.sprind.wallet.networklogic.common.extensions.ResponseTest`. The tests in
 * `org.sprind.wallet.networklogic.rwsca.extensions.ResponseTest` pin the rwsca-specific
 * factory wiring and the [RwscaErrorResponse] serialization/`withTraceId` integration
 * end-to-end.
 *
 * @param T The successful body type declared by the Retrofit endpoint.
 */
internal inline fun <reified T> Response<T>.toRwscaApiResult(): RwscaApiResult<T> =
    this.toApiResult { RwscaErrorResponse(code = UNKNOWN_RWSCA_ERROR_CODE, description = it) }