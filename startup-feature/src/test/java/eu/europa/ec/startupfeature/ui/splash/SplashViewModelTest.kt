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

package eu.europa.ec.startupfeature.ui.splash

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.startupfeature.interactor.registration.WalletInitialRegistrationPartialState
import eu.europa.ec.startupfeature.interactor.registration.WalletInitialRegistrationPartialState.ErrorCode
import eu.europa.ec.startupfeature.interactor.registration.WalletRegistrationInteractor
import eu.europa.ec.startupfeature.interactor.splash.SplashInteractor
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.networklogic.common.NetworkFailure
import kotlin.time.Instant

private const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"

class SplashViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val walletRegistrationInteractor: WalletRegistrationInteractor = mock()

    private val subject by lazy {
        SplashViewModel(
            interactor = mock<SplashInteractor>(),
            walletRegistrationInteractor = walletRegistrationInteractor,
            logController = mock<LogController>(),
            navigationGuards = emptyList(),
        )
    }

    @Test
    fun `a timeout shows the slow internet dialog, whose button registers again`() = coroutineRule.runTest {
        givenRegistrationFails(networkFailure = NetworkFailure.Timeout)

        val dialog = initialize()

        assertEquals(R.string.global_timeout_slow_internet_error_title, dialog.titleRes)
        assertNull(dialog.errorCode)
        assertNull(dialog.traceId)

        dialog.onPrimaryButtonClick()
        advance()

        verify(walletRegistrationInteractor, times(2)).registerWallet()
    }

    @Test
    fun `a rate limit shows the retry later dialog, whose button only dismisses`() = coroutineRule.runTest {
        givenRegistrationFails(
            networkFailure = NetworkFailure.RateLimited(Instant.parse("2026-10-24T10:05:00Z")),
        )

        val dialog = initialize()

        assertEquals(R.string.global_timeout_too_many_requests_title, dialog.titleRes)
        assertEquals(R.string.global_timeout_too_many_requests_paragraph, dialog.bodyTextRes)

        dialog.onPrimaryButtonClick()
        advance()

        assertNull(subject.viewState.value.errorDialog)
        verify(walletRegistrationInteractor, times(1)).registerWallet()
    }

    @Test
    fun `a retry registers right away, without replaying the logo animation`() = coroutineRule.runTest {
        givenRegistrationFails(networkFailure = NetworkFailure.Timeout)
        val dialog = initialize()

        dialog.onPrimaryButtonClick()
        coroutineRule.testScope.testScheduler.runCurrent()

        verify(walletRegistrationInteractor, times(2)).registerWallet()
    }

    @Test
    fun `a second retry while the registration still runs does not register again`() = coroutineRule.runTest {
        givenRegistrationFails(networkFailure = NetworkFailure.Timeout)
        val dialog = initialize()
        val pendingRegistration = CompletableDeferred<WalletInitialRegistrationPartialState>()
        whenever(walletRegistrationInteractor.registerWallet()).thenReturn(
            flow { emit(pendingRegistration.await()) }
        )

        dialog.onPrimaryButtonClick()
        dialog.onPrimaryButtonClick()
        advance()

        verify(walletRegistrationInteractor, times(2)).registerWallet()
        pendingRegistration.complete(WalletInitialRegistrationPartialState.Revoked)
        advance()
    }

    @Test
    fun `other failures keep their dialog`() = coroutineRule.runTest {
        givenRegistrationFails(networkFailure = null, errorCode = ErrorCode.WB_BAD_REQUEST)

        val dialog = initialize()

        assertEquals(R.string.app_onboarding_wb_bad_request_title, dialog.titleRes)
        assertEquals(ErrorCode.WB_BAD_REQUEST.name, dialog.errorCode)
        assertEquals(TRACE_ID, dialog.traceId)
    }

    private suspend fun givenRegistrationFails(
        networkFailure: NetworkFailure?,
        errorCode: ErrorCode = ErrorCode.NO_INTERNET,
    ) {
        whenever(walletRegistrationInteractor.registerWallet()).thenReturn(
            flowOf(
                WalletInitialRegistrationPartialState.Failure(
                    errorCode = errorCode,
                    traceId = TRACE_ID,
                    networkFailure = networkFailure,
                )
            )
        )
    }

    private fun initialize(): GenericErrorDialogConfig {
        subject.setEvent(Event.Initialize)
        advance()
        return checkNotNull(subject.viewState.value.errorDialog) { "no error dialog" }
    }

    /** Runs past the logo animation delay. */
    private fun advance() {
        coroutineRule.testScope.testScheduler.advanceUntilIdle()
    }
}
