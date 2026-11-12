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

package eu.europa.ec.corelogic.provider

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletAttestationGenerationResult
import eu.europa.ec.authenticationlogic.model.JwtKeyPair
import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.eudi.openid4vci.AuthorizationCode
import eu.europa.ec.eudi.openid4vci.ClientAuthentication
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialIssuerId
import eu.europa.ec.eudi.openid4vci.CredentialResponseEncryptionPolicy
import eu.europa.ec.eudi.openid4vci.EncryptionSupportConfig
import eu.europa.ec.eudi.openid4vci.Issuer
import eu.europa.ec.eudi.openid4vci.OpenId4VCIConfig
import eu.europa.ec.eudi.wallet.provider.WalletAttestationKey
import eu.europa.ec.networklogic.repository.WalletAttestationRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.toEcPublicKey
import org.multipaz.securearea.AndroidKeystoreKeyInfo
import org.sprind.wallet.businesslogic.controller.crypto.EcKeyPairController
import java.net.URI
import java.security.KeyPair
import java.security.Signature
import java.util.Date

/**
 * Drives the openid4vci client against a mocked issuer whose authorization server advertises
 * `attest_jwt_client_auth`, with client authentication built the way wallet-core builds it for an
 * EAA issuer: [WalletAttestationKey.toClientAuthentication] over [WalletCoreAttestationProviderImpl].
 * Only the wallet backend (behind [AppAttestationController]) and the Android KeyStore key are
 * stand-ins, so the test pins that the WIA the backend returns is the one sent to the issuer and that
 * its PoP is signed by the wallet-core attestation key the backend bound it to.
 */
class WalletCoreAttestationProviderClientAuthenticationTest {

    private val appAttestationController = mockk<AppAttestationController>()

    // Stands in for the AndroidKeystoreSecureArea key wallet-core creates for the issuer.
    private val attestationKey: ECKey = ECKeyGenerator(Curve.P_256).generate()

    // The same key, as the provider reads it back from Android KeyStore under the alias.
    private val ecKeyPairController = mockk<EcKeyPairController> {
        every { getEcKeyPair(KEYSTORE_ALIAS) } returns
            KeyPair(attestationKey.toECPublicKey(), attestationKey.toECPrivateKey())
    }

    private val provider = WalletCoreAttestationProviderImpl(
        walletCoreConfig = mockk<WalletCoreConfig>(relaxed = true),
        walletAttestationRepository = mockk<WalletAttestationRepository>(relaxed = true),
        appAttestationController = appAttestationController,
        ecKeyPairController = ecKeyPairController,
    )
    private val keyInfo: AndroidKeystoreKeyInfo = mockk {
        every { alias } returns KEYSTORE_ALIAS
        every { algorithm } returns Algorithm.ESP256
        every { publicKey } returns attestationKey.toECPublicKey().toEcPublicKey(EcCurve.P256)
        every { isUserAuthenticationRequired } returns false
    }

    // DER-encoded ECDSA, as SecureAreaWalletKeyManager returns it.
    private val walletAttestationKey = WalletAttestationKey(keyInfo) { data ->
        Signature.getInstance("SHA256withECDSA").run {
            initSign(attestationKey.toECPrivateKey())
            update(data)
            sign()
        }
    }

    private val backendWia: String = signedWia(subject = CLIENT_ID, boundKey = attestationKey)

    private val authorizationServerRequests = mutableListOf<HttpRequestData>()

    @Test
    fun `PAR and token requests carry the backend WIA and a PoP signed by the bound attestation key`() =
        runTest {
            every { appAttestationController.generateAttestation(KEYSTORE_ALIAS) } returns
                flowOf(backendSuccess())

            val clientAuthentication = clientAuthentication().getOrThrow()
            val (issuer, _) = Issuer.makeWalletInitiated(
                config = openId4VciConfig(clientAuthentication),
                credentialIssuerId = CredentialIssuerId(ISSUER_URL).getOrThrow(),
                credentialConfigurationIdentifiers = listOf(CredentialConfigurationIdentifier(CONFIGURATION_ID)),
                httpClient = mockedHttpClient(),
            ).getOrThrow()

            val prepared = issuer.prepareAuthorizationRequest().getOrThrow()
            with(issuer) {
                prepared.authorizeWithAuthorizationCode(
                    authorizationCode = AuthorizationCode("authorization-code"),
                    serverState = prepared.state,
                ).getOrThrow()
            }

            assertEquals(
                listOf("/par", "/token"),
                authorizationServerRequests.map { it.url.encodedPath },
            )
            authorizationServerRequests.forEach { request ->
                assertEquals(backendWia, request.headers[CLIENT_ATTESTATION_HEADER])
                assertPopSignedByAttestationKey(request.headers[CLIENT_ATTESTATION_POP_HEADER])
                assertEquals(CLIENT_ID, request.formParameter("client_id"))
            }
            // The client_id is derived from the WIA up front, and that same WIA is handed to the first
            // provisioning, so the backend is asked once for the key wallet-core passed in.
            verify(exactly = 1) { appAttestationController.generateAttestation(KEYSTORE_ALIAS) }
        }

    @Test
    fun `a failed backend WIA request fails client authentication with the backend error code`() =
        runTest {
            every { appAttestationController.generateAttestation(KEYSTORE_ALIAS) } returns flowOf(
                WalletAttestationGenerationResult.Failure(errorCode = "WB_ERROR", traceId = "trace")
            )

            val result = clientAuthentication()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("WB_ERROR"))
        }

    private suspend fun clientAuthentication(): Result<ClientAuthentication.AttestationBased> =
        with(walletAttestationKey) { provider.toClientAuthentication() }

    private fun backendSuccess() = WalletAttestationGenerationResult.Success(
        WalletInstanceAttestationSpec(
            wbWiaJwt = SignedJWT.parse(backendWia),
            wiWiaPopKeyPair = mockk<JwtKeyPair>(),
        )
    )

    private fun openId4VciConfig(clientAuthentication: ClientAuthentication) = OpenId4VCIConfig(
        clientAuthentication = clientAuthentication,
        authFlowRedirectionURI = URI.create("eudi-openid4ci://authorize"),
        encryptionSupportConfig = EncryptionSupportConfig(
            credentialResponseEncryptionPolicy = CredentialResponseEncryptionPolicy.SUPPORTED,
            ecConfig = null,
            rsaConfig = null,
        ),
    )

    private fun assertPopSignedByAttestationKey(pop: String?) {
        val popJwt = SignedJWT.parse(checkNotNull(pop) { "missing $CLIENT_ATTESTATION_POP_HEADER" })
        assertEquals(JOSEObjectType(CLIENT_ATTESTATION_POP_TYPE), popJwt.header.type)
        assertTrue(popJwt.verify(ECDSAVerifier(attestationKey.toPublicJWK())))
        assertEquals(CLIENT_ID, popJwt.jwtClaimsSet.issuer)
    }

    private fun mockedHttpClient(): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when {
                    request.isGet("/.well-known/openid-credential-issuer") -> respondJson(issuerMetadata)
                    request.isGet("/.well-known/oauth-authorization-server") -> respondJson(authorizationServerMetadata)
                    request.isPost("/par") -> {
                        authorizationServerRequests += request
                        respondJson("""{"request_uri":"urn:ietf:params:oauth:request_uri:abc","expires_in":60}""")
                    }

                    request.isPost("/token") -> {
                        authorizationServerRequests += request
                        respondJson("""{"access_token":"access-token","token_type":"Bearer","expires_in":3600}""")
                    }

                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun HttpRequestData.isGet(path: String) =
        method == HttpMethod.Get && url.encodedPath.endsWith(path)

    private fun HttpRequestData.isPost(path: String) =
        method == HttpMethod.Post && url.encodedPath == path

    private fun HttpRequestData.formParameter(name: String): String? =
        parseQueryString(runBlocking { body.toByteArray() }.decodeToString())[name]

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun signedWia(subject: String, boundKey: ECKey): String {
        val walletProviderKey = ECKeyGenerator(Curve.P_256).generate()
        val now = System.currentTimeMillis()
        val claims = JWTClaimsSet.Builder()
            .issuer("german-national-wallet:wpb:test")
            .subject(subject)
            .issueTime(Date(now))
            .expirationTime(Date(now + 600_000))
            .claim("cnf", mapOf("jwk" to boundKey.toPublicJWK().toJSONObject()))
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(JOSEObjectType("oauth-client-attestation+jwt"))
            .build()
        return SignedJWT(header, claims).apply { sign(ECDSASigner(walletProviderKey)) }.serialize()
    }

    private companion object {
        const val KEYSTORE_ALIAS = "client-attestation-0123456789abcdef"
        const val CLIENT_ID = "wallet-instance-client-id"
        const val ISSUER_URL = "https://issuer.example.com"
        const val AUTHORIZATION_SERVER_URL = "https://as.example.com"
        const val CONFIGURATION_ID = "loyalty_card_sd_jwt"
        const val CLIENT_ATTESTATION_HEADER = "OAuth-Client-Attestation"
        const val CLIENT_ATTESTATION_POP_HEADER = "OAuth-Client-Attestation-PoP"
        const val CLIENT_ATTESTATION_POP_TYPE = "oauth-client-attestation-pop+jwt"

        val issuerMetadata = """
            {
              "credential_issuer": "$ISSUER_URL",
              "authorization_servers": ["$AUTHORIZATION_SERVER_URL"],
              "credential_endpoint": "$ISSUER_URL/credential",
              "nonce_endpoint": "$ISSUER_URL/nonce",
              "credential_configurations_supported": {
                "$CONFIGURATION_ID": {
                  "format": "dc+sd-jwt",
                  "scope": "$CONFIGURATION_ID",
                  "vct": "urn:eudi:loyalty_card:1",
                  "cryptographic_binding_methods_supported": ["jwk"],
                  "credential_signing_alg_values_supported": ["ES256"],
                  "proof_types_supported": {
                    "jwt": { "proof_signing_alg_values_supported": ["ES256"] }
                  }
                }
              }
            }
        """.trimIndent()

        val authorizationServerMetadata = """
            {
              "issuer": "$AUTHORIZATION_SERVER_URL",
              "authorization_endpoint": "$AUTHORIZATION_SERVER_URL/authorize",
              "token_endpoint": "$AUTHORIZATION_SERVER_URL/token",
              "pushed_authorization_request_endpoint": "$AUTHORIZATION_SERVER_URL/par",
              "response_types_supported": ["code"],
              "code_challenge_methods_supported": ["S256"],
              "token_endpoint_auth_methods_supported": ["attest_jwt_client_auth"],
              "client_attestation_signing_alg_values_supported": ["ES256"],
              "client_attestation_pop_signing_alg_values_supported": ["ES256"]
            }
        """.trimIndent()
    }
}
