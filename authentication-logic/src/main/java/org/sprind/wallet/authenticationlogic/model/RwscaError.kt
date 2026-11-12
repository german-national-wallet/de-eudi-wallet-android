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
import org.sprind.wallet.networklogic.common.NetworkFailure
import org.sprind.wallet.networklogic.common.toNetworkFailure
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType
import org.sprind.wallet.networklogic.rwsca.model.error.toRwscaErrorType
import org.sprind.wallet.networklogic.trace.traceId
import org.sprind.wallet.networklogic.utils.NO_INTERNET_ERROR_CODE

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
 * [networkFailure] is set when a request failed in transit with a failure that has its own
 * error dialog (see [toNetworkFailure]).
 */
sealed class RwscaError(
    val originatingEntity: NetEntity,
    val code: String,
    val traceId: String?,
    hasServerResponse: Boolean,
) {
    init { require(originatingEntity.isServer == hasServerResponse) }

    abstract val networkFailure: NetworkFailure?

    /**
     * The request got no answer from the backend because it failed in transit, e.g. the device
     * is offline or the call timed out (WD-4685).
     */
    val isNoInternet: Boolean get() = code == NO_INTERNET_ERROR_CODE

    /**
     * @param clientTraceId The client-generated trace id of a request that never got a response.
     */
    data class FromRwsca(
        val type: RwscaErrorType,
        val serverResponse: RwscaErrorResponse?,
        val clientTraceId: String? = null,
        override val networkFailure: NetworkFailure? = null,
    ) : RwscaError(
        originatingEntity = type.errorSource,
        code = type.code,
        traceId = serverResponse?.traceId ?: clientTraceId,
        hasServerResponse = serverResponse != null,
    )

    /**
     * An RwscaError caused by an underlying MDVM operation failing with the given [mdvmError].
     */
    data class FromMdvm(
        val mdvmError: MdvmError
    ) : RwscaError(
        originatingEntity = mdvmError.type.errorSource,
        code = mdvmError.type.code,
        traceId = mdvmError.traceId,
        hasServerResponse = mdvmError.serverResponse != null,
    ) {
        override val networkFailure: NetworkFailure? get() = mdvmError.networkFailure
    }
}

typealias RwscaResult<T> = ApiResult<T, RwscaError>

/** Converts this server error response to an application-level [RwscaError]. */
fun RwscaErrorResponse.toRwscaError(): RwscaError.FromRwsca =
    RwscaError.FromRwsca(toRwscaErrorType(), serverResponse = this)

/** Converts a local failure (e.g. no connectivity) to an application-level [RwscaError]. */
fun Throwable.toRwscaError(): RwscaError.FromRwsca =
    RwscaError.FromRwsca(
        type = toRwscaErrorType(),
        serverResponse = null,
        clientTraceId = traceId(),
        networkFailure = toNetworkFailure(),
    )
