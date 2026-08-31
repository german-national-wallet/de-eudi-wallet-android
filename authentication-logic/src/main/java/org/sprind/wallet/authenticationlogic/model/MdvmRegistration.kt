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

import com.nimbusds.jwt.JWTClaimNames
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Persisted MDVM registration info derived from the wire formats
 * [org.sprind.wallet.networklogic.mdvm.model.response.MdvmRegisterResponse] and
 * [org.sprind.wallet.networklogic.mdvm.model.response.MdvmRenewalResponse].
 */
@Serializable
data class MdvmRegistration(
    val mdvm_wi_id: String,
    val mdvm_token: String,
    /** Alias under which the wi_mdvm_auth key pair is stored in KeyStore. */
    val wi_mdvm_auth_keys_alias: String,
) {
    val mdvmTokenJwt: SignedJWT
        get() = SignedJWT.parse(mdvm_token)
    val expiry: Instant
        get() = mdvmTokenJwt.jwtClaimsSet.getDateClaim(
            JWTClaimNames.EXPIRATION_TIME // "exp"
        ).toInstant().toKotlinInstant()
}