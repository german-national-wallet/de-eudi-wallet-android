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

package org.sprind.wallet.networklogic.mdvm.model.request

import kotlinx.serialization.Serializable

@Serializable
data class MdvmRenewalRequest(
    val wi_device_class: Map<String, String>,
    val wi_android_key_attestation: List<String>,
    // Empty until the backend makes this field optional; remove then.
    val pap_playintegrity_attestation: String,
)
