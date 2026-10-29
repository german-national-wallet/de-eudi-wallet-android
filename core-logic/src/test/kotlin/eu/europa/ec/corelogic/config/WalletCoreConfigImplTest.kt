/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.europa.ec.corelogic.config

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.jwk.Curve
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.eudi.openid4vci.CredentialResponseEncryptionPolicy
import eu.europa.ec.eudi.openid4vci.EcConfig
import eu.europa.ec.eudi.openid4vci.EncryptionSupportConfig
import eu.europa.ec.eudi.openid4vci.IssuerMetadataPolicy
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationHandler
import eu.europa.ec.eudi.wallet.issue.openid4vci.dpop.DPopConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WalletCoreConfigImplTest {

    private val storage = mockk<Storage>(relaxed = true)
    private val rwscSecureArea = mockk<SecureArea>(relaxed = true)
    private val androidKeystoreSecureArea = mockk<SecureArea>(relaxed = true)

    private val subject = WalletCoreConfigImpl(
        context = RuntimeEnvironment.getApplication(),
        configLogic = mockk<ConfigLogic>(relaxed = true).apply {
            every { environmentConfig.pidIssuerURL } returns PID_ISSUER_URL
        },
        ausweisSdkAuthorizationHandler = mockk<AuthorizationHandler>(relaxed = true),
        rwscSecureArea = rwscSecureArea,
        androidKeystoreSecureArea = androidKeystoreSecureArea,
        storage = storage,
    )

    @Test
    fun `every vci config stores issuance metadata in the encrypted storage`() {
        assertTrue(subject.vciConfig.isNotEmpty())
        subject.vciConfig.values.forEach { assertSame(storage, it.issuanceMetadataStorage) }
    }

    @Test
    fun `no vci config uses the default DPoP config, which persists metadata unencrypted`() {
        subject.vciConfig.values.forEach { assertNotEquals(DPopConfig.Default, it.dpopConfig) }
    }

    @Test
    fun `default DPoP config keeps its keys in the shared android keystore secure area`() {
        assertSame(androidKeystoreSecureArea, subject.defaultDPopConfig.secureArea)
    }

    @Test
    fun `key attested configs use the encrypted config for their provisional key`() {
        val keyAttested = subject.vciConfig.values.map { it.dpopConfig }
            .filterIsInstance<DPopConfig.KeyAttested>()
        assertTrue(keyAttested.isNotEmpty())
        keyAttested.forEach { assertSame(subject.defaultDPopConfig, it.provisionalConfig) }
    }

    @Test
    fun `every vci config requires signed issuer metadata`() {
        subject.vciConfig.values.forEach {
            assertTrue(it.issuerMetadataPolicy is IssuerMetadataPolicy.RequireSigned)
        }
    }

    @Test
    fun `the default vci config requires signed issuer metadata`() {
        assertTrue(subject.defaultVciConfig.issuerMetadataPolicy is IssuerMetadataPolicy.RequireSigned)
    }

    @Test
    fun `every vci config uses the approved response encryption allowlist`() {
        (subject.vciConfig.values + subject.defaultVciConfig).forEach {
            val encryption = it.responseEncryptionConfig
            assertNull(encryption.rsaConfig)
            assertEquals(CredentialResponseEncryptionPolicy.REQUIRED, encryption.credentialResponseEncryptionPolicy)
            assertEquals(Curve.P_256, encryption.ecConfig?.ecKeyCurve)
            assertEquals(APPROVED_ALGORITHMS, encryption.supportedEncryptionAlgorithms)
            assertEquals(APPROVED_METHODS, encryption.supportedEncryptionMethods)
        }
    }

    @Test
    fun `weak JWE primitives are not allowed`() {
        val encryption = WalletCoreConfigImpl.RESPONSE_ENCRYPTION_CONFIG
        listOf(JWEAlgorithm.RSA1_5, JWEAlgorithm.RSA_OAEP_256, JWEAlgorithm.A128KW, JWEAlgorithm.DIR)
            .forEach { assertFalse(it in encryption.supportedEncryptionAlgorithms) }
        listOf(EncryptionMethod.A128CBC_HS256, EncryptionMethod.A256CBC_HS512)
            .forEach { assertFalse(it in encryption.supportedEncryptionMethods) }
    }

    @Test
    fun `the approved allowlist is still supported by the nimbus version in use`() {
        assertTrue(EcConfig.SUPPORTED_ENCRYPTION_ALGORITHMS.containsAll(APPROVED_ALGORITHMS))
        assertTrue(EncryptionSupportConfig.SUPPORTED_ENCRYPTION_METHODS.containsAll(APPROVED_METHODS))
    }

    private companion object {
        val APPROVED_ALGORITHMS = listOf(JWEAlgorithm.ECDH_ES)
        val APPROVED_METHODS = listOf(EncryptionMethod.A256GCM, EncryptionMethod.A128GCM)

        const val PID_ISSUER_URL = "https://pid-issuer.example.com"
    }
}
