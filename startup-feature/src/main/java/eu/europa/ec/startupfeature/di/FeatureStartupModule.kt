/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.startupfeature.di

import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.storage.WalletRegistrationStorageController
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.startupfeature.interactor.registration.WalletRegistrationInteractor
import eu.europa.ec.startupfeature.interactor.registration.WalletRegistrationInteractorImpl
import eu.europa.ec.startupfeature.interactor.splash.SplashInteractor
import eu.europa.ec.startupfeature.interactor.splash.SplashInteractorImpl
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.sprind.wallet.commonfeature.interactor.MdvmInteractor
import kotlin.time.Clock

@Module
@ComponentScan("eu.europa.ec.startupfeature")
class FeatureStartupModule

@Factory
fun provideSplashInteractor(
    walletCoreDocumentsController: WalletCoreDocumentsController
): SplashInteractor = SplashInteractorImpl(
    walletCoreDocumentsController
)

@Factory
fun provideWalletRegistrationInteractor(
    appAttestationController: AppAttestationController,
    walletRegistrationStorageController: WalletRegistrationStorageController,
    mdvmInteractor: MdvmInteractor,
): WalletRegistrationInteractor =
    WalletRegistrationInteractorImpl(
        appAttestationController,
        walletRegistrationStorageController,
        mdvmInteractor,
    )

@Factory
fun provideClock(): Clock = Clock.System
