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

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import java.security.interfaces.ECPublicKey

/**
 * Extension function to convert an ECPublicKey to an ECKey in JWK format.
 *
 * @receiver ECPublicKey the public key to convert.
 * @return ECKey the public key in JSON Web Key (JWK) format.
 */
fun ECPublicKey.toECJWK(): ECKey =
    ECKey.Builder(Curve.P_256, this)
        .build()