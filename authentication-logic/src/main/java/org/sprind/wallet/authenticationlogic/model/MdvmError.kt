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

package org.sprind.wallet.authenticationlogic.model

import org.sprind.wallet.networklogic.common.NetEntity
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType
import org.sprind.wallet.networklogic.mdvm.model.error.toMdvmErrorType

/**
 * Application-level representation of an error that occurred during an MDVM operation.
 *
 * [type] identifies the error. [serverResponse] carries the full server response for server-side
 * errors and is null for client-side errors (i.e. when [MdvmErrorType.errorSource] is
 * [NetEntity.CLIENT]).
 */
data class MdvmError(
    val type: MdvmErrorType,
    val serverResponse: MdvmErrorResponse?,
) {
    init { require(type.errorSource.isServer == (serverResponse != null)) }
    val code: String get() = type.code
    val traceId: String? get() = serverResponse?.trace_id
}

typealias MdvmResult<T> = ApiResult<T, MdvmError>

/** Converts this server error response to an application-level [MdvmError]. */
fun MdvmErrorResponse.toMdvmError(): MdvmError =
    MdvmError(toMdvmErrorType(), serverResponse = this)
