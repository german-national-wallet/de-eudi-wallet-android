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

import com.google.gson.annotations.SerializedName

/**
 * Response body for the push notifications challenge endpoint (`POST /v1/pns/challenge`).
 *
 * @property authChallenge The push notifications auth challenge JWT.
 */
data class PushNotificationsChallengeResponse(
    @SerializedName("pns_auth_challenge")
    val authChallenge: String,
)