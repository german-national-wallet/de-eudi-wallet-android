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

package org.sprind.wallet.networklogic.rwsca.model.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.sprind.wallet.networklogic.common.model.WithTraceId

@Serializable
data class RwscaErrorResponse(
    val code: String,
    val description: String? = null,
    val timestamp: String? = null, // date-time
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("try_counter") val tryCounter: Int? = null, // int32
    @SerialName("try_allowed_after") val tryAllowedAfter: String? = null, // date-time
): WithTraceId<RwscaErrorResponse> {
    override fun withTraceId(traceId: String?): RwscaErrorResponse = copy(traceId = traceId)

}