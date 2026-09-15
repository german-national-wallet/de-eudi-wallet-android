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

package org.sprind.wallet.pushnotificationsfeature.dispatcher

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor
import java.io.IOException

class FcmMessageDispatcherTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var interactor: PushNotificationsInteractor

    @Mock
    private lateinit var logController: LogController

    private lateinit var dispatcher: FcmMessageDispatcher

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        dispatcher = FcmMessageDispatcher(
            interactor = interactor,
            logController = logController,
        )
    }

    @Test
    fun `RENEW_MDVM_TOKEN action triggers the authenticated revocation flow`() = coroutineRule.runTest {
        dispatcher.dispatch(
            action = FcmMessageDispatcher.ACTION_RENEW_MDVM_TOKEN,
            scope = coroutineRule.testScope,
        )

        verify(interactor).handleRevocationPush()
    }

    @Test
    fun `unknown action is ignored and does not call the interactor`() = coroutineRule.runTest {
        dispatcher.dispatch(
            action = "UNKNOWN_ACTION",
            scope = coroutineRule.testScope,
        )

        verify(interactor, never()).handleRevocationPush()
    }

    @Test
    fun `null action is ignored and does not call the interactor`() = coroutineRule.runTest {
        dispatcher.dispatch(
            action = null,
            scope = coroutineRule.testScope,
        )

        verify(interactor, never()).handleRevocationPush()
    }

    @Test
    fun `a throwing revocation flow is logged and does not propagate out of the dispatch`() =
        coroutineRule.runTest {
            val offline = IOException("device offline")
            whenever(interactor.handleRevocationPush()).thenAnswer { throw offline }

            dispatcher.dispatch(
                action = FcmMessageDispatcher.ACTION_RENEW_MDVM_TOKEN,
                scope = coroutineRule.testScope,
            )

            verify(logController).e(eq("FcmDispatcher"), any<Throwable>())
        }
}