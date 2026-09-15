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

import android.content.Intent
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.sprind.wallet.pushnotificationsfeature.dispatcher.FcmMessageDispatcher
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WalletFirebaseMessagingServiceTest {

    private val dispatcher: FcmMessageDispatcher = mock(FcmMessageDispatcher::class.java)

    private lateinit var service: WalletFirebaseMessagingService

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<PrefsController> { mock(PrefsController::class.java) }
                    single<LogController> { mock(LogController::class.java) }
                    single<PushNotificationsInteractor> { mock(PushNotificationsInteractor::class.java) }
                    single { dispatcher }
                }
            )
        }
        service = Robolectric.setupService(WalletFirebaseMessagingService::class.java)
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a background notification message is handled by the app instead of the SDK`() {
        val intent = Intent(FcmNotificationIntercept.ACTION_RECEIVE)
            .putExtra("gcm.n.e", "1")
            .putExtra("gcm.n.title", "Wallet revoked")
            .putExtra(WalletFirebaseMessagingService.KEY_ACTION, FcmMessageDispatcher.ACTION_RENEW_MDVM_TOKEN)

        service.handleIntent(intent)

        verify(dispatcher).dispatch(eq(FcmMessageDispatcher.ACTION_RENEW_MDVM_TOKEN), any())
    }

    @Test
    fun `an intent without a notification block is left to the SDK`() {
        val intent = Intent("com.example.UNRELATED_ACTION")
            .putExtra("gcm.n.e", "1")

        service.handleIntent(intent)

        verify(dispatcher, never()).dispatch(any(), any())
    }
}
