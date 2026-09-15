/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.provider

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.securearea.RwscaKeyInfo
import eu.europa.ec.eudi.openid4vci.Nonce
import eu.europa.ec.networklogic.repository.WalletAttestationRepository
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPublicKey
import org.multipaz.securearea.KeyAttestation
import org.multipaz.securearea.KeyInfo
import org.sprind.wallet.authenticationlogic.model.decodeBase64EcPublicKey

class WalletCoreAttestationProviderImplTest {

    private val walletCoreConfig = mockk<WalletCoreConfig>(relaxed = true)
    private val walletAttestationRepository = mockk<WalletAttestationRepository>(relaxed = true)

    private val subject = WalletCoreAttestationProviderImpl(
        walletCoreConfig = walletCoreConfig,
        walletAttestationRepository = walletAttestationRepository,
    )

    // A real P-256 SubjectPublicKeyInfo base64 key, reused across the constructed KeyInfos.
    private val base64EncodedPublicKey =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECsoA5k+f1g3u/HCO0wI/aZ4fGjokL4mZVz22G+i5YmS5CRe5SJS2s+xkWANg4yD9Ff62jIu8pLz3s8J75g9z4w=="
    private val publicKey: EcPublicKey = decodeBase64EcPublicKey(base64EncodedPublicKey)

    @Test
    fun `getKeyAttestation returns the stored WTE when the nonce claim matches the create-key nonce`() =
        runTest {
            val nonce = "create-key-c-nonce"
            val wte = signedWteWithNonce(nonce)
            val keys = listOf(rwscaKey(wte, nonce), rwscaKey(wte, nonce))

            val result = subject.getKeyAttestation(keys, nonce = null)

            assertTrue(result.isSuccess)
            assertEquals(wte, result.getOrNull())
        }

    @Test
    fun `getKeyAttestation ignores the incoming credential-proof nonce for rWSCA keys`() =
        runTest {
            val createKeyNonce = "create-key-c-nonce"
            val wte = signedWteWithNonce(createKeyNonce)
            val keys = listOf(rwscaKey(wte, createKeyNonce))

            // The incoming nonce is the credential-proof callback c_nonce, which differs
            // from the create-key nonce the WTE is bound to. It must not be consulted: the
            // stored WTE is returned as long as it matches the stored create-key nonce.
            val result = subject.getKeyAttestation(keys, nonce = Nonce("a-later-callback-nonce"))

            assertTrue(result.isSuccess)
            assertEquals(wte, result.getOrNull())
        }

    @Test
    fun `getKeyAttestation returns the WTE without parsing when no create-key nonce was stored`() =
        runTest {
            // A null stored nonce means no nonce metadata is available, so validation is
            // skipped and the (here intentionally non-JWT) WTE is returned untouched.
            val wte = "opaque-wte-not-a-jwt"
            val keys = listOf(rwscaKey(wte, nonce = null))

            val result = subject.getKeyAttestation(keys, nonce = null)

            assertTrue(result.isSuccess)
            assertEquals(wte, result.getOrNull())
        }

    @Test
    fun `getKeyAttestation fails with a missing-WTE error when every rWSCA key has a null WTE`() =
        runTest {
            val keys = listOf(rwscaKey(wte = null, nonce = null), rwscaKey(wte = null, nonce = null))

            val result = subject.getKeyAttestation(keys, nonce = null)

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()?.message.orEmpty().contains("missing the WTE")
            )
        }

    @Test
    fun `getKeyAttestation fails when rWSCA keys carry differing WTEs`() = runTest {
        val keys = listOf(rwscaKey("wte-a", nonce = null), rwscaKey("wte-b", nonce = null))

        val result = subject.getKeyAttestation(keys, nonce = null)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("do not share a single WTE")
        )
    }

    @Test
    fun `getKeyAttestation fails when rWSCA keys share a WTE but carry differing nonces`() = runTest {
        val keys = listOf(rwscaKey("wte", "nonce-a"), rwscaKey("wte", "nonce-b"))

        val result = subject.getKeyAttestation(keys, nonce = null)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("do not share a single WTE nonce")
        )
    }

    @Test
    fun `getKeyAttestation fails on a mix of rWSCA and non-rWSCA keys`() = runTest {
        val keys = listOf(rwscaKey("wte", nonce = null), plainKey())

        val result = subject.getKeyAttestation(keys, nonce = null)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("mixed rWSCA and non-rWSCA")
        )
    }

    @Test
    fun `getKeyAttestation fails when the WTE nonce claim does not match the create-key nonce`() =
        runTest {
            val wte = signedWteWithNonce("a-different-nonce")
            val keys = listOf(rwscaKey(wte, "create-key-c-nonce"))

            val result = subject.getKeyAttestation(keys, nonce = null)

            assertTrue(result.isFailure)
        }

    private fun rwscaKey(wte: String?, nonce: String?): RwscaKeyInfo = RwscaKeyInfo(
        alias = "rwsca-alias",
        publicKey = publicKey,
        attestation = KeyAttestation(publicKey, certChain = null),
        algorithm = Algorithm.ESP256,
        walletTrustEvidence = wte,
        walletTrustEvidenceNonce = nonce,
    )

    private fun plainKey(): KeyInfo = PlainKeyInfo()

    private inner class PlainKeyInfo : KeyInfo(
        alias = "plain-alias",
        publicKey = publicKey,
        attestation = KeyAttestation(publicKey, certChain = null),
        algorithm = Algorithm.ESP256,
    )

    private fun signedWteWithNonce(nonce: String): String {
        val ecJwk = ECKeyGenerator(Curve.P_256).generate()
        val claims = JWTClaimsSet.Builder().claim("nonce", nonce).build()
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.ES256), claims)
        jwt.sign(ECDSASigner(ecJwk))
        return jwt.serialize()
    }
}
