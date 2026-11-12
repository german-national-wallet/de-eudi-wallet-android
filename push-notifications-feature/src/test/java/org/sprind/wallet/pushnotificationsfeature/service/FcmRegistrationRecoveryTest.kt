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

package org.sprind.wallet.pushnotificationsfeature.service

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.controller.storage.MdvmRegistrationStorageController
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.businesslogic.controller.revocation.WalletRevocationStore
import org.sprind.wallet.businesslogic.controller.storage.EncryptedPrefsUnavailableException
import org.sprind.wallet.businesslogic.controller.storage.PlainPrefsController
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.pushnotifications.model.PushNotificationsErrorResponse
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor
import org.sprind.wallet.pushnotificationsfeature.service.WalletFirebaseMessagingService.Companion.FCM_TOKEN_REGISTERED_KEY

class FcmRegistrationRecoveryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var plainPrefsController: PlainPrefsController

    @Mock
    private lateinit var walletRevocationStore: WalletRevocationStore

    @Mock
    private lateinit var mdvmRegistrationStorageController: MdvmRegistrationStorageController

    @Mock
    private lateinit var pushNotificationsInteractor: PushNotificationsInteractor

    @Mock
    private lateinit var logController: LogController

    private lateinit var closeable: AutoCloseable

    private var tokenProvider: suspend () -> String = { TOKEN }

    private val subject by lazy {
        FcmRegistrationRecovery(
            plainPrefsController = plainPrefsController,
            walletRevocationStore = walletRevocationStore,
            mdvmRegistrationStorageController = mdvmRegistrationStorageController,
            pushNotificationsInteractor = pushNotificationsInteractor,
            tokenProvider = { tokenProvider() },
            logController = logController,
        )
    }

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(walletRevocationStore.isRevoked()).thenReturn(false)
        whenever(plainPrefsController.getBool(FCM_TOKEN_REGISTERED_KEY, false)).thenReturn(false)
        whenever(mdvmRegistrationStorageController.getMdvmRegistration()).thenReturn(
            MdvmRegistration(mdvm_wi_id = "wi", mdvm_token = "token", wi_mdvm_auth_keys_alias = "alias")
        )
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `registers the current token and sets the flag when not registered`() =
        coroutineRule.runTest {
            whenever(pushNotificationsInteractor.registerFcmToken(TOKEN))
                .thenReturn(ApiResult.Success(Unit))

            subject.registerIfNeeded()

            verify(pushNotificationsInteractor).registerFcmToken(TOKEN)
            verify(plainPrefsController).setBool(FCM_TOKEN_REGISTERED_KEY, true)
        }

    @Test
    fun `an unset flag counts as not registered`() = coroutineRule.runTest {
        // The mock's default for getBool is false, i.e. the SharedPreferences default we pass.
        whenever(pushNotificationsInteractor.registerFcmToken(TOKEN))
            .thenReturn(ApiResult.Success(Unit))

        subject.registerIfNeeded()

        verify(pushNotificationsInteractor).registerFcmToken(TOKEN)
    }

    @Test
    fun `does nothing when already registered`() = coroutineRule.runTest {
        whenever(plainPrefsController.getBool(FCM_TOKEN_REGISTERED_KEY, false)).thenReturn(true)

        subject.registerIfNeeded()

        verify(pushNotificationsInteractor, never()).registerFcmToken(any())
        verify(plainPrefsController, never()).setBool(any(), any())
    }

    @Test
    fun `does nothing when the wallet is revoked`() = coroutineRule.runTest {
        whenever(walletRevocationStore.isRevoked()).thenReturn(true)

        subject.registerIfNeeded()

        verify(pushNotificationsInteractor, never()).registerFcmToken(any())
    }

    @Test
    fun `does nothing before the wallet is registered with the MDVM`() = coroutineRule.runTest {
        // The push backend call is authenticated with the MDVM registration and can only fail
        // without it; a not-yet-onboarded install must not fetch a token on every resume.
        whenever(mdvmRegistrationStorageController.getMdvmRegistration()).thenReturn(null)
        var tokenFetched = false
        tokenProvider = { tokenFetched = true; TOKEN }

        subject.registerIfNeeded()

        assertFalse(tokenFetched)
        verify(pushNotificationsInteractor, never()).registerFcmToken(any())
        verify(plainPrefsController, never()).setBool(any(), any())
    }

    @Test
    fun `postpones the attempt when the MDVM registration cannot be read`() = coroutineRule.runTest {
        doAnswer { throw EncryptedPrefsUnavailableException.Locked(Exception("not authenticated")) }
            .whenever(mdvmRegistrationStorageController).getMdvmRegistration()

        subject.registerIfNeeded()

        verify(pushNotificationsInteractor, never()).registerFcmToken(any())
        verify(logController).e(any(), any<Throwable>())
    }

    @Test
    fun `propagates failures unrelated to the encrypted preferences`() = coroutineRule.runTest {
        whenever(mdvmRegistrationStorageController.getMdvmRegistration())
            .thenThrow(IllegalStateException("bug"))

        val failure = runCatching { subject.registerIfNeeded() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun `leaves the flag unset when registration fails`() = coroutineRule.runTest {
        whenever(pushNotificationsInteractor.registerFcmToken(TOKEN))
            .thenReturn(ApiResult.Failure(PushNotificationsErrorResponse(errorCode = "E")))

        subject.registerIfNeeded()

        verify(plainPrefsController, never()).setBool(any(), any())
    }

    @Test
    fun `leaves the flag unset and does not register when the token cannot be fetched`() =
        coroutineRule.runTest {
            tokenProvider = { throw IOException("no Play Services") }

            subject.registerIfNeeded()

            verify(pushNotificationsInteractor, never()).registerFcmToken(any())
            verify(plainPrefsController, never()).setBool(any(), any())
        }

    companion object {
        private const val TOKEN = "fcm-token"
    }
}
