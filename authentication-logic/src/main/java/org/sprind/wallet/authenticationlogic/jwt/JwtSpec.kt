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

package org.sprind.wallet.authenticationlogic.jwt

import com.nimbusds.jose.JWSAlgorithm
import java.time.Duration

/**
 * Data class representing the specifications of a JWT, including type, algorithm, expiry,
 * and required claims.
 *
 * @property type the JWT type.
 * @property algorithm the algorithm to use for signing, e.g., JWSAlgorithm.ES256.
 * @property expireAfter the duration after which the JWT will expire.
 * @property requiredClaims a set of required claims for the JWT.
 */
data class JwtSpec(
    val type: String,
    val algorithm: JWSAlgorithm,
    val expireAfter: Duration = Duration.ZERO,
    val requiredClaims: Set<String> = emptySet(),
)