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
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType
import org.sprind.wallet.networklogic.rwsca.model.error.toRwscaErrorType

/**
 * Application-level representation of an error that occurred during an rWSCA operation.
 *
 * rWSCA operations depend on MDVM, so errors may originate from either service.
 * [FromRwsca] covers errors from the rWSCA service itself (including client-side errors
 * generated locally). [FromMdvm] covers errors that originated from the MDVM service
 * (or locally in MDVM-related logic) while executing an rWSCA operation.
 *
 * In both subtypes, [serverResponse] carries the full server response for server-side errors
 * and is null for client-side errors (i.e. when [originatingEntity] is [NetEntity.CLIENT]).
 */
sealed class RwscaError(
    val originatingEntity: NetEntity,
    val code: String,
    val traceId: String?,
    hasServerResponse: Boolean,
) {
    init { require(originatingEntity.isServer == hasServerResponse) }

    data class FromRwsca(
        val type: RwscaErrorType,
        val serverResponse: RwscaErrorResponse?,
    ) : RwscaError(type.errorSource, type.code, serverResponse?.trace_id, serverResponse != null)

    /**
     * An RwscaError caused by an underlying MDVM operation failing with the given [mdvmError].
     */
    data class FromMdvm(
        val mdvmError: MdvmError
    ) : RwscaError(
        originatingEntity = mdvmError.type.errorSource,
        code = mdvmError.type.code,
        traceId = mdvmError.serverResponse?.trace_id,
        hasServerResponse = mdvmError.serverResponse != null,
    )
}

typealias RwscaResult<T> = ApiResult<T, RwscaError>

/** Converts this server error response to an application-level [RwscaError]. */
fun RwscaErrorResponse.toRwscaError(): RwscaError.FromRwsca =
    RwscaError.FromRwsca(toRwscaErrorType(), serverResponse = this)
