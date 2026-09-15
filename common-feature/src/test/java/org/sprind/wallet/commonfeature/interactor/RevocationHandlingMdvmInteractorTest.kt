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

import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.Assert.assertTrue
import org.mockito.kotlin.verifyNoInteractions
import org.sprind.wallet.authenticationlogic.model.MdvmRegistration
import org.sprind.wallet.authenticationlogic.model.toMdvmError
import org.sprind.wallet.businesslogic.controller.revocation.WalletRevocationStore
import org.sprind.wallet.corelogic.revocation.WalletRevocationHandler
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorType
import org.sprind.wallet.networklogic.mdvm.model.error.MdvmErrorResponse

class RevocationHandlingMdvmInteractorTest {

    @Mock
    private lateinit var revocationStore: WalletRevocationStore

    @Mock
    private lateinit var revocationHandler: WalletRevocationHandler

    @Mock
    private lateinit var delegate: MdvmInteractor

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var subject: RevocationHandlingMdvmInteractor

    private lateinit var closeable: AutoCloseable

    private val mdvmRegistration = MdvmRegistration(
        mdvm_wi_id = "test-wi-id",
        mdvm_token = "test-mdvm-token",
        wi_mdvm_auth_keys_alias = "test-key-alias",
    )

    private val accountRevokedError = MdvmErrorResponse(code = MdvmErrorType.ACCOUNT_REVOKED.code).toMdvmError()

    private val transientError = MdvmErrorResponse(code = "TRANSIENT_ERROR").toMdvmError()

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(revocationStore.isRevoked()).thenReturn(false)
        subject = RevocationHandlingMdvmInteractor(revocationStore, { revocationHandler }, delegate)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `locks the wallet when delegate returns ACCOUNT_REVOKED`() = coroutineRule.runTest {
        val result = ApiResult.Failure(accountRevokedError)
        whenever(delegate.mdvmRegistration()).thenReturn(result)

        val actual = subject.mdvmRegistration()

        assertEquals(result, actual)
        verify(revocationHandler).lockWallet()
    }

    @Test
    fun `locks the wallet when delegate returns ACCOUNT_REVOKED with forceRenewal`() = coroutineRule.runTest {
        val result = ApiResult.Failure(accountRevokedError)
        whenever(delegate.mdvmRegistration(forceRenewal = true)).thenReturn(result)

        val actual = subject.mdvmRegistration(forceRenewal = true)

        assertEquals(result, actual)
        verify(revocationHandler).lockWallet()
    }

    @Test
    fun `does not lock when delegate returns a transient error`() = coroutineRule.runTest {
        val result = ApiResult.Failure(transientError)
        whenever(delegate.mdvmRegistration()).thenReturn(result)

        val actual = subject.mdvmRegistration()

        assertEquals(result, actual)
        verify(revocationHandler, never()).lockWallet()
    }

    @Test
    fun `does not lock when delegate returns Success`() = coroutineRule.runTest {
        val result = ApiResult.Success(mdvmRegistration)
        whenever(delegate.mdvmRegistration()).thenReturn(result)

        val actual = subject.mdvmRegistration()

        assertEquals(result, actual)
        verify(revocationHandler, never()).lockWallet()
    }

    @Test
    fun `an already-revoked wallet short-circuits to ACCOUNT_REVOKED without calling the delegate`() =
        coroutineRule.runTest {
            whenever(revocationStore.isRevoked()).thenReturn(true)

            val actual = subject.mdvmRegistration(forceRenewal = true)

            assertTrue(actual is ApiResult.Failure && actual.error.type == MdvmErrorType.ACCOUNT_REVOKED)
            verifyNoInteractions(delegate)
            verify(revocationHandler, never()).lockWallet()
        }
}