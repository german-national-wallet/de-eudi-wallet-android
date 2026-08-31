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

package org.sprind.wallet.revocationfeature.di

import eu.europa.ec.authenticationlogic.controller.storage.WalletRegistrationStorageController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.sprind.wallet.uilogic.navigation.NavigationGuard
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.sprind.wallet.revocationfeature.interactor.RevocationInteractor
import org.sprind.wallet.revocationfeature.interactor.RevocationInteractorImpl
import org.sprind.wallet.revocationfeature.provider.RevocationStorageProvider
import org.sprind.wallet.revocationfeature.storage.PrefsRevocationStorageProvider

@Module
@ComponentScan("org.sprind.wallet.revocationfeature")
class FeatureRevocationModule

@Factory(binds = [NavigationGuard::class])
fun provideRevocationInteractor(
    walletRegistrationStorageController: WalletRegistrationStorageController,
    revocationStorageProvider: RevocationStorageProvider
): RevocationInteractor =
    RevocationInteractorImpl(
        walletRegistrationStorageController = walletRegistrationStorageController,
        revocationStorageProvider = revocationStorageProvider
    )

@Single
fun provideRevocationStorageProvider(
    prefsController: PrefsController,
): RevocationStorageProvider =
    PrefsRevocationStorageProvider(
        prefsController = prefsController
    )
