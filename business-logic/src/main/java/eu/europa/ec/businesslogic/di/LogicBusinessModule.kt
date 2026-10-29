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

package eu.europa.ec.businesslogic.di

import android.content.Context
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.config.ConfigLogicImpl
import eu.europa.ec.businesslogic.config.EnvironmentConfig
import eu.europa.ec.businesslogic.config.LogConfig
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.crypto.CryptoControllerImpl
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import eu.europa.ec.businesslogic.controller.crypto.KeystoreControllerImpl
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.log.LogControllerImpl
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.controller.storage.PrefKeysImpl
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerImpl
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.sprind.wallet.businesslogic.config.DebugConfigOverrides
import org.sprind.wallet.businesslogic.config.DebugConfigOverridesController
import org.sprind.wallet.businesslogic.config.DebugConfigOverridesControllerImpl
import org.sprind.wallet.businesslogic.config.UserRuntimeConfig
import org.sprind.wallet.businesslogic.config.UserRuntimeConfigImpl
import org.sprind.wallet.businesslogic.controller.crypto.CipherInstanceProvider
import org.sprind.wallet.businesslogic.controller.crypto.CryptoKeyGenerator
import org.sprind.wallet.businesslogic.controller.crypto.CryptoKeyGeneratorImpl
import org.sprind.wallet.businesslogic.controller.crypto.EcKeyPairController
import org.sprind.wallet.businesslogic.controller.crypto.EcKeyPairControllerImpl
import org.sprind.wallet.businesslogic.controller.crypto.KeyStoreInstanceProvider
import org.sprind.wallet.businesslogic.controller.storage.StorageController
import org.sprind.wallet.businesslogic.controller.storage.StorageControllerImpl
import org.sprind.wallet.businesslogic.util.RandomUUIDGenerator

@Module
@ComponentScan("eu.europa.ec.businesslogic", "org.sprind.wallet.businesslogic")
class LogicBusinessModule

@Single
fun provideConfigLogic(environmentConfig: EnvironmentConfig): ConfigLogic =
    ConfigLogicImpl(environmentConfig)

@Single(binds = [DebugConfigOverrides::class])
fun provideDebugConfigOverridesController(prefsController: PrefsController):
        DebugConfigOverridesController = DebugConfigOverridesControllerImpl(prefsController)

@Single
fun provideLogController(context: Context, logConfig: LogConfig): LogController =
    LogControllerImpl(context, logConfig)

@Single
fun providePrefsController(
    resourceProvider: ResourceProvider,
    logController: LogController,
): PrefsController = PrefsControllerImpl(resourceProvider, logController)

@Single
fun providePrefKeys(prefsController: PrefsController): PrefKeys =
    PrefKeysImpl(prefsController)

@Factory
fun provideKeyStoreInstanceProvider() = KeyStoreInstanceProvider()

@Single
fun provideRandomUUIDGenerator() = RandomUUIDGenerator()

@Single
fun provideEcKeyPairController(
    keyStoreInstanceProvider: KeyStoreInstanceProvider,
): EcKeyPairController = EcKeyPairControllerImpl(keyStoreInstanceProvider)

@Single
fun provideKeystoreController(
    prefKeys: PrefKeys,
    logController: LogController,
    keyStoreInstanceProvider: KeyStoreInstanceProvider,
    cryptoKeyGenerator: CryptoKeyGenerator,
): KeystoreController =
    KeystoreControllerImpl(
        prefKeys, logController,
        keyStoreInstanceProvider,
        cryptoKeyGenerator,
    )

@Single
fun provideStorageController(
    context: Context,
    prefsController: PrefsController
): StorageController =
    StorageControllerImpl(context, prefsController)
@Single
fun provideCipherInstanceProvider() = CipherInstanceProvider()

@Factory
fun provideCryptoController(
    keystoreController: KeystoreController,
    cipherInstanceProvider: CipherInstanceProvider,
    cryptoKeyGenerator: CryptoKeyGenerator,
    logController: LogController,
): CryptoController =
    CryptoControllerImpl(keystoreController, cipherInstanceProvider, cryptoKeyGenerator, logController)

@Factory
fun provideCryptoKeyGenerator(): CryptoKeyGenerator =
    CryptoKeyGeneratorImpl()

@Single
fun provideUserRuntimeConfig(configLogic: ConfigLogic): UserRuntimeConfig =
    UserRuntimeConfigImpl(configLogic)
