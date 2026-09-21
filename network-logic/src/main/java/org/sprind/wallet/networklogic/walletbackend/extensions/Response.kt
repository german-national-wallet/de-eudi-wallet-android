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

package org.sprind.wallet.networklogic.walletbackend.extensions

import org.sprind.wallet.networklogic.common.extensions.toApiResult
import org.sprind.wallet.networklogic.common.model.WithTraceId
import org.sprind.wallet.networklogic.walletbackend.model.WalletApiResult
import org.sprind.wallet.networklogic.walletbackend.model.response.WalletErrorResponse
import retrofit2.Response

internal const val UNKNOWN_WALLET_ERROR_CODE = "Unknown Wallet Error"

/**
 * Converts this Retrofit [Response] into a [WalletApiResult], using [WalletErrorResponse] as
 * the error type.
 *
 * Delegates to [Response.toApiResult] with a factory that produces a [WalletErrorResponse]
 * whose `errorCode` is [UNKNOWN_WALLET_ERROR_CODE]. Note that, unlike the mdvm/rwsca variants,
 * the raw error text is not carried into the fallback error (the [WalletErrorResponse] shape
 * exposes only `errorCode` and `traceId`). The response's `X-trace-Id` header, if present,
 * is attached to the resulting error via [WithTraceId.withTraceId].
 *
 * Branch-level behavior (success vs. failure, JSON decode vs. fallback, trace-id propagation)
 * is documented on [Response.toApiResult] and tested by
 * `org.sprind.wallet.networklogic.common.extensions.ResponseTest`. The tests in
 * `org.sprind.wallet.networklogic.walletbackend.extensions.ResponseTest` pin the
 * walletbackend-specific factory wiring and the [WalletErrorResponse] serialization /
 * `withTraceId` integration end-to-end.
 *
 * @param T The successful body type declared by the Retrofit endpoint.
 */
internal inline fun <reified T> Response<T>.toWalletApiResult(): WalletApiResult<T> =
    this.toApiResult { WalletErrorResponse(errorCode = UNKNOWN_WALLET_ERROR_CODE) }