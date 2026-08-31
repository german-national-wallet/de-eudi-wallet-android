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

package org.sprind.wallet.pushnotificationsfeature.di

import eu.europa.ec.businesslogic.controller.log.LogController
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContextProvider
import org.sprind.wallet.commonfeature.interactor.MdvmInteractor
import org.sprind.wallet.networklogic.pushnotifications.api.PushNotificationsApiClient
import org.sprind.wallet.pushnotificationsfeature.dispatcher.FcmMessageDispatcher
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractor
import org.sprind.wallet.pushnotificationsfeature.interactor.PushNotificationsInteractorImpl

@Module
@ComponentScan("org.sprind.wallet.pushnotificationsfeature")
class FeaturePushNotificationsModule {

    @Factory
    fun providePushNotificationsInteractor(
        pushNotificationsApiClient: PushNotificationsApiClient,
        mdvmAuthContextProvider: MdvmAuthContextProvider,
        mdvmInteractor: MdvmInteractor,
        logController: LogController,
    ): PushNotificationsInteractor = PushNotificationsInteractorImpl(
        pushNotificationsApiClient = pushNotificationsApiClient,
        mdvmAuthContextProvider = mdvmAuthContextProvider,
        mdvmInteractor = mdvmInteractor,
        logController = logController,
    )

    @Factory
    fun provideFcmMessageDispatcher(
        pushNotificationsInteractor: PushNotificationsInteractor,
        logController: LogController,
    ): FcmMessageDispatcher = FcmMessageDispatcher(
        interactor = pushNotificationsInteractor,
        logController = logController,
    )
}