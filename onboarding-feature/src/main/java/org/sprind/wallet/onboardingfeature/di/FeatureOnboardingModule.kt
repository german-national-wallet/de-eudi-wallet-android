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

package org.sprind.wallet.onboardingfeature.di

import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.sprind.wallet.onboardingfeature.interactor.OnboardingInteractor
import org.sprind.wallet.onboardingfeature.interactor.OnboardingInteractorImpl
import org.sprind.wallet.onboardingfeature.provider.OnboardingStorageProvider
import org.sprind.wallet.onboardingfeature.setup.interactor.NotificationsSetupGuard
import org.sprind.wallet.onboardingfeature.setup.interactor.PlatformAuthSetupGuard
import org.sprind.wallet.onboardingfeature.setup.interactor.PrivacyPolicySetupGuard
import org.sprind.wallet.onboardingfeature.setup.interactor.SetupInteractor
import org.sprind.wallet.onboardingfeature.setup.interactor.SetupInteractorImpl
import org.sprind.wallet.onboardingfeature.setup.interactor.SuccessSetupGuard
import org.sprind.wallet.onboardingfeature.setup.provider.SetupStorageProvider
import org.sprind.wallet.onboardingfeature.setup.storage.PrefsSetupStorageProvider
import org.sprind.wallet.onboardingfeature.storage.PrefsOnboardingStorageProvider
import org.sprind.wallet.uilogic.navigation.NavigationGuard

@Module
@ComponentScan("org.sprind.wallet.onboardingfeature")
class FeatureOnboardingModule

@Factory(binds = [NavigationGuard::class])
fun provideOnboardingInteractor(
    onboardingStorageProvider: OnboardingStorageProvider
): OnboardingInteractor =
    OnboardingInteractorImpl(onboardingStorageProvider)

@Single
fun provideOnboardingStorageProvider(
    prefsController: PrefsController
): OnboardingStorageProvider = PrefsOnboardingStorageProvider(prefsController)

@Single
fun provideSetupStorageProvider(
    prefsController: PrefsController
): SetupStorageProvider = PrefsSetupStorageProvider(prefsController)

@Factory
fun provideSetupInteractor(
    setupStorageProvider: SetupStorageProvider,
    navigationGuards: List<NavigationGuard>,
): SetupInteractor = SetupInteractorImpl(setupStorageProvider, navigationGuards)

@Factory(binds = [NavigationGuard::class])
fun providePlatformAuthSetupGuard(
    setupStorageProvider: SetupStorageProvider,
): PlatformAuthSetupGuard = PlatformAuthSetupGuard(setupStorageProvider)

@Factory(binds = [NavigationGuard::class])
fun providePrivacyPolicySetupGuard(
    setupStorageProvider: SetupStorageProvider,
): PrivacyPolicySetupGuard = PrivacyPolicySetupGuard(setupStorageProvider)

@Factory(binds = [NavigationGuard::class])
fun provideNotificationsSetupGuard(
    setupStorageProvider: SetupStorageProvider,
): NotificationsSetupGuard = NotificationsSetupGuard(setupStorageProvider)

@Factory(binds = [NavigationGuard::class])
fun provideSuccessSetupGuard(
    setupStorageProvider: SetupStorageProvider,
): SuccessSetupGuard = SuccessSetupGuard(setupStorageProvider)