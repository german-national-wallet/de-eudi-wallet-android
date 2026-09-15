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

package org.sprind.wallet.pushnotificationsfeature.interactor

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.toMdvmError
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContext
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContextProvider
import org.sprind.wallet.commonfeature.interactor.MdvmInteractor
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verifyNoInteractions
import org.sprind.wallet.businesslogic.controller.revocation.WalletRevocationStore
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse
import org.sprind.wallet.networklogic.pushnotifications.api.PushNotificationsApiClient
import org.sprind.wallet.networklogic.pushnotifications.api.SigningPushNotificationsApiClient
import org.sprind.wallet.networklogic.pushnotifications.model.PushNotificationsChallengeResponse
import org.sprind.wallet.networklogic.pushnotifications.model.PushNotificationsErrorResponse
import java.security.PrivateKey
import java.io.IOException

class PushNotificationsInteractorImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var pushNotificationsApiClient: PushNotificationsApiClient

    @Mock
    private lateinit var signingPushNotificationsApiClient: SigningPushNotificationsApiClient

    @Mock
    private lateinit var mdvmAuthContextProvider: MdvmAuthContextProvider

    @Mock
    private lateinit var mdvmInteractor: MdvmInteractor

    @Mock
    private lateinit var mdvmAuthPrvk: PrivateKey

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var walletRevocationStore: WalletRevocationStore

    private lateinit var closeable: AutoCloseable

    private val subject by lazy {
        PushNotificationsInteractorImpl(
            pushNotificationsApiClient = pushNotificationsApiClient,
            mdvmAuthContextProvider = mdvmAuthContextProvider,
            mdvmInteractor = mdvmInteractor,
            walletRevocationStore = walletRevocationStore,
            logController = logController,
        )
    }

    private val authContext by lazy {
        MdvmAuthContext(
            mdvmToken = "test-mdvm-token",
            mdvmAuthPrvk = mdvmAuthPrvk,
        )
    }

    private val challengeResponse = PushNotificationsChallengeResponse(
        authChallenge = "test-challenge",
    )

    private val mdvmError = MdvmErrorResponse(code = "test-mdvm-error").toMdvmError()

    private val pnsError = PushNotificationsErrorResponse(errorCode = "test-pns-error")

    private val mdvmRegistration = MdvmRegistration(
        mdvm_wi_id = "test-wi-id",
        mdvm_token = "test-mdvm-token",
        wi_mdvm_auth_keys_alias = "test-key-alias",
    )

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(pushNotificationsApiClient.signingApi(any())).thenReturn(signingPushNotificationsApiClient)
        whenever(walletRevocationStore.isRevoked()).thenReturn(false)
    }

    @After
    fun after() {
        closeable.close()
    }

    // region registerFcmToken

    @Test
    fun `registerFcmToken returns Success and registers when all steps succeed`() = coroutineRule.runTest {
        whenever(mdvmAuthContextProvider.getMdvmAuthContext()).thenReturn(ApiResult.Success(authContext))
        whenever(pushNotificationsApiClient.getChallenge()).thenReturn(ApiResult.Success(challengeResponse))
        whenever(
            signingPushNotificationsApiClient.register(
                mppRegistrationToken = "fcm-token",
                authChallenge = "test-challenge",
                mdvmToken = "test-mdvm-token",
            )
        ).thenReturn(ApiResult.Success(Unit))

        val actual = subject.registerFcmToken("fcm-token")

        assertEquals(ApiResult.Success(Unit), actual)
        verify(signingPushNotificationsApiClient).register(
            mppRegistrationToken = "fcm-token",
            authChallenge = "test-challenge",
            mdvmToken = "test-mdvm-token",
        )
    }

    @Test
    fun `registerFcmToken returns Failure with mapped error code when MDVM auth context fails`() = coroutineRule.runTest {
        whenever(mdvmAuthContextProvider.getMdvmAuthContext()).thenReturn(ApiResult.Failure(mdvmError))

        val actual = subject.registerFcmToken("fcm-token")

        assertEquals(
            ApiResult.Failure(PushNotificationsErrorResponse(errorCode = "test-mdvm-error")),
            actual,
        )
        verify(pushNotificationsApiClient, never()).getChallenge()
        verify(pushNotificationsApiClient, never()).signingApi(any())
        verify(signingPushNotificationsApiClient, never()).register(any(), any(), any())
    }

    @Test
    fun `registerFcmToken returns Failure when getChallenge fails`() = coroutineRule.runTest {
        whenever(mdvmAuthContextProvider.getMdvmAuthContext()).thenReturn(ApiResult.Success(authContext))
        whenever(pushNotificationsApiClient.getChallenge()).thenReturn(ApiResult.Failure(pnsError))

        val actual = subject.registerFcmToken("fcm-token")

        assertEquals(ApiResult.Failure(pnsError), actual)
        verify(pushNotificationsApiClient, never()).signingApi(any())
        verify(signingPushNotificationsApiClient, never()).register(any(), any(), any())
    }

    @Test
    fun `registerFcmToken returns Failure when register fails`() = coroutineRule.runTest {
        whenever(mdvmAuthContextProvider.getMdvmAuthContext()).thenReturn(ApiResult.Success(authContext))
        whenever(pushNotificationsApiClient.getChallenge()).thenReturn(ApiResult.Success(challengeResponse))
        whenever(
            signingPushNotificationsApiClient.register(any(), any(), any())
        ).thenReturn(ApiResult.Failure(pnsError))

        val actual = subject.registerFcmToken("fcm-token")

        assertEquals(ApiResult.Failure(pnsError), actual)
    }

    // endregion

    // region deleteRegistration

    @Test
    fun `deleteRegistration returns Success and deletes when all steps succeed`() = coroutineRule.runTest {
        whenever(mdvmAuthContextProvider.getMdvmAuthContext()).thenReturn(ApiResult.Success(authContext))
        whenever(pushNotificationsApiClient.getChallenge()).thenReturn(ApiResult.Success(challengeResponse))
        whenever(
            signingPushNotificationsApiClient.delete(
                authChallenge = "test-challenge",
                mdvmToken = "test-mdvm-token",
            )
        ).thenReturn(ApiResult.Success(Unit))

        val actual = subject.deleteRegistration()

        assertEquals(ApiResult.Success(Unit), actual)
        verify(signingPushNotificationsApiClient).delete(
            authChallenge = "test-challenge",
            mdvmToken = "test-mdvm-token",
        )
    }

    @Test
    fun `deleteRegistration returns Failure with mapped error code when MDVM auth context fails`() = coroutineRule.runTest {
        whenever(mdvmAuthContextProvider.getMdvmAuthContext()).thenReturn(ApiResult.Failure(mdvmError))

        val actual = subject.deleteRegistration()

        assertEquals(
            ApiResult.Failure(PushNotificationsErrorResponse(errorCode = "test-mdvm-error")),
            actual,
        )
        verify(pushNotificationsApiClient, never()).getChallenge()
        verify(pushNotificationsApiClient, never()).signingApi(any())
        verify(signingPushNotificationsApiClient, never()).delete(any(), any())
    }

    @Test
    fun `deleteRegistration returns Failure when getChallenge fails`() = coroutineRule.runTest {
        whenever(mdvmAuthContextProvider.getMdvmAuthContext()).thenReturn(ApiResult.Success(authContext))
        whenever(pushNotificationsApiClient.getChallenge()).thenReturn(ApiResult.Failure(pnsError))

        val actual = subject.deleteRegistration()

        assertEquals(ApiResult.Failure(pnsError), actual)
        verify(pushNotificationsApiClient, never()).signingApi(any())
        verify(signingPushNotificationsApiClient, never()).delete(any(), any())
    }

    @Test
    fun `deleteRegistration returns Failure when delete fails`() = coroutineRule.runTest {
        whenever(mdvmAuthContextProvider.getMdvmAuthContext()).thenReturn(ApiResult.Success(authContext))
        whenever(pushNotificationsApiClient.getChallenge()).thenReturn(ApiResult.Success(challengeResponse))
        whenever(
            signingPushNotificationsApiClient.delete(any(), any())
        ).thenReturn(ApiResult.Failure(pnsError))

        val actual = subject.deleteRegistration()

        assertEquals(ApiResult.Failure(pnsError), actual)
    }

    // endregion

    // region handleRevocationPush

    @Test
    fun `handleRevocationPush retries when first renewal succeeds`() = coroutineRule.runTest {
        whenever(mdvmInteractor.mdvmRegistration(forceRenewal = true))
            .thenReturn(ApiResult.Success(mdvmRegistration))
            .thenReturn(ApiResult.Success(mdvmRegistration))

        subject.handleRevocationPush()

        verify(mdvmInteractor, org.mockito.Mockito.times(2)).mdvmRegistration(forceRenewal = true)
        // Two conclusive responses: no recheck left behind.
        inOrder(walletRevocationStore) {
            verify(walletRevocationStore).markRecheckPending()
            verify(walletRevocationStore).clearRecheckPending()
        }
    }

    @Test
    fun `handleRevocationPush does not retry when first renewal fails`() = coroutineRule.runTest {
        whenever(mdvmInteractor.mdvmRegistration(forceRenewal = true))
            .thenReturn(ApiResult.Failure(mdvmError))

        subject.handleRevocationPush()

        verify(mdvmInteractor).mdvmRegistration(forceRenewal = true)
        // A transient server error says nothing about revocation: the recheck must survive it.
        verify(walletRevocationStore).markRecheckPending()
        verify(walletRevocationStore, never()).clearRecheckPending()
    }

    @Test
    fun `handleRevocationPush clears the recheck when the failure is ACCOUNT_REVOKED`() =
        coroutineRule.runTest {
            whenever(mdvmInteractor.mdvmRegistration(forceRenewal = true))
                .thenReturn(
                    ApiResult.Failure(
                        MdvmErrorResponse(code = MdvmErrorType.ACCOUNT_REVOKED.code).toMdvmError()
                    )
                )

            subject.handleRevocationPush()

            verify(walletRevocationStore).clearRecheckPending()
        }

    @Test
    fun `handleRevocationPush keeps the recheck when the retry is a transient failure`() =
        coroutineRule.runTest {
            whenever(mdvmInteractor.mdvmRegistration(forceRenewal = true))
                .thenReturn(ApiResult.Success(mdvmRegistration))
                .thenReturn(ApiResult.Failure(mdvmError))

            subject.handleRevocationPush()

            verify(mdvmInteractor, org.mockito.Mockito.times(2)).mdvmRegistration(forceRenewal = true)
            verify(walletRevocationStore, never()).clearRecheckPending()
        }

    @Test
    fun `handleRevocationPush leaves the recheck pending when the renewal throws`() = coroutineRule.runTest {
        whenever(mdvmInteractor.mdvmRegistration(forceRenewal = true))
            .thenAnswer { throw IOException("device offline") }

        runCatching { subject.handleRevocationPush() }

        verify(walletRevocationStore).markRecheckPending()
        verify(walletRevocationStore, never()).clearRecheckPending()
    }

    @Test
    fun `handleRevocationPush is a no-op when the wallet is already locked`() = coroutineRule.runTest {
        whenever(walletRevocationStore.isRevoked()).thenReturn(true)

        subject.handleRevocationPush()

        verifyNoInteractions(mdvmInteractor)
        verify(walletRevocationStore, never()).markRecheckPending()
    }

    @Test
    fun `runPendingRevocationRecheck is a no-op without a pending recheck`() = coroutineRule.runTest {
        whenever(walletRevocationStore.isRecheckPending()).thenReturn(false)

        subject.runPendingRevocationRecheck()

        verifyNoInteractions(mdvmInteractor)
    }

    @Test
    fun `runPendingRevocationRecheck clears the flag on a conclusive response`() = coroutineRule.runTest {
        whenever(walletRevocationStore.isRecheckPending()).thenReturn(true)
        whenever(mdvmInteractor.mdvmRegistration(forceRenewal = true))
            .thenReturn(ApiResult.Success(mdvmRegistration))

        subject.runPendingRevocationRecheck()

        verify(mdvmInteractor).mdvmRegistration(forceRenewal = true)
        verify(walletRevocationStore).clearRecheckPending()
    }

    @Test
    fun `runPendingRevocationRecheck keeps the flag on a transient failure`() = coroutineRule.runTest {
        whenever(walletRevocationStore.isRecheckPending()).thenReturn(true)
        whenever(mdvmInteractor.mdvmRegistration(forceRenewal = true))
            .thenReturn(ApiResult.Failure(mdvmError))

        subject.runPendingRevocationRecheck()

        verify(walletRevocationStore, never()).clearRecheckPending()
    }

    @Test
    fun `runPendingRevocationRecheck keeps the flag when the renewal throws`() = coroutineRule.runTest {
        whenever(walletRevocationStore.isRecheckPending()).thenReturn(true)
        whenever(mdvmInteractor.mdvmRegistration(forceRenewal = true))
            .thenAnswer { throw IOException("device offline") }

        subject.runPendingRevocationRecheck()

        verify(walletRevocationStore, never()).clearRecheckPending()
    }

    @Test
    fun `handleRevocationPush retries when first succeeds and second fails with ACCOUNT_REVOKED`() = coroutineRule.runTest {
        whenever(mdvmInteractor.mdvmRegistration(forceRenewal = true))
            .thenReturn(ApiResult.Success(mdvmRegistration))
            .thenReturn(
                ApiResult.Failure(
                    MdvmErrorResponse(code = MdvmErrorType.ACCOUNT_REVOKED.code).toMdvmError()
                )
            )

        subject.handleRevocationPush()

        verify(mdvmInteractor, org.mockito.Mockito.times(2)).mdvmRegistration(forceRenewal = true)
    }

    // endregion
}