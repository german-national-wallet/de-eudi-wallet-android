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

package eu.europa.ec.commonfeature.di


import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.commonfeature.interactor.BiometricInteractor
import eu.europa.ec.commonfeature.interactor.BiometricInteractorImpl
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Single
import org.koin.core.annotation.Module
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmController
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmKeyManager
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController
import org.sprind.wallet.authenticationlogic.controller.storage.MdvmRegistrationStorageController
import org.sprind.wallet.authenticationlogic.crypto.PinKeyFactory
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContextProvider
import org.sprind.wallet.authenticationlogic.provider.RwscaRegistrationsProvider
import org.sprind.wallet.authenticationlogic.provider.RwscaStorageController
import org.sprind.wallet.businesslogic.controller.storage.StorageController
import org.sprind.wallet.commonfeature.interactor.LoggingMdvmInteractor
import org.sprind.wallet.commonfeature.interactor.LoggingRwscaInteractor
import org.sprind.wallet.commonfeature.interactor.MdvmInteractor
import org.sprind.wallet.commonfeature.interactor.MdvmInteractorImpl
import org.sprind.wallet.commonfeature.interactor.RevocationHandlingMdvmInteractor
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.commonfeature.interactor.RwscaInteractorImpl
import org.sprind.wallet.commonfeature.provider.MdvmAuthContextProviderImpl
import org.sprind.wallet.commonfeature.provider.RwscaRegistrationsProviderImpl
import kotlin.time.Clock

@Module
@ComponentScan("eu.europa.ec.commonfeature", "org.sprind.wallet.commonfeature")
class FeatureCommonModule {
    // Single so that concurrent callers, the startup registration and the push notification
    // registration, share one instance and serialise on its lock
    @Single
    fun provideMdvmInteractor(
        logController: LogController,
        storageController: StorageController,
        mdvmController: MdvmController,
        mdvmRegistrationStorageController: MdvmRegistrationStorageController,
        clock: Clock,
    ): MdvmInteractor {
        // We deliberately don't expose a DI binding for the non-logging delegate because
        // cross-cutting decisions should be centralized in this module. This allows us to
        // stack decorators (logging, revocation self-lock) without introducing separate
        // bindings for each layer.
        return LoggingMdvmInteractor(
            logController = logController,
            delegate = RevocationHandlingMdvmInteractor(
                storageController = storageController,
                delegate = MdvmInteractorImpl(
                    mdvmController,
                    mdvmRegistrationStorageController,
                    clock,
                ),
            ),
        )
    }

    @Factory
    fun provideRwscaInteractor(
        mdvmInteractor: MdvmInteractor,
        rwscaController: RwscaController,
        rwscaStorageController: RwscaStorageController,
        pinKeyFactory: PinKeyFactory,
        logController: LogController,
    ): RwscaInteractor = LoggingRwscaInteractor(
        logController = logController,
        delegate = RwscaInteractorImpl(
            mdvmInteractor,
            rwscaController,
            rwscaStorageController,
            pinKeyFactory,
        )
    )

    @Factory
    fun provideRwscaRegistrationsProvider(
        rwscaInteractor: RwscaInteractor,
    ): RwscaRegistrationsProvider = RwscaRegistrationsProviderImpl(rwscaInteractor)

    @Factory
    fun provideMdvmAuthContextProvider(
        mdvmInteractor: MdvmInteractor,
        mdvmKeyManager: MdvmKeyManager,
    ): MdvmAuthContextProvider = MdvmAuthContextProviderImpl(mdvmInteractor, mdvmKeyManager)

    @Factory
    fun provideBiometricInteractor(
        biometryStorageController: BiometryStorageController,
        biometricAuthenticationController: BiometricAuthenticationController,
    ): BiometricInteractor {
        return BiometricInteractorImpl(
            biometryStorageController,
            biometricAuthenticationController,
        )
    }

}

