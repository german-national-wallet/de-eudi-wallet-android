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

package org.sprind.wallet.commonfeature.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAuthenticate
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.commonfeature.interactor.IdentificationOnlyAuthenticationInteractor
import eu.europa.ec.testlogic.base.TestApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.sprind.wallet.businesslogic.controller.session.SessionManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class ProtectedScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sessionManager = mock<SessionManager>()
    private val identificationOnlyAuthenticationInteractor = mock<IdentificationOnlyAuthenticationInteractor>()
    private val isUnlockedFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        whenever(sessionManager.isUnlocked).thenReturn(isUnlockedFlow)
    }

    @Test
    fun whenUnlockedContentIsVisible() {
        isUnlockedFlow.value = true

        composeTestRule.setContent {
            ProtectedScreen(
                sessionManager = sessionManager,
                identificationOnlyAuthenticationInteractor = identificationOnlyAuthenticationInteractor,
                onDismiss = {}
            ) {
                Text("Secret Content")
            }
        }

        composeTestRule.onNodeWithText("Secret Content").assertExists()
    }

    @Test
    fun whenLockedAndBiometricsAvailableAuthenticationIsTriggered() {
        isUnlockedFlow.value = false

        doAnswer { invocation ->
            val callback = invocation.getArgument<(BiometricsAvailability) -> Unit>(0)
            callback.invoke(BiometricsAvailability.CanAuthenticate)
            null
        }.whenever(identificationOnlyAuthenticationInteractor).getBiometricsAvailability(any())

        composeTestRule.setContent {
            ProtectedScreen(
                sessionManager = sessionManager,
                identificationOnlyAuthenticationInteractor = identificationOnlyAuthenticationInteractor,
                onDismiss = {}
            ) {
                Text("Secret Content")
            }
        }

        verify(identificationOnlyAuthenticationInteractor).authenticateWithBiometrics(any(), eq(false), any())
    }

    @Test
    fun whenLockedAndBiometricsFailOrCancelOnDismissIsCalled() {
        isUnlockedFlow.value = false
        val onDismissMock = mock<() -> Unit>()

        doAnswer { invocation ->
            val callback = invocation.getArgument<(BiometricsAvailability) -> Unit>(0)
            callback.invoke(BiometricsAvailability.CanAuthenticate)
            null
        }.whenever(identificationOnlyAuthenticationInteractor).getBiometricsAvailability(any())

        doAnswer { invocation ->
            val callback = invocation.getArgument<(BiometricsAuthenticate) -> Unit>(2)
            callback.invoke(BiometricsAuthenticate.Cancelled)
            null
        }.whenever(identificationOnlyAuthenticationInteractor).authenticateWithBiometrics(any(), eq(false), any())

        composeTestRule.setContent {
            ProtectedScreen(
                sessionManager = sessionManager,
                identificationOnlyAuthenticationInteractor = identificationOnlyAuthenticationInteractor,
                onDismiss = onDismissMock
            ) {}
        }

        verify(onDismissMock).invoke()
    }
}
