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

import android.security.keystore.UserNotAuthenticatedException
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.businesslogic.controller.revocation.WalletRevocationStore
import org.sprind.wallet.businesslogic.controller.storage.EncryptedPrefsUnavailableException
import org.sprind.wallet.commonfeature.interactor.MdvmInteractor
import org.sprind.wallet.corelogic.config.PidConfigurationOverridesProvider
import org.sprind.wallet.pushnotificationsfeature.dispatcher.FcmMessageDispatcher
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor
import org.sprind.wallet.pushnotificationsfeature.service.FcmRegistrationRecovery

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
            single<EncryptedPrefsController> { mock(EncryptedPrefsController::class.java) }
        }
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(assembledModules + storageStub)
        }.koin

        // Before the deferred-handler fix, this single resolution overflowed the stack.
        assertNotNull(koin.get<MdvmInteractor>())
        assertNotNull(koin.get<WalletRevocationStore>())
        assertNotNull(koin.get<PushNotificationsInteractor>())
        assertNotNull(koin.get<FcmRegistrationRecovery>())
        assertNotNull(koin.get<PidConfigurationOverridesProvider>())
    }

    @Test
    fun `the background start graph resolves while the encrypted preferences are locked`() {
        // The auth-bound master key is unusable until the next device unlock. Everything a
        // background start (FCM) resolves must still build.
        val lockedPrefs = mock(EncryptedPrefsController::class.java) { invocation ->
            if (invocation.method.declaringClass == Any::class.java) {
                Answers.RETURNS_DEFAULTS.answer(invocation)
            } else {
                throw EncryptedPrefsUnavailableException.Locked(UserNotAuthenticatedException())
            }
        }
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(assembledModules + module { single<EncryptedPrefsController> { lockedPrefs } })
        }.koin

        // Application.onCreate resolves the telemetry eagerly, on every start.
        assertNotNull(koin.get<Telemetry>())
        assertNotNull(koin.get<ConfigLogic>().environmentConfig.serverHostURL)
        // What WalletFirebaseMessagingService injects.
        assertNotNull(koin.get<FcmMessageDispatcher>())
        assertNotNull(koin.get<PushNotificationsInteractor>())
        assertNotNull(koin.get<WalletRevocationStore>())
        // What MainActivity injects for the resume retry.
        assertNotNull(koin.get<FcmRegistrationRecovery>())
    }
}
