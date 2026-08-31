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

package org.sprind.wallet.networklogic.common.model

sealed class ApiResult<out Res, out Err> {
    data class Success<Res>(val response: Res) : ApiResult<Res, Nothing>()
    data class Failure<Err>(val error: Err) : ApiResult<Nothing, Err>()
}

/**
 * Transforms the result type of this ApiResult (for example to a type suitable for
 * a different abstraction level) using the given [mapper], or returns [this] if
 * it is an error.
 */
fun <ResOld, ResNew, Err> ApiResult<ResOld, Err>.map(mapper: (ResOld) -> ResNew): ApiResult<ResNew, Err> = when (this) {
    is ApiResult.Success<ResOld> -> ApiResult.Success(mapper(response))
    is ApiResult.Failure<Err>-> this
}

/**
 * Transforms the error type of this ApiResult (for example to a type suitable for
 * a different abstraction level) using the given [mapper], or returns [this] if
 * it is not an error.
 */
fun <Res, ErrOld, ErrNew> ApiResult<Res, ErrOld>.mapError(mapper: (ErrOld) -> ErrNew): ApiResult<Res, ErrNew> = when (this) {
    is ApiResult.Success<Res> -> this
    is ApiResult.Failure<ErrOld> -> ApiResult.Failure(mapper(error))
}
