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

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.authenticationlogic.jwt.JwtSigningSpec
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey
import java.time.Clock
import java.util.Date

interface JwtSigner {
    /**
     * Creates a [JwtSigningSpec] ([SignedJWT] + signing key) from a
     * specified JwtSpec, signing key, claims, and optional header JWK.
     *
     * @param spec the JwtSpec defining the token type, algorithm, expiry, and required claims.
     * @param extraClaims: Additional claim names and values to add to the JWT, beyond
     *        [JWTClaimNames.ISSUED_AT] and [JWTClaimNames.EXPIRATION_TIME].
     * @param signingKey the key used to sign the JWT.
     * @param headerKey public key to add to the header in JWK form, if any.
     *
     * @return the constructed [JwtSigningSpec] containing a [SignedJWT] and the used signingKey.
     *
     * @throws Exception if the headerJwk is missing
     * @throws Exception if the signingKey is missing
     * @throws Exception if claimSet is missing any required claim specific in JwtSpec
     */
    fun sign(
        spec: JwtSpec,
        extraClaims: Map<String, Any>,
        signingKey: PrivateKey,
        headerKey: ECPublicKey?,
    ): JwtSigningSpec
}

class JwtSignerImpl(private val javaClock: Clock): JwtSigner {
    override fun sign(
        spec: JwtSpec,
        extraClaims: Map<String, Any>,
        signingKey: PrivateKey,
        headerKey: ECPublicKey?,
    ): JwtSigningSpec {
        val headerJwk = headerKey?.toECJWK()
        val header =
            JWSHeader.Builder(spec.algorithm)
                .type(JOSEObjectType(spec.type))
                .jwk(headerJwk)

        val now = javaClock.instant()

        val claimsSet = JWTClaimsSet.Builder()
        // Add the extraClaims first to maintain maximum compatibility with previous versions;
        // I'm not sure if the order matters.
        for ((name, value) in extraClaims) {
            claimsSet.claim(name, value)
        }
        claimsSet
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(spec.expireAfter)))

        val jwtClaimsSet = claimsSet.build()
        val availableClaimsInJwt = jwtClaimsSet.claims

        for (claim in spec.requiredClaims) {
            if (!availableClaimsInJwt.containsKey(claim)) {
                throw RuntimeException("Required Claim missing in JWT: $claim")
            }
        }
        val jwt = SignedJWT(header.build(), jwtClaimsSet)
        jwt.sign(ECDSASigner(signingKey, Curve.P_256))
        return JwtSigningSpec(
            signedJwt = jwt,
            signingKey = signingKey,
            publicKey = headerKey,
        )
    }

}
