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

package eu.europa.ec.corelogic.handler

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.handler.pid.RefreshUrlInteractor
import eu.europa.ec.corelogic.handler.pid.RefreshUrlResult
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.sprind.wallet.businesslogic.config.EidCardType
import org.sprind.wallet.businesslogic.config.UserRuntimeConfig
import org.sprind.wallet.corelogic.eid.EidEngineImpl
import org.sprind.wallet.corelogic.eid.EidRequest
import org.sprind.wallet.corelogic.eid.FakeEidSdkPort
import org.sprind.wallet.corelogic.eid.SdkSignal

@OptIn(ExperimentalCoroutinesApi::class)
class AusweisSdkAuthorizationHandlerTest {

    private val port = FakeEidSdkPort()
    private val userRuntimeConfig: UserRuntimeConfig = mock(UserRuntimeConfig::class.java)
    private val refreshUrlInteractor: RefreshUrlInteractor = mock(RefreshUrlInteractor::class.java)

    init {
        whenever(userRuntimeConfig.eidCardType).thenReturn(EidCardType.PHYSICAL)
    }

    private fun TestScope.handler() = AusweisSdkAuthorizationHandler(
        eidEngine = EidEngineImpl(port, backgroundScope, mock(LogController::class.java)),
        userRuntimeConfig = userRuntimeConfig,
        refreshUrlInteractor = refreshUrlInteractor,
    )

    @Test
    fun `an authorization starts the authentication and offers it`() = runTest {
        val handler = handler()

        backgroundScope.async { handler.authorize(TC_TOKEN_URL) }
        runCurrent()
        port.emit(SdkSignal.Started)
        runCurrent()

        assertTrue(handler.authentication.value != null)
        assertEquals(listOf("CONNECT", "RUN_AUTH"), port.commands)
    }

    @Test
    fun `the chosen simulated card is the one authenticated with`() = runTest {
        whenever(userRuntimeConfig.eidCardType).thenReturn(EidCardType.VIRTUAL)
        whenever(userRuntimeConfig.simulatedEidCardPseudonymKey).thenReturn("pseudonym-key")
        val handler = handler()
        backgroundScope.async { handler.authorize(TC_TOKEN_URL) }
        runCurrent()
        port.emit(SdkSignal.Started)
        port.emit(SdkSignal.InsertCard)
        runCurrent()

        val insertCard = handler.authentication.value?.request?.value as EidRequest.InsertCard

        assertTrue(insertCard.acceptsSimulatedCard)
    }

    @Test
    fun `the redirect handed over answers the authorization`() = runTest {
        whenever(refreshUrlInteractor.callRefreshUrl(REDIRECT_URL)).thenReturn(
            flowOf(RefreshUrlResult.Success(AuthorizationResponse("code", "state", "nonce")))
        )
        val handler = handler()
        val authorization = backgroundScope.async { handler.authorize(TC_TOKEN_URL) }
        runCurrent()

        handler.resumeWithRedirectUri(REDIRECT_URL)

        assertEquals(Result.success(AuthorizationResponse("code", "state", "nonce")), authorization.await())
        assertNull(handler.authentication.value)
    }

    @Test
    fun `an authorization given up on ends its authentication`() = runTest {
        val handler = handler()
        val authorization = backgroundScope.async { handler.authorize(TC_TOKEN_URL) }
        runCurrent()
        port.emit(SdkSignal.Started)
        runCurrent()
        val authentication = handler.authentication.value

        authorization.cancel()
        runCurrent()

        assertEquals("CANCEL", port.commands.last())
        assertNull(handler.authentication.value)
        port.emit(SdkSignal.AuthFailed("User_Cancelled"))
        runCurrent()
        assertTrue(authentication?.outcome?.isCompleted == true)
    }

    @Test
    fun `a forgotten authorization is no longer offered`() = runTest {
        val handler = handler()
        backgroundScope.async { handler.authorize(TC_TOKEN_URL) }
        runCurrent()

        handler.forgetPendingAuthorization()

        assertNull(handler.authentication.value)
    }
}

private const val TC_TOKEN_URL = "https://pid.example/tcToken"
private const val REDIRECT_URL = "https://pid.example/refresh?code=1"
