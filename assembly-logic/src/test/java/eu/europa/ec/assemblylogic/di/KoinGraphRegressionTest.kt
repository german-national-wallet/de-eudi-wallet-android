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

package eu.europa.ec.assemblylogic.di

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.sprind.wallet.businesslogic.controller.revocation.WalletRevocationStore
import org.sprind.wallet.commonfeature.interactor.MdvmInteractor
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class KoinGraphRegressionTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the MDVM interactor graph resolves without recursing`() {
        // EncryptedSharedPreferences needs a real AndroidKeyStore, which Robolectric lacks;
        // the leaf storage is mocked so the structural DI graph above it can be exercised.
        val storageStub = module {
            single<PrefsController> { mock(PrefsController::class.java) }
        }
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(assembledModules + storageStub)
        }.koin

        // Before the deferred-handler fix, this single resolution overflowed the stack.
        assertNotNull(koin.get<MdvmInteractor>())
        assertNotNull(koin.get<WalletRevocationStore>())
        assertNotNull(koin.get<PushNotificationsInteractor>())
    }
}
