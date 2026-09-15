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

package eu.europa.ec.startupfeature.interactor.registration

import app.cash.turbine.test
import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletRegistrationPartialState
import eu.europa.ec.authenticationlogic.controller.storage.WalletRegistrationStorageController
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verifyNoInteractions
import org.sprind.wallet.businesslogic.controller.revocation.WalletRevocationStore
import org.sprind.wallet.commonfeature.interactor.MdvmInteractor
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.toMdvmError

private const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"

class WalletRegistrationInteractorTest {

    @Mock
    private lateinit var appAttestationController: AppAttestationController

    @Mock
    private lateinit var walletRegistrationStorageController: WalletRegistrationStorageController

    @Mock
    private lateinit var mdvmInteractor: MdvmInteractor

    @Mock
    private lateinit var walletRevocationStore: WalletRevocationStore

    private lateinit var closeable: AutoCloseable

    /** Only presence matters here: the interactor does not read any field on success. */
    private val mdvmRegistration = MdvmRegistration(
        mdvm_wi_id = "test_wi_id",
        mdvm_token = "test_token",
        wi_mdvm_auth_keys_alias = "test_alias",
    )

    private val subject: WalletRegistrationInteractorImpl by lazy {
        WalletRegistrationInteractorImpl(
            appAttestationController = appAttestationController,
            walletRegistrationStorageController = walletRegistrationStorageController,
            mdvmInteractor = mdvmInteractor,
            walletRevocationStore = walletRevocationStore,
        )
    }

    @Before
    fun before() = runTest {
        closeable = MockitoAnnotations.openMocks(this@WalletRegistrationInteractorTest)
        whenever(walletRegistrationStorageController.getWalletRegistration()).thenReturn(null)
        whenever(walletRevocationStore.isRevoked()).thenReturn(false)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `a revoked wallet emits Revoked and never reaches the backend`() = runTest {
        whenever(walletRevocationStore.isRevoked()).thenReturn(true)

        subject.registerWallet().test {
            assertEquals(WalletInitialRegistrationPartialState.Revoked, awaitItem())
            awaitComplete()
        }
        verifyNoInteractions(mdvmInteractor)
        verifyNoInteractions(appAttestationController)
    }

    @Test
    fun `keeps the MDVM error code so it can be shown to the user`() = runTest {
        val mdvmError = MdvmErrorResponse(code = "ANDROID_INTEGRITY_FAILURE", trace_id = TRACE_ID)
        whenever(mdvmInteractor.mdvmRegistration())
            .thenReturn(ApiResult.Failure(mdvmError.toMdvmError()))

        subject.registerWallet().test {
            val failure = awaitItem() as WalletInitialRegistrationPartialState.Failure

            assertEquals("ANDROID_INTEGRITY_FAILURE", failure.backendErrorCode)
            assertEquals(TRACE_ID, failure.traceId)
            awaitComplete()
        }
    }

    @Test
    fun `keeps a wallet backend error code this app does not recognise`() = runTest {
        whenever(mdvmInteractor.mdvmRegistration()).thenReturn(ApiResult.Success(mdvmRegistration))
        whenever(appAttestationController.registerWallet()).thenReturn(
            flowOf(
                WalletRegistrationPartialState.Failure(
                    errorCode = "WB_SOME_CODE_ADDED_AFTER_RELEASE",
                    traceId = TRACE_ID,
                )
            )
        )

        subject.registerWallet().test {
            val failure = awaitItem() as WalletInitialRegistrationPartialState.Failure

            // The recognised code falls back to UNKNOWN, which drives the message shown...
            assertEquals(
                WalletInitialRegistrationPartialState.ErrorCode.UNKNOWN,
                failure.errorCode
            )
            // ...but the code the backend actually sent is what the user gets to quote.
            assertEquals("WB_SOME_CODE_ADDED_AFTER_RELEASE", failure.backendErrorCode)
            awaitComplete()
        }
    }

    @Test
    fun `maps a recognised wallet backend error code`() = runTest {
        whenever(mdvmInteractor.mdvmRegistration()).thenReturn(ApiResult.Success(mdvmRegistration))
        whenever(appAttestationController.registerWallet()).thenReturn(
            flowOf(
                WalletRegistrationPartialState.Failure(
                    errorCode = "WB_BAD_REQUEST",
                    traceId = TRACE_ID,
                )
            )
        )

        subject.registerWallet().test {
            val failure = awaitItem() as WalletInitialRegistrationPartialState.Failure

            assertEquals(
                WalletInitialRegistrationPartialState.ErrorCode.WB_BAD_REQUEST,
                failure.errorCode
            )
            assertEquals("WB_BAD_REQUEST", failure.backendErrorCode)
            awaitComplete()
        }
    }
}