package eu.europa.ec.authenticationlogic.controller.appattestation.jwt

import android.security.keystore.KeyProperties
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.authenticationlogic.jwt.JwtBuilder
import org.sprind.wallet.businesslogic.controller.crypto.EcKeyPairController
import eu.europa.ec.businesslogic.controller.log.LogController
import org.sprind.wallet.businesslogic.util.RandomUUIDGenerator
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.jwt.JwtSigner
import org.sprind.wallet.authenticationlogic.jwt.JwtSignerImpl
import org.sprind.wallet.businesslogic.controller.storage.KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwtBuilderTest {

    @Mock
    private lateinit var ecKeyPairController: EcKeyPairController


    @Mock
    private lateinit var mockKeyPair: KeyPair

    @Mock
    private lateinit var mockPrivateKey: ECPrivateKey

    @Mock
    private lateinit var mockPublicKey: ECPublicKey

    @Mock
    private lateinit var randomUUIDGenerator: RandomUUIDGenerator

    @Mock
    private lateinit var logController: LogController

    private val fakeJwtParser = FakeJwtParser()

    private lateinit var closeable: AutoCloseable

    private val now = Instant.parse("2024-03-22T12:00:00.000Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val jwtSigner: JwtSigner = JwtSignerImpl(clock)

    private val jwtBuilder: JwtBuilder by lazy {
        JwtBuilder(
            ecKeyPairController = ecKeyPairController,
            randomUUIDGenerator = randomUUIDGenerator,
            logController = logController,
            jwtParser = fakeJwtParser,
            jwtSigner = jwtSigner
        )
    }

    private val mockUUID = "350337f6-76e7-456b-8bf9-1b7edd80d1b89139dd95-dc5d-4386-a4e3-53b0"

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)

        whenever(randomUUIDGenerator.generate()).thenReturn(mockUUID)

        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
        keyPairGenerator.initialize(Curve.P_256.toECParameterSpec())
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `parseJwt should parse valid serialized JWT and return SignedJWT`() {
        // Jwt with "wb_auth_challenge" having the value "test" in the claim
        val testJwtStringWithNonceClaim =
            "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImp3dCJ9.eyJ3Yl9hdXRoX2NoYWxsZW5nZSI6ICJ0ZXN0In0.sig\n"

        val signedJwt = fakeJwtParser.parseJwt(testJwtStringWithNonceClaim)

        assertNotNull(signedJwt, "Parsed JWT should not be null")
        assertEquals("test", signedJwt.jwtClaimsSet.getClaim("wb_auth_challenge"))
    }

    @Test
    fun `createInstancePopJwt should create a valid JWT with provided nonce, current issued time and expiration time and save hardware key data`() =
        runTest {
            val keystoreAlias = KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS
            val purposes = KeyProperties.PURPOSE_SIGN

            // Given
            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            keyPairGenerator.initialize(Curve.P_256.toECParameterSpec())
            val keyPair = keyPairGenerator.generateKeyPair()
            val privateKey = keyPair.private as ECPrivateKey
            val publicKey = keyPair.public as ECPublicKey

            whenever(ecKeyPairController.getEcKeyPair(keystoreAlias)).thenReturn(mockKeyPair)
            whenever(mockKeyPair.private).thenReturn(privateKey)
            whenever(mockKeyPair.public).thenReturn(publicKey)

            val expiryInstant = now.plus(Duration.ofMinutes(60))
            val testNonce = "test-nonce"

            // Then
            val jwt = jwtBuilder.createInstancePopJwt(testNonce)

            assertNotNull(jwt, "Generated JWT should not be null")
            verify(ecKeyPairController).generateEcKeyPair(keystoreAlias, purposes)

            val signedJwt = SignedJWT.parse(jwt)

            assertEquals(testNonce, signedJwt.jwtClaimsSet.getClaim("wb_auth_challenge"))
            assertEquals(Date.from(now).time, signedJwt.jwtClaimsSet.issueTime.time)
            assertEquals(
                Date.from(expiryInstant).time,
                signedJwt.jwtClaimsSet.expirationTime.time
            )
            assertEquals("wi-wb-auth-pop+jwt", signedJwt.header.type.toString())
            assertEquals("ES256", signedJwt.header.algorithm.toString())
        }

    @Test
    fun `createInstancePopJwt should invoke ecKeyPairController`() =
        runTest {
            val keystoreAlias = KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS
            val purposes = KeyProperties.PURPOSE_SIGN

            // Given
            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            keyPairGenerator.initialize(Curve.P_256.toECParameterSpec())
            val keyPair = keyPairGenerator.generateKeyPair()
            val testNonce = "test-nonce"

            val privateKey = keyPair.private as ECPrivateKey
            val publicKey = keyPair.public as ECPublicKey

            whenever(ecKeyPairController.getEcKeyPair(keystoreAlias)).thenReturn(mockKeyPair)
            whenever(mockKeyPair.private).thenReturn(privateKey)
            whenever(mockKeyPair.public).thenReturn(publicKey)

            // Then
            jwtBuilder.createInstancePopJwt(testNonce)

            verify(ecKeyPairController).generateEcKeyPair(keystoreAlias, purposes)
        }

    @Test
    fun `createInstancePopJwt should throw exception if generateEcKeyPair fails`() = runTest {
        val keystoreAlias = KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS
        val purposes = KeyProperties.PURPOSE_SIGN

        // Given
        val nonce = "test-nonce"

        whenever(ecKeyPairController.getEcKeyPair(keystoreAlias)).thenReturn(mockKeyPair)
        whenever(mockKeyPair.private).thenReturn(mockPrivateKey)
        whenever(mockKeyPair.public).thenReturn(mockPublicKey)

        doThrow(RuntimeException("Failed to create key in keystore"))
            .`when`(ecKeyPairController)
            .generateEcKeyPair(keystoreAlias, purposes)

        // Then
        val exception = assertFailsWith<RuntimeException> {
            jwtBuilder.createInstancePopJwt(nonce)
        }

        verify(ecKeyPairController, never()).getEcKeyPair(keystoreAlias)
        assertEquals("Failed to create key in keystore", exception.message)
    }

    @Test
    fun `createInstancePopJwtWithSavedKeys should create a valid JWT with provided nonce, current issued time and expiration time`() =
        runTest {
            val keystoreAlias = KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS

            // Sample Key Generation that can be used for jwt creation
            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            keyPairGenerator.initialize(Curve.P_256.toECParameterSpec())
            val keyPair = keyPairGenerator.generateKeyPair()
            val publicKey = keyPair.public as ECPublicKey

            // Getting the stored private key
            whenever(ecKeyPairController.hasEcKeyPair(keystoreAlias)).thenReturn(true)
            whenever(ecKeyPairController.getEcKeyPair(keystoreAlias)).thenReturn(keyPair)

            // Given
            val expiryInstant = now.plus(Duration.ofMinutes(60))
            val testNonce = "test-nonce"

            // Then
            val jwt = jwtBuilder.createInstancePopJwtWithSavedKeys(testNonce)

            assertNotNull(jwt, "Generated JWT should not be null")

            val signedJwt = SignedJWT.parse(jwt)

            assertEquals(testNonce, signedJwt.jwtClaimsSet.getClaim("wb_auth_challenge"))
            assertEquals(Date.from(now).time, signedJwt.jwtClaimsSet.issueTime.time)
            assertEquals(
                Date.from(expiryInstant).time,
                signedJwt.jwtClaimsSet.expirationTime.time
            )
            assertEquals("wi-wb-auth-pop+jwt", signedJwt.header.type.toString())
            assertEquals("ES256", signedJwt.header.algorithm.toString())
            assertNull(signedJwt.header.jwk)

            //Verification of the jwt being signed with the private key
            //signedJwt.verify(verifier) will return true only if the signature was generated using the correct private key
            val verifier = ECDSAVerifier(publicKey)
            assertTrue(signedJwt.verify(verifier), "JWT signature should be valid")
        }

    @Test
    fun `createInstancePopJwtWithSavedKeys should invoke ecKeyPairController`() =
        runTest {
            val keystoreAlias = KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS
            val purposes = KeyProperties.PURPOSE_SIGN

            // Given
            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            keyPairGenerator.initialize(Curve.P_256.toECParameterSpec())
            val keyPair = keyPairGenerator.generateKeyPair()

            val testNonce = "test-nonce"

            whenever(ecKeyPairController.hasEcKeyPair(keystoreAlias)).thenReturn(true)
            whenever(ecKeyPairController.getEcKeyPair(keystoreAlias)).thenReturn(keyPair)

            // Then
            jwtBuilder.createInstancePopJwtWithSavedKeys(testNonce)
            verify(ecKeyPairController, never()).generateEcKeyPair(keystoreAlias, purposes)
            verify(ecKeyPairController).hasEcKeyPair(keystoreAlias)
        }

    @Test
    fun `createInstancePopJwtWithSavedKeys should be null if there is no key stored for wallet instance`() =
        runTest {
            val keystoreAlias = KEYSTORE_ALIAS_WALLET_INSTANCE_AUTH_KEYS

            // Given
            val nonce = "test-nonce"

            whenever(ecKeyPairController.hasEcKeyPair(keystoreAlias)).thenReturn(false)

            // Then
            val result = jwtBuilder.createInstancePopJwtWithSavedKeys(nonce)
            assertNull(result)
            verify(ecKeyPairController, never()).getEcKeyPair(keystoreAlias)
        }
}