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

import eu.europa.ec.commonfeature.interactor.StartPinSessionResult
import junit.framework.TestCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.authenticationlogic.model.RwscaPinSession
import org.sprind.wallet.authenticationlogic.provider.RwscaPinSessionHolder
import org.sprind.wallet.businesslogic.model.UserPinImpl
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorResponse
import org.sprind.wallet.networklogic.rwsca.model.error.RwscaErrorType

class RwscaPinHandlerImplTest {

    @Mock
    private lateinit var rwscaInteractor: RwscaInteractor
    @Mock
    private lateinit var rwscaPinSessionHolder: RwscaPinSessionHolder

    private lateinit var closeable: AutoCloseable

    private fun fakePin() = UserPinImpl("111111")
    private val fakePinSession =
        RwscaPinSession(rwsca_pin_session_token_jwt = "fake-pin-session-jwt")
    private val fakeError = RwscaError.FromRwsca(
        type = RwscaErrorType.Companion.PIN_VERIFICATION_FAILED,
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
        RwscaPinHandlerImpl(rwscaInteractor, rwscaPinSessionHolder)
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
    fun `startPinSession returns Success when interactor returns Success`() = runTest {
        whenever(rwscaInteractor.rwscaPinSession(any())).thenReturn(ApiResult.Success(fakePinSession))

        val result = subject.startPinSession(fakePin())

        TestCase.assertEquals(StartPinSessionResult.Success, result)
    }

    @Test
    fun `startPinSession stores the pin session in the holder on success`() = runTest {
        whenever(rwscaInteractor.rwscaPinSession(any())).thenReturn(ApiResult.Success(fakePinSession))

        subject.startPinSession(fakePin())

        verify(rwscaPinSessionHolder).set(fakePinSession)
    }

    @Test
    fun `startPinSession returns Failure with the error when interactor returns Failure`() =
        runTest {
            whenever(rwscaInteractor.rwscaPinSession(any())).thenReturn(ApiResult.Failure(fakeError))

            val result = subject.startPinSession(fakePin())

            TestCase.assertEquals(StartPinSessionResult.Failure(fakeError), result)
        }

    @Test
    fun `startPinSession does not store anything in the holder on failure`() = runTest {
        whenever(rwscaInteractor.rwscaPinSession(any())).thenReturn(ApiResult.Failure(fakeError))

        subject.startPinSession(fakePin())

        verify(rwscaPinSessionHolder, never()).set(fakePinSession)
    }

    @Test
    fun `clearPinSession clears the holder`() {
        subject.clearPinSession()

        verify(rwscaPinSessionHolder).clear()
    }
}