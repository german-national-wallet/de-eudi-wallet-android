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

package org.sprind.wallet.authenticationlogic.controller.rwsca

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmKeyManager
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController.StartPinSessionMode.InitialPinSession
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController.StartPinSessionMode.SubsequentPinSession
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaCreatedKeys
import org.sprind.wallet.authenticationlogic.model.RwscaPinSession
import org.sprind.wallet.authenticationlogic.model.RwscaRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaWrappedKey
import org.sprind.wallet.authenticationlogic.model.toRwscaError
import org.sprind.wallet.authenticationlogic.testing.generateEcKeyPairForTests
import org.sprind.wallet.authenticationlogic.testing.makeFakeCertificate
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType
import org.sprind.wallet.networklogic.rwsca.api.DoubleSigningRwscaApiClient
import org.sprind.wallet.networklogic.rwsca.api.RwscaApiClient
import org.sprind.wallet.networklogic.rwsca.api.SigningRwscaApiClient
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.request.RwscaInitializePinAndStartPinSessionRequest
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaChallengeResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaCreateKeysResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaInitializePinAndStartPinSessionResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaRegisterResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaStartPinSessionResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaWiKeySpec
import java.security.KeyPair

class RwscaControllerTest {

    @Mock private lateinit var rwscaApiClient: RwscaApiClient
    @Mock private lateinit var mdvmKeyManager: MdvmKeyManager
    @Mock private lateinit var signingApiClient: SigningRwscaApiClient
    @Mock private lateinit var doubleSigningApiClient: DoubleSigningRwscaApiClient

    private lateinit var closeable: AutoCloseable

    private val mdvmKeyPair: KeyPair = generateEcKeyPairForTests()
    private val pinKeyPair: KeyPair = generateEcKeyPairForTests()
    private val fakeCertificate = makeFakeCertificate(mdvmKeyPair.public)

    private val rwscaAuthChallenge = "fake rwsca_auth_challenge for test"
    private val rwscaAccountId = "fake rwsca_account_id for test"
    private val pinSessionToken = "fake rwsca_pin_session_token for test"
    private val mdvmToken = "fake mdvm_token for test"
    private val mdvmWiId = "fake mdvm_wi_id for test"
    private val wiMdvmAuthKeysAlias = "wi_mdvm_auth_keys"
    private val fakePpCNonce = "fake pp_c_nonce for test"
    private val fakeEncodedPublicKey = "fake encoded public key"
    private val fakeWrappedPrivateKey = "fake wrapped private key"
    private val fakeWalletTrustEvidence = "fake wallet trust evidence"
    private val rwscaErrorResponse = RwscaErrorResponse(code = "fake error for test")
    private val failureApiResponse = ApiResult.Failure(rwscaErrorResponse)
    private val failureMappedResponse = ApiResult.Failure(rwscaErrorResponse.toRwscaError())

    private val fakeMdvmRegistration = MdvmRegistration(
        mdvm_wi_id = mdvmWiId,
        mdvm_token = mdvmToken,
        wi_mdvm_auth_keys_alias = wiMdvmAuthKeysAlias,
    )
    private val fakeRwscaRegistration = RwscaRegistration(rwsca_account_id = rwscaAccountId)

    private val fakeWithPinKeys = object : RwscaController.WithPinKeys {
        override suspend fun <T> execute(block: suspend (KeyPair) -> T): T = block(pinKeyPair)
    }

    private val subject: RwscaControllerImpl by lazy {
        RwscaControllerImpl(rwscaApiClient, mdvmKeyManager)
    }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `register returns Failure when challenge request fails`() = runTest {
        whenever(rwscaApiClient.challenge()).thenReturn(failureApiResponse)

        val result = subject.register(fakeMdvmRegistration)

        assertEquals(failureMappedResponse, result)
    }

    @Test
    fun `register returns Failure with MDVM_KEY_NOT_FOUND when key is missing from KeyStore`() = runTest {
        whenever(rwscaApiClient.challenge()).thenReturn(
            ApiResult.Success(RwscaChallengeResponse(rwscaAuthChallenge))
        )
        whenever(mdvmKeyManager.getExistingAuthKeys(wiMdvmAuthKeysAlias)).thenReturn(null)

        val result = subject.register(fakeMdvmRegistration) as ApiResult.Failure

        assertEquals(MdvmErrorType.MDVM_KEY_NOT_FOUND.code, result.error.code)
    }

    @Test
    fun `register returns Failure when register API call fails`() = runTest {
        setupHappyPathForRegister(registerResponse = failureApiResponse)

        val result = subject.register(fakeMdvmRegistration)

        assertEquals(failureMappedResponse, result)
    }

    @Test
    fun `register returns Success with rwsca_account_id from server`() = runTest {
        setupHappyPathForRegister()

        val result = subject.register(fakeMdvmRegistration)

        assertEquals(ApiResult.Success(fakeRwscaRegistration), result)
    }

    @Test
    fun `register passes rwsca_auth_challenge and mdvm_token to register endpoint`() = runTest {
        setupHappyPathForRegister()

        subject.register(fakeMdvmRegistration)

        val authChallengeCaptor = argumentCaptor<String>()
        val mdvmTokenCaptor = argumentCaptor<String>()
        verify(signingApiClient).register(
            authChallenge = authChallengeCaptor.capture(),
            mdvmToken = mdvmTokenCaptor.capture(),
        )
        // assertEquals(list, list) shows all failures; 2x assertEquals would stop at the first.
        assertEquals(
            listOf(rwscaAuthChallenge, mdvmToken),
            listOf(authChallengeCaptor.firstValue, mdvmTokenCaptor.firstValue),
        )
    }

    @Test
    fun `register uses mdvmKeyPair private key for HTTP message signing`() = runTest {
        setupHappyPathForRegister()

        subject.register(fakeMdvmRegistration)

        verify(rwscaApiClient).signingApi(mdvmKeyPair.private)
    }

    @Test
    fun `createKeys returns Success with rwscd_wi_pubk mapped to encodedPublicKey and rwsca_wi_wrapped_prvk mapped to wrappedPrivateKey`() = runTest {
        setupHappyPathForCreateKeys()

        val result = subject.createKeys(fakeMdvmRegistration, fakeRwscaRegistration, numberOfKeys = 1, ppCNonce = fakePpCNonce)

        assertEquals(
            ApiResult.Success(
                RwscaCreatedKeys(
                    wrappedKeys = listOf(
                        RwscaWrappedKey(
                            encodedPublicKey = fakeEncodedPublicKey,
                            wrappedPrivateKey = fakeWrappedPrivateKey,
                        )
                    ),
                    walletTrustEvidence = fakeWalletTrustEvidence,
                )
            ),
            result,
        )
    }

    @Test
    fun `startPinSession with InitialPinSession returns Failure when challenge request fails`() = runTest {
        whenever(rwscaApiClient.challenge()).thenReturn(failureApiResponse)

        val result = subject.startPinSession(
            fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, InitialPinSession
        )

        assertEquals(failureMappedResponse, result)
    }

    @Test
    fun `startPinSession with InitialPinSession returns Failure with MDVM_KEY_NOT_FOUND when key is missing from KeyStore`() = runTest {
        whenever(rwscaApiClient.challenge()).thenReturn(
            ApiResult.Success(RwscaChallengeResponse(rwscaAuthChallenge))
        )
        whenever(mdvmKeyManager.getExistingAuthKeys(wiMdvmAuthKeysAlias)).thenReturn(null)

        val result = subject.startPinSession(
            fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, InitialPinSession
        ) as ApiResult.Failure

        assertEquals(MdvmErrorType.MDVM_KEY_NOT_FOUND.code, result.error.code)
    }

    @Test
    fun `startPinSession with InitialPinSession returns Failure when API call fails`() = runTest {
        setupHappyPathForInitialPinSession(
            apiResponse = failureApiResponse
        )

        val result = subject.startPinSession(
            fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, InitialPinSession
        )

        assertEquals(failureMappedResponse, result)
    }

    @Test
    fun `startPinSession with InitialPinSession returns Success with correct RwscaPinSession`() = runTest {
        setupHappyPathForInitialPinSession()
        val expected = ApiResult.Success(RwscaPinSession(rwsca_pin_session_token_jwt = pinSessionToken))

        val result = subject.startPinSession(
            fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, InitialPinSession
        )

        assertEquals(expected, result)
    }

    @Test
    fun `startPinSession with InitialPinSession sends base64-encoded pin public key in request`() = runTest {
        setupHappyPathForInitialPinSession()

        subject.startPinSession(fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, InitialPinSession)

        val captor = argumentCaptor<RwscaInitializePinAndStartPinSessionRequest>()
        verify(doubleSigningApiClient).initializePinAndStartPinSession(
            request = captor.capture(),
            authChallenge = any(),
            mdvmToken = any(),
            rwscaAccountId = any(),
        )
        assertEquals(
            pinKeyPair.public.encoded.toByteString().base64(),
            captor.firstValue.wi_rwsca_pin_pubk
        )
    }

    @Test
    fun `startPinSession with InitialPinSession passes rwsca_auth_challenge mdvm_token and rwsca_account_id to endpoint`() = runTest {
        setupHappyPathForInitialPinSession()

        subject.startPinSession(fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, InitialPinSession)

        val authChallengeCaptor = argumentCaptor<String>()
        val mdvmTokenCaptor = argumentCaptor<String>()
        val rwscaAccountIdCaptor = argumentCaptor<String>()
        verify(doubleSigningApiClient).initializePinAndStartPinSession(
            request = any(),
            authChallenge = authChallengeCaptor.capture(),
            mdvmToken = mdvmTokenCaptor.capture(),
            rwscaAccountId = rwscaAccountIdCaptor.capture(),
        )
        // assertEquals(list, list) shows all failures; 3x assertEquals would stop at the first.
        assertEquals(
            listOf(rwscaAuthChallenge, mdvmToken, rwscaAccountId),
            listOf(authChallengeCaptor.firstValue, mdvmTokenCaptor.firstValue, rwscaAccountIdCaptor.firstValue),
        )
    }

    @Test
    fun `startPinSession with InitialPinSession uses mdvmKeyPair and pinKeyPair for double signing`() = runTest {
        setupHappyPathForInitialPinSession()

        subject.startPinSession(fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, InitialPinSession)

        verify(rwscaApiClient).doubleSigningApi(
            mdvmAuthPrvk = mdvmKeyPair.private,
            rwscaPinPrvk = pinKeyPair.private,
        )
    }

    @Test
    fun `startPinSession with InitialPinSession calls initializePinAndStartPinSession but not startPinSession endpoint`() = runTest {
        setupHappyPathForInitialPinSession()

        subject.startPinSession(fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, InitialPinSession)

        verify(doubleSigningApiClient).initializePinAndStartPinSession(any(), any(), any(), any())
        verify(doubleSigningApiClient, never()).startPinSession(any(), any(), any())
    }

    @Test
    fun `startPinSession with SubsequentPinSession calls startPinSession but not initializePinAndStartPinSession endpoint`() = runTest {
        setupHappyPathForSubsequentPinSession()

        subject.startPinSession(fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, SubsequentPinSession)

        verify(doubleSigningApiClient).startPinSession(any(), any(), any())
        verify(doubleSigningApiClient, never()).initializePinAndStartPinSession(any(), any(), any(), any())
    }

    @Test
    fun `startPinSession with SubsequentPinSession returns Failure when API call fails`() = runTest {
        setupHappyPathForSubsequentPinSession(apiResponse = failureApiResponse)

        val result = subject.startPinSession(
            fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, SubsequentPinSession
        )

        assertEquals(failureMappedResponse, result)
    }

    @Test
    fun `startPinSession with SubsequentPinSession returns Success with correct RwscaPinSession`() = runTest {
        setupHappyPathForSubsequentPinSession()
        val expected = ApiResult.Success(RwscaPinSession(rwsca_pin_session_token_jwt = pinSessionToken))

        val result = subject.startPinSession(
            fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, SubsequentPinSession
        )

        assertEquals(expected, result)
    }

    @Test
    fun `startPinSession with SubsequentPinSession passes rwsca_auth_challenge mdvm_token and rwsca_account_id to endpoint`() = runTest {
        setupHappyPathForSubsequentPinSession()

        subject.startPinSession(fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, SubsequentPinSession)

        val authChallengeCaptor = argumentCaptor<String>()
        val mdvmTokenCaptor = argumentCaptor<String>()
        val rwscaAccountIdCaptor = argumentCaptor<String>()
        verify(doubleSigningApiClient).startPinSession(
            authChallenge = authChallengeCaptor.capture(),
            mdvmToken = mdvmTokenCaptor.capture(),
            rwscaAccountId = rwscaAccountIdCaptor.capture(),
        )
        // assertEquals(list, list) shows all failures; 3x assertEquals would stop at the first.
        assertEquals(
            listOf(rwscaAuthChallenge, mdvmToken, rwscaAccountId),
            listOf(authChallengeCaptor.firstValue, mdvmTokenCaptor.firstValue, rwscaAccountIdCaptor.firstValue),
        )
    }

    @Test
    fun `startPinSession with SubsequentPinSession uses mdvmKeyPair and pinKeyPair for double signing`() = runTest {
        setupHappyPathForSubsequentPinSession()

        subject.startPinSession(fakeMdvmRegistration, fakeRwscaRegistration, fakeWithPinKeys, SubsequentPinSession)

        verify(rwscaApiClient).doubleSigningApi(
            mdvmAuthPrvk = mdvmKeyPair.private,
            rwscaPinPrvk = pinKeyPair.private,
        )
    }

    @Test
    fun `deleteAccount returns Failure when challenge request fails`() = runTest {
        whenever(rwscaApiClient.challenge()).thenReturn(failureApiResponse)

        val result = subject.deleteAccount(fakeMdvmRegistration, fakeRwscaRegistration)

        assertEquals(failureMappedResponse, result)
    }

    @Test
    fun `deleteAccount returns Failure with MDVM_KEY_NOT_FOUND when key is missing from KeyStore`() = runTest {
        whenever(rwscaApiClient.challenge()).thenReturn(
            ApiResult.Success(RwscaChallengeResponse(rwscaAuthChallenge))
        )
        whenever(mdvmKeyManager.getExistingAuthKeys(wiMdvmAuthKeysAlias)).thenReturn(null)

        val result = subject.deleteAccount(fakeMdvmRegistration, fakeRwscaRegistration) as ApiResult.Failure

        assertEquals(MdvmErrorType.MDVM_KEY_NOT_FOUND.code, result.error.code)
    }

    @Test
    fun `deleteAccount returns Failure when deleteAccount API call fails`() = runTest {
        setupHappyPathForDeleteAccount(apiResponse = failureApiResponse)

        val result = subject.deleteAccount(fakeMdvmRegistration, fakeRwscaRegistration)

        assertEquals(failureMappedResponse, result)
    }

    @Test
    fun `deleteAccount returns Success`() = runTest {
        setupHappyPathForDeleteAccount()

        val result = subject.deleteAccount(fakeMdvmRegistration, fakeRwscaRegistration)

        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `deleteAccount passes authChallenge mdvmToken and rwscaAccountId to endpoint`() = runTest {
        setupHappyPathForDeleteAccount()

        subject.deleteAccount(fakeMdvmRegistration, fakeRwscaRegistration)

        val authChallengeCaptor = argumentCaptor<String>()
        val mdvmTokenCaptor = argumentCaptor<String>()
        val rwscaAccountIdCaptor = argumentCaptor<String>()
        verify(signingApiClient).deleteAccount(
            authChallenge = authChallengeCaptor.capture(),
            mdvmToken = mdvmTokenCaptor.capture(),
            rwscaAccountId = rwscaAccountIdCaptor.capture(),
        )
        // assertEquals(list, list) shows all failures; 3x assertEquals would stop at the first.
        assertEquals(
            listOf(rwscaAuthChallenge, mdvmToken, rwscaAccountId),
            listOf(authChallengeCaptor.firstValue, mdvmTokenCaptor.firstValue, rwscaAccountIdCaptor.firstValue),
        )
    }

    @Test
    fun `deleteAccount uses mdvmKeyPair private key for HTTP message signing`() = runTest {
        setupHappyPathForDeleteAccount()

        subject.deleteAccount(fakeMdvmRegistration, fakeRwscaRegistration)

        verify(rwscaApiClient).signingApi(mdvmKeyPair.private)
    }

    private suspend fun setupHappyPathForDeleteAccount(
        apiResponse: ApiResult<Unit, RwscaErrorResponse> = ApiResult.Success(Unit)
    ) {
        setupHappyPathForChallengeAndKeys()
        whenever(rwscaApiClient.signingApi(mdvmKeyPair.private)).thenReturn(signingApiClient)
        whenever(signingApiClient.deleteAccount(any(), any(), any())).thenReturn(apiResponse)
    }

    private suspend fun setupHappyPathForChallengeAndKeys() {
        whenever(rwscaApiClient.challenge()).thenReturn(
            ApiResult.Success(RwscaChallengeResponse(rwscaAuthChallenge))
        )
        whenever(mdvmKeyManager.getExistingAuthKeys(wiMdvmAuthKeysAlias))
            .thenReturn(mdvmKeyPair)
    }

    private suspend fun setupHappyPathForRegister(
        registerResponse: ApiResult<RwscaRegisterResponse, RwscaErrorResponse> = ApiResult.Success(
            RwscaRegisterResponse(rwscaAccountId)
        )
    ) {
        setupHappyPathForChallengeAndKeys()
        whenever(rwscaApiClient.signingApi(mdvmKeyPair.private)).thenReturn(signingApiClient)
        whenever(signingApiClient.register(any(), any())).thenReturn(registerResponse)
    }

    private suspend fun setupHappyPathForCreateKeys(
        apiResponse: ApiResult<RwscaCreateKeysResponse, RwscaErrorResponse> = ApiResult.Success(
            RwscaCreateKeysResponse(
                rwsca_wi_keys = listOf(
                    RwscaWiKeySpec(
                        rwscd_wi_pubk = fakeEncodedPublicKey,
                        rwsca_wi_wrapped_prvk = fakeWrappedPrivateKey,
                    )
                ),
                rwsca_wte = fakeWalletTrustEvidence,
            )
        )
    ) {
        setupHappyPathForChallengeAndKeys()
        whenever(rwscaApiClient.signingApi(mdvmKeyPair.private)).thenReturn(signingApiClient)
        whenever(signingApiClient.createKeys(any(), any(), any(), any())).thenReturn(apiResponse)
    }

    private suspend fun setupHappyPathForInitialPinSession(
        apiResponse: ApiResult<RwscaInitializePinAndStartPinSessionResponse, RwscaErrorResponse> =
            ApiResult.Success(RwscaInitializePinAndStartPinSessionResponse(pinSessionToken))
    ) {
        setupHappyPathForChallengeAndKeys()
        whenever(rwscaApiClient.doubleSigningApi(mdvmKeyPair.private, pinKeyPair.private))
            .thenReturn(doubleSigningApiClient)
        whenever(doubleSigningApiClient.initializePinAndStartPinSession(any(), any(), any(), any()))
            .thenReturn(apiResponse)
    }

    private suspend fun setupHappyPathForSubsequentPinSession(
        apiResponse: ApiResult<RwscaStartPinSessionResponse, RwscaErrorResponse> =
            ApiResult.Success(RwscaStartPinSessionResponse(pinSessionToken))
    ) {
        setupHappyPathForChallengeAndKeys()
        whenever(rwscaApiClient.doubleSigningApi(mdvmKeyPair.private, pinKeyPair.private))
            .thenReturn(doubleSigningApiClient)
        whenever(doubleSigningApiClient.startPinSession(any(), any(), any()))
            .thenReturn(apiResponse)
    }
}
