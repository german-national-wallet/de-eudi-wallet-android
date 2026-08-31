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

@Serializable
data class RwscaPinSession(
    /*
    Example:
    "header": {
      "typ": "rwsca-pin-session-token",
      "alg": "HS256"
    },
    "payload": {
      "iss": "german-national-wallet:wallet-backend",
      "rwsca_account_id": "885a5160-225f-43c8-b785-0c1de8c9d756",
      "exp": 1772633560
    }
     */
    val rwsca_pin_session_token_jwt: String,
) {
    val rwscaPinSessionTokenJwt: SignedJWT
        get() = SignedJWT.parse(rwsca_pin_session_token_jwt)

    val expiry: Instant
        get() = rwscaPinSessionTokenJwt.jwtClaimsSet.getDateClaim(
            JWTClaimNames.EXPIRATION_TIME // "exp"
        ).toInstant().toKotlinInstant()
}
