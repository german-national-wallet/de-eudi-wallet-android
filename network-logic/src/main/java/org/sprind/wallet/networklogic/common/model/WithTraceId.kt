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

/**
 * A type that can carry the trace id propagated from an HTTP response, so that errors and
 * downstream artifacts can be correlated end-to-end with the request that produced them.
 *
 * This is the error-side contract that pairs with [ApiResult]: a failed [ApiResult.Failure]
 * carries an error value, and when that error implements [WithTraceId], the response-to-result
 * adapter can stamp the request's trace id onto it for downstream correlation.
 *
 * The trace id typically originates from the `X-trace-Id` response header (extracted by
 * `org.sprind.wallet.networklogic.common.extensions.traceId`) and is attached to error types
 * after a failed response is parsed by
 * `org.sprind.wallet.networklogic.common.extensions.toApiResult`. Concrete implementors are
 * the per-backend error response models, e.g. `MdvmErrorResponse`, `RwscaErrorResponse`, and
 * `WalletErrorResponse`.
 *
 * @param T The type returned by [withTraceId]. Conventionally the implementing type itself
 *          (self-type bound), so the trace id can be set fluently without changing the
 *          static type of the instance.
 */
fun interface WithTraceId<T> {
    /**
     * Returns a copy of this instance tagged with the given [traceId].
     *
     * @param traceId The trace id to attach, as extracted from the response, or `null` when
     *                no trace id was present.
     */
    fun withTraceId(traceId: String?): T;
}