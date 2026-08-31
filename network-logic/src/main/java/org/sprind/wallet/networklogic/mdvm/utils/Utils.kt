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

import com.google.gson.Gson
import org.sprind.wallet.networklogic.utils.traceId
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.MdvmApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse
import org.sprind.wallet.networklogic.mdvm.model.error.withTraceId
import retrofit2.Response

internal inline fun <reified T> Response<T>.toMdvmApiResult(): MdvmApiResult<T> {
    return if (isSuccessful) {
        val body = body() ?: if (T::class == Unit::class) @Suppress("UNCHECKED_CAST") (Unit as T) else null
        body?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(MdvmErrorResponse(code = "UNKNOWN", trace_id = traceId()))
    } else {
        val traceId = traceId()
        val errorText = errorBody()?.string()
        @Suppress("UselessCallOnNotNull") val error = errorText
            ?.let { runCatching { Gson().fromJson(it, MdvmErrorResponse::class.java) }.getOrNull() }
            ?.takeIf { !it.code.isNullOrBlank() }   // reject schema-invalid bodies
            ?: MdvmErrorResponse(code = "UNKNOWN", description = errorText)

        ApiResult.Failure(
            error = error.withTraceId(traceId)
        )
    }
}