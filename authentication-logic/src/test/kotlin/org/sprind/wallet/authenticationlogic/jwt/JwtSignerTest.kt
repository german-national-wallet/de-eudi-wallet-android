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
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import eu.europa.ec.authenticationlogic.jwt.JwtSigningSpec
import org.junit.Before
import org.junit.Test
import org.multipaz.util.toBase64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtSignerImplTest {

    private lateinit var jwtSigner: JwtSigner

    private lateinit var keyPair: KeyPair
    private lateinit var privateKey: ECPrivateKey
    private lateinit var publicKey: ECPublicKey

    private val validityDuration = Duration.ofMinutes(30)

    private val now = Instant.parse("2024-01-01T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Before
    fun setup() {
        jwtSigner = JwtSignerImpl(clock)
        keyPair = generateKeyPairForTest()
        privateKey = keyPair.private as ECPrivateKey
        publicKey = keyPair.public as ECPublicKey
    }

    @Test
    fun `sign should create valid signed JWT with correct header and claims`() {
        // Given
        val spec = createSpecForTest(requiredClaims = setOf("foo"))
        val extraClaims = mapOf(
            "foo" to "bar",
            "custom" to "value",
            "age" to 42,
        )

        // When
        val result: JwtSigningSpec = jwtSigner.sign(
            spec = spec,
            extraClaims = extraClaims,
            signingKey = privateKey,
            headerKey = publicKey
        )

        // Then
        val signedJwt = result.signedJwt

        // Header assertions
        assertEquals("ES256", signedJwt.header.algorithm.name)
        assertEquals("test+jwt", signedJwt.header.type.type)
        assertNotNull(signedJwt.header.jwk)

        // Claim assertions
        val claims = signedJwt.jwtClaimsSet
        assertEquals("bar", claims.getStringClaim("foo"))
        assertEquals("value", claims.getStringClaim("custom"))
        assertEquals(expected = 42L, claims.getLongClaim("age"))
        assertEquals(Date.from(now), claims.issueTime)
        assertEquals(
            Date.from(now.plus(validityDuration)),
            claims.expirationTime
        )

        // Signature verification
        val verifier = ECDSAVerifier(publicKey)
        assertTrue(signedJwt.verify(verifier))
    }

    @Test
    fun `sign should omit jwk from header when headerKey is null`() {
        val spec = createSpecForTest()

        val result = jwtSigner.sign(
            spec = spec,
            extraClaims = emptyMap(),
            signingKey = privateKey,
            headerKey = null
        )

        assertNull(result.signedJwt.header.jwk)
    }

    @Test
    fun `sign should throw if required claim is missing`() {
        val spec = createSpecForTest(requiredClaims = setOf("required-claim"))

        val exception = assertFailsWith<RuntimeException> {
            jwtSigner.sign(
                spec = spec,
                extraClaims = emptyMap(), // missing "required-claim"
                signingKey = privateKey,
                headerKey = publicKey
            )
        }

        assertEquals(
            "Required Claim missing in JWT: required-claim",
            exception.message
        )
    }

    @Test
    fun `sign should include issuedAt and expiration even when no extra claims provided`() {
        val spec = createSpecForTest()

        val result = jwtSigner.sign(
            spec = spec,
            extraClaims = emptyMap(),
            signingKey = privateKey,
            headerKey = publicKey
        )

        val claims = result.signedJwt.jwtClaimsSet

        assertEquals(Date.from(now), claims.issueTime)
        assertEquals(
            Date.from(now.plus(validityDuration)),
            claims.expirationTime
        )
    }

    @Test
    fun `signature should fail verification with different public key`() {
        val spec = createSpecForTest()

        val result = jwtSigner.sign(
            spec = spec,
            extraClaims = emptyMap(),
            signingKey = privateKey,
            headerKey = publicKey
        )

        // Generate a different key pair
        val otherKeyPair = generateKeyPairForTest()
        assertNotEquals(keyPair.public.encoded.toBase64(), otherKeyPair.public.encoded.toBase64())
        val wrongPublicKey = otherKeyPair.public as ECPublicKey
        val verifier = ECDSAVerifier(wrongPublicKey)

        assertFalse(result.signedJwt.verify(verifier))
    }

    private fun createSpecForTest(
        requiredClaims: Set<String> = setOf()
    ): JwtSpec {
        return JwtSpec(
            type = "test+jwt",
            algorithm = JWSAlgorithm.ES256,
            expireAfter = validityDuration,
            requiredClaims = requiredClaims
        )
    }

}

private fun generateKeyPairForTest(): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    keyPairGenerator.initialize(Curve.P_256.toECParameterSpec())
    return keyPairGenerator.generateKeyPair()
}
