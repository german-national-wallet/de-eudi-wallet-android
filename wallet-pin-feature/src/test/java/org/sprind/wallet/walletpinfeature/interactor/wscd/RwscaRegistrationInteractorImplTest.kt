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

package org.sprind.wallet.walletpinfeature.interactor.wscd

import eu.europa.ec.commonfeature.interactor.StartPinSessionResult
import org.sprind.wallet.businesslogic.model.UserPinImpl
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.authenticationlogic.model.RwscaRegistration
import org.sprind.wallet.authenticationlogic.provider.RwscaStorageController
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType

class RwscaRegistrationInteractorImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock private lateinit var rwscaPinHandler: RwscaPinHandler
    @Mock private lateinit var rwscaStorageController: RwscaStorageController

    private lateinit var closeable: AutoCloseable

    private val fakeRegistration = RwscaRegistration(rwsca_account_id = "fake-account-id")
    private val fakeError = RwscaError.FromRwsca(
        type = RwscaErrorType.PIN_VERIFICATION_FAILED,
        serverResponse = RwscaErrorResponse(
            code = "PIN_VERIFICATION_FAILED",
            description = "fake description",
            timestamp = "2026-01-01T00:00:00Z",
            trace_id = "fake-trace-id",
            tryCounter = 2,
            tryAllowedAfter = null,
        )
    )

    private val subject by lazy {
        RwscaRegistrationInteractorImpl(rwscaPinHandler, rwscaStorageController)
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
    fun `isAlreadyRegistered returns true when registration exists and pin is initialized`() {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(fakeRegistration)
        whenever(rwscaStorageController.isPinInitialized()).thenReturn(true)

        assertTrue(subject.isAlreadyRegistered())
    }

    @Test
    fun `isAlreadyRegistered returns false when no registration exists`() {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(null)
        whenever(rwscaStorageController.isPinInitialized()).thenReturn(true)

        assertFalse(subject.isAlreadyRegistered())
    }

    @Test
    fun `isAlreadyRegistered returns false when registration exists but pin is not initialized`() {
        whenever(rwscaStorageController.getRwscaRegistration()).thenReturn(fakeRegistration)
        whenever(rwscaStorageController.isPinInitialized()).thenReturn(false)

        assertFalse(subject.isAlreadyRegistered())
    }

    @Test
    fun `register emits Success when startPinSession succeeds`() = coroutineRule.runTest {
        whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)

        subject.register(UserPinImpl("111111")).runFlowTest {
            assertEquals(WscaRegistrationResult.Success, awaitItem())
        }
    }

    @Test
    fun `register emits Failure with error code and traceId when startPinSession fails`() =
        coroutineRule.runTest {
            whenever(rwscaPinHandler.startPinSession(any()))
                .thenReturn(StartPinSessionResult.Failure(fakeError))

            subject.register(UserPinImpl("111111")).runFlowTest {
                assertEquals(
                    WscaRegistrationResult.Failure(
                        errorCode = fakeError.code,
                        traceId = fakeError.traceId,
                    ),
                    awaitItem()
                )
            }
        }
}
