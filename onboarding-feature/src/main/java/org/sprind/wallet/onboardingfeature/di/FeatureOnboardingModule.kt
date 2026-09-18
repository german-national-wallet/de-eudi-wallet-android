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