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

package org.sprind.wallet.networklogic.pushnotifications.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.sprind.wallet.networklogic.common.model.WithTraceId

/**
 * Error response body returned by push notifications endpoints on failure.
 *
 * @property errorCode The machine-readable error code.
 * @property description Optional human-readable description.
 * @property timestamp Optional timestamp of the error.
 * @property traceId Optional trace ID for correlating with backend logs.
 */
@Serializable
data class PushNotificationsErrorResponse(
    @SerialName("code")
    val errorCode: String,
    val description: String? = null,
    val timestamp: String? = null,
    @SerialName("trace_id")
    val traceId: String? = null,
) : WithTraceId<PushNotificationsErrorResponse> {
    override fun withTraceId(traceId: String?): PushNotificationsErrorResponse =
        this.copy(traceId = traceId)
}