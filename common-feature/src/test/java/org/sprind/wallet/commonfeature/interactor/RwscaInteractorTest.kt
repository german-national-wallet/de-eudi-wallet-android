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

package org.sprind.wallet.commonfeature.interactor

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController.StartPinSessionMode.InitialPinSession
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController.StartPinSessionMode.SubsequentPinSession
import org.sprind.wallet.authenticationlogic.crypto.PinKeyFactory
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.authenticationlogic.model.RwscaError.FromMdvm
import org.sprind.wallet.authenticationlogic.model.RwscaPinSession
import org.sprind.wallet.authenticationlogic.model.RwscaRegistration
import org.sprind.wallet.authenticationlogic.model.toMdvmError
import org.sprind.wallet.authenticationlogic.model.toRwscaError
import org.sprind.wallet.authenticationlogic.provider.RwscaStorageController
import org.sprind.wallet.businesslogic.model.UserPinImpl
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType
import java.security.KeyPair
import java.security.KeyPairGenerator

class RwscaInteractorTest {

    @Mock private lateinit var mdvmInteractor: MdvmInteractor
    @Mock private lateinit var rwscaController: RwscaController
    @Mock private lateinit var rwscaStorageController: RwscaStorageController
    @Mock private lateinit var pinKeyFactory: PinKeyFactory

    private lateinit var closeable: AutoCloseable

    private val testSalt: ByteString = "test-salt-bytes".encodeUtf8()
    private val testKeyPair: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private fun fakePin() = UserPinImpl("111111")

    private val mdvmErrorResponse = MdvmErrorResponse(
        code = "MDVM_ERROR",
        description = "fake description",
        timestamp = "2026-01-01T00:00:00Z",
        trace_id = "fake-trace-id",
    )
    private val mdvmError = mdvmErrorResponse.toMdvmError()
    private val rwscaErrorResponse = RwscaErrorResponse(
        code = "RWSCA_ERROR",
        description = "fake description",
        timestamp = "2026-01-01T00:00:00Z",
        trace_id = "fake-trace-id",
        tryCounter = 3,
        tryAllowedAfter = "2126-01-01T00:00:00Z",
    )
    private val rwscaError = rwscaErrorResponse.toRwscaError()

    private val fakeMdvmRegistration = MdvmRegistration(
        mdvm_wi_id = "fake mdvm_wi_id for test",
        mdvm_token = "fake mdvm_token for test",
        wi_mdvm_auth_keys_alias = "wi_mdvm_auth_keys",
    )
    private val fakeRwscaRegistration = RwscaRegistration(rwsca_account_id = "fake rwsca_account_id for test")
    private val fakeRwscaPinSession = RwscaPinSession(rwsca_pin_session_token_jwt = "fake-pin-session-jwt-for-test")

    private val subject by lazy {
        RwscaInteractorImpl(mdvmInteractor, rwscaController, rwscaStorageController, pinKeyFactory)
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
    fun `rwscaRegistration returns stored registration immediately without touching MDVM or the network`() = runTest {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(fakeRwscaRegistration)

        val result = subject.rwscaRegistration()

        assertEquals(ApiResult.Success(fakeRwscaRegistration), result)
        verify(mdvmInteractor, never()).mdvmRegistration()
        verify(rwscaController, never()).register(any())
    }

    @Test
    fun `rwscaRegistration returns Failure when MDVM returns Failure`() = runTest {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(null)
        whenever(mdvmInteractor.mdvmRegistration())
            .thenReturn(ApiResult.Failure(mdvmError))

        val result = subject.rwscaRegistration()

        assertEquals(
            ApiResult.Failure(FromMdvm(mdvmError)),
            result
        )
    }

    @Test
    fun `rwscaRegistration returns Failure when RWSCA register API call fails`() = runTest {
        setupForMdvmSuccess()
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(null)
        whenever(rwscaController.register(fakeMdvmRegistration))
            .thenReturn(ApiResult.Failure(rwscaError))

        val result = subject.rwscaRegistration()

        assertEquals(ApiResult.Failure(rwscaError), result)
    }

    @Test
    fun `rwscaRegistration returns Success with the registered RwscaRegistration`() = runTest {
        setupHappyPathForRegistration()

        val result = subject.rwscaRegistration()

        assertEquals(ApiResult.Success(fakeRwscaRegistration), result)
    }

    @Test
    fun `rwscaRegistration saves new registration to storage on success`() = runTest {
        setupHappyPathForRegistration()

        subject.rwscaRegistration()

        verify(rwscaStorageController).saveRwscaRegistration(fakeRwscaRegistration)
    }

    @Test
    fun `rwscaRegistration does not save to storage when MDVM fails`() = runTest {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(null)
        whenever(mdvmInteractor.mdvmRegistration())
            .thenReturn(ApiResult.Failure(mdvmError))

        subject.rwscaRegistration()

        verify(rwscaStorageController, never()).saveRwscaRegistration(any())
    }

    @Test
    fun `rwscaRegistration does not save to storage when RWSCA register fails`() = runTest {
        setupForMdvmSuccess()
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(null)
        whenever(rwscaController.register(fakeMdvmRegistration))
            .thenReturn(ApiResult.Failure(rwscaError))

        subject.rwscaRegistration()

        verify(rwscaStorageController, never()).saveRwscaRegistration(any())
    }

    @Test
    fun `rwscaRegistration does not call register when a stored RWSCA registration already exists`() = runTest {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(fakeRwscaRegistration)

        subject.rwscaRegistration()

        verify(rwscaController, never()).register(any())
    }

    @Test
    fun `rwscaPinSession uses InitialPinSession when pin has not been initialized`() = runTest {
        setupForPinSessionCreation(mode = InitialPinSession)

        subject.rwscaPinSession(fakePin())

        verify(rwscaController).startPinSession(any(), any(), any(), mode = eq(InitialPinSession))
    }

    @Test
    fun `rwscaPinSession uses SubsequentPinSession when pin has been initialized`() = runTest {
        setupForPinSessionCreation(mode = SubsequentPinSession)

        subject.rwscaPinSession(fakePin())

        verify(rwscaController).startPinSession(any(), any(), any(), mode = eq(SubsequentPinSession))
    }

    @Test
    fun `rwscaPinSession marks pin as initialized after successful InitialPinSession`() = runTest {
        setupForPinSessionCreation(mode = InitialPinSession)

        subject.rwscaPinSession(fakePin())

        verify(rwscaStorageController).savePinInitialized()
    }

    @Test
    fun `rwscaPinSession does not mark pin as initialized when InitialPinSession fails`() = runTest {
        setupForPinSessionCreation(mode = InitialPinSession, pinSessionResponse = ApiResult.Failure(rwscaError))

        subject.rwscaPinSession(fakePin())

        verify(rwscaStorageController, never()).savePinInitialized()
    }

    @Test
    fun `rwscaPinSession returns Failure when MDVM fails during pin session creation`() = runTest {
        whenever(rwscaStorageController.isPinInitialized()).thenReturn(false)
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(null)
        whenever(mdvmInteractor.mdvmRegistration())
            .thenReturn(ApiResult.Failure(mdvmError))

        val result = subject.rwscaPinSession(fakePin())

        assertEquals(
            ApiResult.Failure(FromMdvm(mdvmError)),
            result
        )
    }

    @Test
    fun `rwscaPinSession returns Failure when RWSCA register fails during pin session creation`() = runTest {
        setupForMdvmSuccess()
        whenever(rwscaStorageController.isPinInitialized()).thenReturn(false)
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(null)
        whenever(rwscaController.register(fakeMdvmRegistration))
            .thenReturn(ApiResult.Failure(rwscaError))

        val result = subject.rwscaPinSession(fakePin())

        assertEquals(ApiResult.Failure(rwscaError), result)
    }

    @Test
    fun `rwscaPinSession returns Failure when startPinSession fails`() = runTest {
        setupForPinSessionCreation(pinSessionResponse = ApiResult.Failure(rwscaError))

        val result = subject.rwscaPinSession(fakePin())

        assertEquals(ApiResult.Failure(rwscaError), result)
    }

    @Test
    fun `rwscaPinSession returns Success with the new pin session when the pin had not yet been initialized`() = runTest {
        setupForPinSessionCreation(mode = InitialPinSession)

        val result = subject.rwscaPinSession(fakePin())

        assertEquals(ApiResult.Success(fakeRwscaPinSession), result)
    }

    @Test
    fun `rwscaPinSession returns Success with the new pin session when the pin has already been initialized`() = runTest {
        setupForPinSessionCreation(mode = SubsequentPinSession)

        val result = subject.rwscaPinSession(fakePin())

        assertEquals(ApiResult.Success(fakeRwscaPinSession), result)
    }

    @Test
    fun `rwscaPinSession does not call RWSCA register when a stored RWSCA registration exists`() = runTest {
        setupForPinSessionCreation()

        subject.rwscaPinSession(fakePin())

        verify(rwscaController, never()).register(any())
    }

    @Test
    fun `rwscaPinSession passes the correct mdvmRegistration and rwscaRegistration to controller`() = runTest {
        setupForPinSessionCreation()

        subject.rwscaPinSession(fakePin())

        verify(rwscaController).startPinSession(
            eq(fakeMdvmRegistration),
            eq(fakeRwscaRegistration),
            any(),
            mode = eq(InitialPinSession),
        )
    }

    @Test
    fun `rwscaPinSession generates a new salt and saves it when no salt is stored`() = runTest {
        setupForPinSessionCreation(storedSalt = null)

        subject.rwscaPinSession(fakePin())

        verify(pinKeyFactory).generatePinSalt()
        verify(rwscaStorageController).savePinSalt(testSalt)
    }

    @Test
    fun `rwscaPinSession reuses stored salt without generating or saving a new one`() = runTest {
        setupForPinSessionCreation()
        // getPinSalt already returns testSalt from setupForPinSessionCreation

        subject.rwscaPinSession(fakePin())

        verify(pinKeyFactory, never()).generatePinSalt()
        verify(rwscaStorageController, never()).savePinSalt(any())
    }

    @Test
    fun `deleteAccount returns Failure with ACCOUNT_NOT_FOUND_LOCALLY when no local registration exists`() = runTest {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(null)

        val result = subject.deleteAccount() as ApiResult.Failure

        assertEquals(RwscaErrorType.ACCOUNT_NOT_FOUND_LOCALLY.code, result.error.code)
    }

    @Test
    fun `deleteAccount does not call MDVM or network when no local registration exists`() = runTest {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(null)

        subject.deleteAccount()

        verify(mdvmInteractor, never()).mdvmRegistration()
        verify(rwscaController, never()).deleteAccount(any(), any())
    }

    @Test
    fun `deleteAccount returns Failure when MDVM fails`() = runTest {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(fakeRwscaRegistration)
        whenever(mdvmInteractor.mdvmRegistration()).thenReturn(ApiResult.Failure(mdvmError))

        val result = subject.deleteAccount()

        assertEquals(
            ApiResult.Failure(FromMdvm(mdvmError)),
            result
        )
    }

    @Test
    fun `deleteAccount does not call rwscaController when MDVM fails`() = runTest {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(fakeRwscaRegistration)
        whenever(mdvmInteractor.mdvmRegistration()).thenReturn(ApiResult.Failure(mdvmError))

        subject.deleteAccount()

        verify(rwscaController, never()).deleteAccount(any(), any())
    }

    @Test
    fun `deleteAccount returns Failure when rwscaController deleteAccount fails`() = runTest {
        setupForMdvmSuccess()
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(fakeRwscaRegistration)
        whenever(rwscaController.deleteAccount(any(), any())).thenReturn(ApiResult.Failure(rwscaError))

        val result = subject.deleteAccount()

        assertEquals(ApiResult.Failure(rwscaError), result)
    }

    @Test
    fun `deleteAccount does not clear local RWSCA data when rwscaController deleteAccount fails`() = runTest {
        setupForMdvmSuccess()
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(fakeRwscaRegistration)
        whenever(rwscaController.deleteAccount(any(), any())).thenReturn(ApiResult.Failure(rwscaError))

        subject.deleteAccount()

        verify(rwscaStorageController, never()).clearRwscaRegistration()
        verify(rwscaStorageController, never()).clearPinSalt()
        verify(rwscaStorageController, never()).clearPinInitialized()
    }

    @Test
    fun `deleteAccount returns Success`() = runTest {
        setupHappyPathForDeleteAccount()

        val result = subject.deleteAccount()

        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `deleteAccount clears local RWSCA data on success`() = runTest {
        setupHappyPathForDeleteAccount()

        subject.deleteAccount()

        verify(rwscaStorageController).clearRwscaRegistration()
        verify(rwscaStorageController).clearPinSalt()
        verify(rwscaStorageController).clearPinInitialized()
    }

    @Test
    fun `deleteAccount passes the correct mdvmRegistration and rwscaRegistration to controller`() = runTest {
        setupHappyPathForDeleteAccount()

        subject.deleteAccount()

        verify(rwscaController).deleteAccount(fakeMdvmRegistration, fakeRwscaRegistration)
    }

    private suspend fun setupHappyPathForDeleteAccount() {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(fakeRwscaRegistration)
        setupForMdvmSuccess()
        whenever(rwscaController.deleteAccount(any(), any())).thenReturn(ApiResult.Success(Unit))
    }

    private suspend fun setupForMdvmSuccess() {
        whenever(mdvmInteractor.mdvmRegistration())
            .thenReturn(ApiResult.Success(fakeMdvmRegistration))
    }

    private suspend fun setupHappyPathForRegistration() {
        setupForMdvmSuccess()
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(null)
        whenever(rwscaController.register(fakeMdvmRegistration))
            .thenReturn(ApiResult.Success(fakeRwscaRegistration))
    }

    private suspend fun setupForPinSessionCreation(
        mode: RwscaController.StartPinSessionMode = InitialPinSession,
        pinSessionResponse: ApiResult<RwscaPinSession, RwscaError> = ApiResult.Success(fakeRwscaPinSession),
        storedSalt: ByteString? = testSalt,
    ) {
        whenever(rwscaStorageController.isPinInitialized()).thenReturn(mode == SubsequentPinSession)
        // Return existing RWSCA registration to avoid going through register()
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(fakeRwscaRegistration)
        setupForMdvmSuccess()
        whenever(rwscaStorageController.getPinSalt()).thenReturn(storedSalt)
        whenever(pinKeyFactory.generatePinSalt()).thenReturn(testSalt)
        whenever(pinKeyFactory.generatePinKeys(any(), any())).thenReturn(testKeyPair)
        whenever(rwscaController.startPinSession(any(), any(), any(), eq(mode))).thenAnswer { invocation ->
            runBlocking {
                invocation.getArgument<RwscaController.WithPinKeys>(2).execute { testKeyPair }
            }
            pinSessionResponse
        }
    }
}
