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

package org.sprind.wallet.networklogic.rwsca.model

import com.google.gson.Gson
import org.sprind.wallet.networklogic.utils.traceId
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.withTraceId
import retrofit2.Response

typealias RwscaApiResult<T> = ApiResult<T, RwscaErrorResponse>

internal inline fun <reified T> Response<T>.toRwscaApiResult(): RwscaApiResult<T> {
    return if (isSuccessful) {
        val body = body() ?: if (T::class == Unit::class) @Suppress("UNCHECKED_CAST") (Unit as T) else null
        body?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(RwscaErrorResponse(code = "UNKNOWN", trace_id = traceId()))
    } else {
        val traceId = traceId()
        val error = Gson().fromJson(errorBody()?.string(), RwscaErrorResponse::class.java)

        ApiResult.Failure(error.withTraceId(traceId = traceId))
    }
}