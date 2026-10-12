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

package org.sprind.wallet.authenticationlogic.controller.mdvm

import com.google.gson.JsonSyntaxException
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.config.EnvironmentConfig
import eu.europa.ec.businesslogic.controller.log.LogController
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.testing.fakeCertBytes
import org.sprind.wallet.authenticationlogic.testing.generateEcKeyPairForTests
import org.sprind.wallet.authenticationlogic.testing.makeFakeCertificate
import org.sprind.wallet.authenticationlogic.model.MdvmError
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.toMdvmError
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.api.DoubleSigningMdvmApiClient
import org.sprind.wallet.networklogic.mdvm.api.MdvmApiClient
import org.sprind.wallet.networklogic.mdvm.api.MdvmSkipIntegrityChecksHeaderValue
import org.sprind.wallet.networklogic.mdvm.api.SigningMdvmApiClient
import org.sprind.wallet.networklogic.mdvm.model.MdvmApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType
import org.sprind.wallet.networklogic.mdvm.model.request.MdvmRegisterRequest
import org.sprind.wallet.networklogic.mdvm.model.request.MdvmRenewalRequest
import org.sprind.wallet.networklogic.mdvm.model.response.MdvmChallengeResponse
import org.sprind.wallet.networklogic.mdvm.model.response.MdvmRegisterResponse
import org.sprind.wallet.networklogic.mdvm.model.response.MdvmRenewalResponse
import org.sprind.wallet.networklogic.trace.TracedIOException
import java.net.UnknownHostException
import java.security.KeyPair
import java.security.KeyStoreException

class MdvmControllerTest {

    @Mock private lateinit var mdvmApiClient: MdvmApiClient
    @Mock private lateinit var mdvmKeyManager: MdvmKeyManager
    @Mock private lateinit var signingApiClient: SigningMdvmApiClient
    @Mock private lateinit var doubleSigningApiClient: DoubleSigningMdvmApiClient

    private lateinit var closeable: AutoCloseable

    private val keyPair = generateEcKeyPairForTests()
    private val fakeCertificate = makeFakeCertificate(keyPair.public)

    private val reattestKeyPair = generateEcKeyPairForTests()
    private val reattestFakeCertBytes = "fake reattest certificate encoded form".encodeUtf8()
    private val reattestFakeCertificate =
        makeFakeCertificate(reattestKeyPair.public, encoded = reattestFakeCertBytes)

    private val mdvmAuthChallenge = "fake mdvm_auth_challenge for test"
    private val challengeNonce = mdvmAuthChallenge.encodeUtf8().sha256()
    private val mdvmWiId = "fake mdvm_wi_id for test"
    private val mdvmToken = "fake mdvm_token for test"
    private val mdvmErrorResponse = MdvmErrorResponse(code = "fake error for test")
    private val mdvmError = mdvmErrorResponse.toMdvmError()
    private val wiMdvmAuthKeysAlias = AndroidMdvmKeyManager.WI_MDVM_AUTH_KEYS_ALIAS
    private val wiMdvmReattestKeysAlias = AndroidMdvmKeyManager.WI_MDVM_REATTEST_KEYS_ALIAS
    @Mock private lateinit var environmentConfig: EnvironmentConfig
    @Mock private lateinit var configLogic: ConfigLogic
    @Mock private lateinit var logController: LogController

    private val clientTraceId = "fake client trace id for test"
    private val unreachable = TracedIOException(
        traceId = clientTraceId,
        cause = UnknownHostException("no dns"),
    )

    private val subject: MdvmControllerImpl by lazy {
        MdvmControllerImpl(mdvmApiClient, mdvmKeyManager, configLogic, logController)
    }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(configLogic.environmentConfig).thenReturn(environmentConfig)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `register returns Failure when challenge request fails`() = runTest {
        whenever(mdvmApiClient.challenge()).thenReturn(ApiResult.Failure(mdvmErrorResponse))

        val result = subject.register()

        assertEquals(ApiResult.Failure(mdvmError), result)
    }

    @Test
    fun `register returns NO_INTERNET Failure with client trace id when backend is unreachable`() =
        runTest {
            // thenAnswer, because Mockito rejects a checked exception that the suspend function
            // does not declare.
            whenever(mdvmApiClient.challenge()).thenAnswer { throw unreachable }

            val result = subject.register()

            assertEquals(
                ApiResult.Failure(
                    MdvmError(MdvmErrorType.NO_INTERNET, serverResponse = null, clientTraceId)
                ),
                result,
            )
            verify(logController).e(any(), eq(unreachable))
        }

    @Test
    fun `register returns UNKNOWN Failure when key creation throws`() = runTest {
        setupHappyPathForRegister()
        whenever(mdvmKeyManager.createAuthKeys(any())).thenAnswer { throw KeyStoreException("boom") }

        val result = subject.register()

        assertEquals(
            ApiResult.Failure(MdvmError(MdvmErrorType.UNKNOWN, serverResponse = null)),
            result,
        )
    }

    @Test
    fun `register returns UNKNOWN Failure when the response body cannot be parsed`() = runTest {
        setupHappyPathForRegister()
        whenever(signingApiClient.register(any(), any(), any()))
            .thenThrow(JsonSyntaxException("Expected BEGIN_OBJECT but was STRING"))

        val result = subject.register()

        assertEquals(
            ApiResult.Failure(MdvmError(MdvmErrorType.UNKNOWN, serverResponse = null)),
            result,
        )
    }

    @Test
    fun `register returns UNKNOWN Failure when an IllegalStateException is thrown`() = runTest {
        val illegalState = IllegalStateException("inconsistent state")
        setupHappyPathForRegister()
        whenever(mdvmKeyManager.createAuthKeys(any())).thenThrow(illegalState)

        val result = subject.register()

        assertEquals(
            ApiResult.Failure(MdvmError(MdvmErrorType.UNKNOWN, serverResponse = null)),
            result,
        )
        verify(logController).e(any(), eq(illegalState))
    }

    @Test
    fun `register propagates other programming errors instead of mapping them`() = runTest {
        setupHappyPathForRegister()
        whenever(mdvmKeyManager.createAuthKeys(any())).thenThrow(NullPointerException("bug"))

        try {
            subject.register()
            fail("Expected NullPointerException to propagate from register")
        } catch (expected: NullPointerException) {
            // pass
        }
    }

    @Test
    fun `register returns Failure when register request fails`() = runTest {
        setupHappyPathForRegister(registerResponse = ApiResult.Failure(mdvmErrorResponse))

        val result = subject.register()

        assertEquals(ApiResult.Failure(mdvmError), result)
    }

    @Test
    fun `register returns Success with MdvmRegistration from server`() = runTest {
        setupHappyPathForRegister()

        val result = subject.register()

        assertEquals(
            ApiResult.Success(MdvmRegistration(mdvmWiId, mdvmToken, wiMdvmAuthKeysAlias)),
            result
        )
    }

    @Test
    fun `register passes sha256 of challenge to createKeys`() = runTest {
        setupHappyPathForRegister()

        subject.register()

        verify(mdvmKeyManager).createAuthKeys(challengeNonce)
    }

    @Test
    fun `register does not call getExistingKeys`() = runTest {
        setupHappyPathForRegister()

        subject.register()

        verify(mdvmKeyManager, never()).getExistingAuthKeys(any())
    }

    @Test
    fun `register passes challenge to register endpoint as authChallenge`() = runTest {
        setupHappyPathForRegister()

        subject.register()

        val challengeCaptor = argumentCaptor<String>()
        verify(signingApiClient).register(
            request = any(),
            authChallenge = challengeCaptor.capture(),
            skipIntegrityChecks = any(),
        )
        assertEquals(mdvmAuthChallenge, challengeCaptor.firstValue)
    }

    @Test
    fun `register base64-encodes public key and attestation chain in request`() = runTest {
        setupHappyPathForRegister()

        subject.register()

        val requestCaptor = argumentCaptor<MdvmRegisterRequest>()
        verify(signingApiClient).register(
            request = requestCaptor.capture(),
            authChallenge = any(),
            skipIntegrityChecks = any(),
        )
        assertEquals(
            keyPair.public.encoded.toByteString().base64(),
            requestCaptor.firstValue.wi_mdvm_auth_pubk
        )
        assertEquals(
            listOf(fakeCertBytes.base64()),
            requestCaptor.firstValue.wi_android_key_attestation
        )
    }

    @Test
    fun `register passes skipIntegrityChecks value to register endpoint`() = runTest {
        setupHappyPathForRegister()

        subject.register()

        val skipIntegrityChecksCaptor = argumentCaptor<MdvmSkipIntegrityChecksHeaderValue>()
        verify(signingApiClient).register(
            request = any(),
            authChallenge = any(),
            skipIntegrityChecks = skipIntegrityChecksCaptor.capture(),
        )
        assertEquals(
            MdvmSkipIntegrityChecksHeaderValue.PLAY_INTEGRITY,
            skipIntegrityChecksCaptor.firstValue
        )
    }

    @Test
    fun `renewal returns Failure when challenge request fails`() = runTest {
        whenever(mdvmApiClient.challenge()).thenReturn(ApiResult.Failure(mdvmErrorResponse))

        val result = subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        assertEquals(ApiResult.Failure(mdvmError), result)
    }


    @Test
    fun `renewal Failure with expected message when key is missing from KeyStore`() = runTest {
        setupHappyPathForRenewal(existingKeys = null)
        val expectedResult = ApiResult.Failure(
            error = MdvmError(MdvmErrorType.MDVM_KEY_NOT_FOUND, serverResponse = null)
        )

        val result = subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias) as ApiResult.Failure

        assertEquals(expectedResult, result)
    }

    @Test
    fun `renewal returns Failure when renewal request fails`() = runTest {
        setupHappyPathForRenewal(renewalResponse = ApiResult.Failure(mdvmErrorResponse))

        val result = subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        assertEquals(ApiResult.Failure(mdvmError), result)
    }

    @Test
    fun `renewal returns Success with MdvmRegistration from server`() = runTest {
        setupHappyPathForRenewal()

        val result = subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        assertEquals(
            ApiResult.Success(MdvmRegistration(mdvmWiId, mdvmToken, wiMdvmAuthKeysAlias)),
            result
        )
    }

    @Test
    fun `renewal calls getExistingAuthKeys not createAuthKeys`() = runTest {
        setupHappyPathForRenewal()

        subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        verify(mdvmKeyManager).getExistingAuthKeys(wiMdvmAuthKeysAlias)
        verify(mdvmKeyManager, never()).createAuthKeys(any())
    }

    @Test
    fun `renewal passes sha256 of challenge to createReattestKeys`() = runTest {
        setupHappyPathForRenewal()

        subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        verify(mdvmKeyManager).createReattestKeys(challengeNonce)
    }

    @Test
    fun `renewal signs requests with both auth and reattest private keys`() = runTest {
        setupHappyPathForRenewal()

        subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        verify(mdvmApiClient).doubleSigningApi(
            wiMdvmAuthPrvk = keyPair.private,
            wiMdvmReattestPrvk = reattestKeyPair.private,
        )
    }

    @Test
    fun `renewal includes reattest key's attestation chain in request`() = runTest {
        setupHappyPathForRenewal()

        subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        val requestCaptor = argumentCaptor<MdvmRenewalRequest>()
        verify(doubleSigningApiClient).renewal(
            request = requestCaptor.capture(),
            mdvmWiId = any(),
            authChallenge = any(),
            skipIntegrityChecks = any(),
        )
        assertEquals(
            listOf(reattestFakeCertBytes.base64()),
            requestCaptor.firstValue.wi_android_key_attestation
        )
    }

    @Test
    fun `renewal deletes reattest keys after successful renewal`() = runTest {
        setupHappyPathForRenewal()

        subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        verify(mdvmKeyManager).deleteReattestKeys()
    }

    @Test
    fun `renewal deletes reattest keys when renewal request fails`() = runTest {
        setupHappyPathForRenewal(renewalResponse = ApiResult.Failure(mdvmErrorResponse))

        subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        verify(mdvmKeyManager).deleteReattestKeys()
    }

    @Test
    fun `renewal deletes reattest keys and returns NO_INTERNET when renewal call throws`() = runTest {
        setupHappyPathForRenewal()
        whenever(doubleSigningApiClient.renewal(any(), any(), any(), any()))
            .thenAnswer { throw unreachable }

        val result = subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        assertEquals(
            ApiResult.Failure(
                MdvmError(MdvmErrorType.NO_INTERNET, serverResponse = null, clientTraceId)
            ),
            result,
        )
        verify(mdvmKeyManager).deleteReattestKeys()
    }

    @Test
    fun `renewal returns NO_INTERNET Failure when challenge request throws`() = runTest {
        whenever(mdvmApiClient.challenge()).thenAnswer { throw unreachable }

        val result = subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        assertEquals(MdvmErrorType.NO_INTERNET, (result as ApiResult.Failure).error.type)
        verify(mdvmKeyManager, never()).createReattestKeys(any())
    }

    @Test
    fun `renewal does not create reattest keys when auth keys are missing`() = runTest {
        setupHappyPathForRenewal(existingKeys = null)

        subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        verify(mdvmKeyManager, never()).createReattestKeys(any())
        verify(mdvmKeyManager, never()).deleteReattestKeys()
    }

    @Test
    fun `renewal passes skipIntegrityChecks value to renewal endpoint`() = runTest {
        setupHappyPathForRenewal()

        subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        val skipIntegrityChecksCaptor = argumentCaptor<MdvmSkipIntegrityChecksHeaderValue>()
        verify(doubleSigningApiClient).renewal(
            request = any(),
            mdvmWiId = any(),
            authChallenge = any(),
            skipIntegrityChecks = skipIntegrityChecksCaptor.capture(),
        )
        assertEquals(
            MdvmSkipIntegrityChecksHeaderValue.PLAY_INTEGRITY,
            skipIntegrityChecksCaptor.firstValue
        )
    }

    @Test
    fun `renewal passes mdvmWiId to renewal endpoint`() = runTest {
        setupHappyPathForRenewal()

        subject.renewal(mdvmWiId, wiMdvmAuthKeysAlias)

        val wiIdCaptor = argumentCaptor<String>()
        verify(doubleSigningApiClient).renewal(
            request = any(),
            mdvmWiId = wiIdCaptor.capture(),
            authChallenge = any(),
            skipIntegrityChecks = any(),
        )
        assertEquals(mdvmWiId, wiIdCaptor.firstValue)
    }

    private suspend fun setupHappyPathForRegister(
        registerResponse: MdvmApiResult<MdvmRegisterResponse> = ApiResult.Success(
            MdvmRegisterResponse(mdvmWiId, mdvmToken)
        )
    ) {
        whenever(mdvmApiClient.challenge()).thenReturn(
            ApiResult.Success(MdvmChallengeResponse(mdvmAuthChallenge))
        )
        whenever(mdvmKeyManager.createAuthKeys(challengeNonce)).thenReturn(
            MdvmKeyInfo(wiMdvmAuthKeysAlias, keyPair, listOf(fakeCertificate))
        )
        whenever(mdvmApiClient.signingApi(keyPair.private)).thenReturn(signingApiClient)
        whenever(signingApiClient.register(any(), any(), any())).thenReturn(registerResponse)
    }

    private suspend fun setupHappyPathForRenewal(
        existingKeys: KeyPair? = keyPair,
        renewalResponse: MdvmApiResult<MdvmRenewalResponse> = ApiResult.Success(
            MdvmRenewalResponse(mdvmToken)
        ),
    ) {
        whenever(mdvmApiClient.challenge()).thenReturn(
            ApiResult.Success(MdvmChallengeResponse(mdvmAuthChallenge))
        )
        whenever(mdvmKeyManager.getExistingAuthKeys(wiMdvmAuthKeysAlias)).thenReturn(existingKeys)
        if (existingKeys != null) {
            whenever(mdvmKeyManager.createReattestKeys(challengeNonce)).thenReturn(
                MdvmKeyInfo(
                    wiMdvmReattestKeysAlias,
                    reattestKeyPair,
                    listOf(reattestFakeCertificate),
                )
            )
            whenever(mdvmApiClient.doubleSigningApi(
                wiMdvmAuthPrvk = existingKeys.private,
                wiMdvmReattestPrvk = reattestKeyPair.private,
            )).thenReturn(doubleSigningApiClient)
            whenever(doubleSigningApiClient.renewal(any(), any(), any(), any())).thenReturn(renewalResponse)
        } else {
            // error case where key has disappeared from KeyStore
        }
    }
}
