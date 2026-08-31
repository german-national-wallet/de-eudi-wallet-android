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

package eu.europa.ec.authenticationlogic.di

import android.content.Context
import com.google.gson.Gson
import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.authenticationlogic.config.StorageConfigImpl
import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationControllerImpl
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletAttestationController
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletAttestationControllerImpl
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletRegistrationController
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletRegistrationControllerImpl
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationControllerImpl
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationController
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationControllerImpl
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageControllerImpl
import eu.europa.ec.authenticationlogic.controller.storage.HardwareKeyStorageController
import eu.europa.ec.authenticationlogic.controller.storage.HardwareKeyStorageControllerImpl
import eu.europa.ec.authenticationlogic.controller.storage.WalletPinUnBlockTimeStorageController
import eu.europa.ec.authenticationlogic.controller.storage.WalletPinUnBlockTimeStorageControllerImpl
import eu.europa.ec.authenticationlogic.controller.storage.WalletRegistrationStorageController
import eu.europa.ec.authenticationlogic.controller.storage.WalletRegistrationStorageControllerImpl
import eu.europa.ec.authenticationlogic.controller.storage.util.KeyConverter
import eu.europa.ec.authenticationlogic.controller.storage.util.KeyConverterImpl
import eu.europa.ec.authenticationlogic.encoder.Base64EncoderDecoder
import eu.europa.ec.authenticationlogic.encoder.Base64EncoderDecoderDecoderImpl
import eu.europa.ec.authenticationlogic.jwt.DefaultJwtParser
import eu.europa.ec.authenticationlogic.jwt.InstantProvider
import eu.europa.ec.authenticationlogic.jwt.JwtBuilder
import eu.europa.ec.authenticationlogic.jwt.JwtParser
import eu.europa.ec.authenticationlogic.provider.TakIdProvider
import eu.europa.ec.authenticationlogic.provider.TakIdProviderImpl
import eu.europa.ec.authenticationlogic.storage.PrefsBiometryStorageProvider
import eu.europa.ec.authenticationlogic.storage.PrefsHardwareKeyStorageProvider
import eu.europa.ec.authenticationlogic.storage.PrefsWalletPinUnBlockTimeStorageProvider
import eu.europa.ec.authenticationlogic.storage.PrefsWalletRegistrationStorageProvider
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.sprind.wallet.authenticationlogic.controller.mdvm.AndroidMdvmKeyManager
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmKeyManager
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmController
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmControllerImpl
import org.sprind.wallet.authenticationlogic.controller.rwsca.LoggingRwscaController
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaControllerImpl
import org.sprind.wallet.authenticationlogic.controller.storage.MdvmRegistrationStorageController
import org.sprind.wallet.authenticationlogic.controller.storage.MdvmRegistrationStorageControllerImpl
import org.sprind.wallet.authenticationlogic.crypto.PinKeyFactory
import org.sprind.wallet.authenticationlogic.crypto.PinKeyFactoryImpl
import org.sprind.wallet.authenticationlogic.provider.RwscaPinSessionHolder
import org.sprind.wallet.authenticationlogic.provider.RwscaPinSessionHolderImpl
import org.sprind.wallet.authenticationlogic.jwt.JwtSigner
import org.sprind.wallet.authenticationlogic.jwt.JwtSignerImpl
import org.sprind.wallet.authenticationlogic.provider.JsonStorageProvider
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContextProvider
import org.sprind.wallet.authenticationlogic.provider.RwscaStorageController
import org.sprind.wallet.authenticationlogic.storage.PrefsJsonStorageProvider
import org.sprind.wallet.authenticationlogic.storage.PrefsMdvmRegistrationStorageProvider
import org.sprind.wallet.authenticationlogic.storage.RwscaStorageControllerImpl
import org.sprind.wallet.businesslogic.controller.crypto.EcKeyPairController
import org.sprind.wallet.businesslogic.util.RandomUUIDGenerator
import org.sprind.wallet.networklogic.mdvm.api.MdvmApiClient
import org.sprind.wallet.networklogic.rwsca.api.RwscaApiClient
import org.sprind.wallet.networklogic.walletbackend.api.WalletApiClient
import java.time.Clock

@Module
@ComponentScan("eu.europa.ec.authenticationlogic")
class LogicAuthenticationModule

@Single
fun provideStorageConfig(
    prefsController: PrefsController,
    jsonStorageProvider: JsonStorageProvider,
): StorageConfig = StorageConfigImpl(
    // EUDI-removed
    /*
    pinImpl = PrefsPinStorageProvider(prefsController),
    */
    biometryImpl = PrefsBiometryStorageProvider(prefsController),
    hardwareKeyImpl = PrefsHardwareKeyStorageProvider(prefsController),
    walletRegistrationImpl = PrefsWalletRegistrationStorageProvider(prefsController),
    walletPinBlockTimeImpl = PrefsWalletPinUnBlockTimeStorageProvider(prefsController),
    mdvmRegistrationImpl = PrefsMdvmRegistrationStorageProvider(jsonStorageProvider),
)

// EUDI-removed
/*
@Factory
fun providePinStorageController(
    storageConfig: StorageConfig
): PinStorageController = PinStorageControllerImpl(storageConfig)
*/

@Factory
fun provideDeviceAuthenticationController(
    resourceProvider: ResourceProvider,
    biometricAuthenticationController: BiometricAuthenticationController
): DeviceAuthenticationController =
    DeviceAuthenticationControllerImpl(
        resourceProvider = resourceProvider,
        biometricAuthenticationController = biometricAuthenticationController
    )

@Factory
fun provideBiometricAuthenticationController(
    resourceProvider: ResourceProvider,
    cryptoController: CryptoController,
    biometryStorageController: BiometryStorageController
): BiometricAuthenticationController =
    BiometricAuthenticationControllerImpl(
        resourceProvider = resourceProvider,
        cryptoController = cryptoController,
        biometryStorageController = biometryStorageController,
    )

@Factory
fun provideBiometryStorageController(
    storageConfig: StorageConfig
): BiometryStorageController = BiometryStorageControllerImpl(storageConfig)

@Single
fun provideKeyConverter(): KeyConverter = KeyConverterImpl()

@Factory
fun provideHardwareKeyStorageController(
    storageConfig: StorageConfig,
    cryptoController: CryptoController,
): HardwareKeyStorageController =
    HardwareKeyStorageControllerImpl(
        storageConfig,
        cryptoController,
    )

@Factory
fun provideWalletPinBlockTimeStorageController(
    storageConfig: StorageConfig
): WalletPinUnBlockTimeStorageController = WalletPinUnBlockTimeStorageControllerImpl(storageConfig)

@Factory
fun provideWalletRegistrationStorageController(
    storageConfig: StorageConfig,
    cryptoController: CryptoController,
    base64EncoderDecoder: Base64EncoderDecoder
): WalletRegistrationStorageController =
    WalletRegistrationStorageControllerImpl(
        storageConfig,
        cryptoController,
        base64EncoderDecoder
    )

@Single
fun provideWalletRegistrationController(
    apiClient: WalletApiClient,
    logController: LogController
): WalletRegistrationController = WalletRegistrationControllerImpl(apiClient, logController)

@Single
fun provideWalletAttestationController(
    apiClient: WalletApiClient,
    logController: LogController
): WalletAttestationController = WalletAttestationControllerImpl(apiClient, logController)

@Single
fun provideAppAttestationController(
    walletRegistrationController: WalletRegistrationController,
    jwtBuilder: JwtBuilder,
    walletRegistrationStorageController: WalletRegistrationStorageController,
    mdvmAuthContextProvider: MdvmAuthContextProvider,
    walletAttestationController: WalletAttestationController,
    logController: LogController
): AppAttestationController =
    AppAttestationControllerImpl(
        walletRegistrationController,
        jwtBuilder,
        walletRegistrationStorageController,
        mdvmAuthContextProvider,
        walletAttestationController,
        logController
    )

@Single
fun provideBase64Encoder(): Base64EncoderDecoder = Base64EncoderDecoderDecoderImpl()

@Single
fun provideInstantProvider(): InstantProvider = InstantProvider()

@Single
fun provideJwtBuilder(
    ecKeyPairController: EcKeyPairController,
    jwtSigner: JwtSigner,
    randomUUIDGenerator: RandomUUIDGenerator,
    logController: LogController,
    jwtParser: JwtParser
): JwtBuilder =
    JwtBuilder(
        ecKeyPairController,
        randomUUIDGenerator,
        logController,
        jwtParser,
        jwtSigner
    )

@Factory
fun provideJwtSigner(): JwtSigner = JwtSignerImpl(
    javaClock = Clock.systemUTC()
)

@Factory
fun providePinKeyFactory(): PinKeyFactory = PinKeyFactoryImpl()

@Single
fun provideRwscaPinSessionHolder(): RwscaPinSessionHolder = RwscaPinSessionHolderImpl()

@Single
fun provideTakIdProvider(
    logController: LogController
): TakIdProvider = TakIdProviderImpl(logController)

@Factory
fun provideJwtParser(): JwtParser = DefaultJwtParser()

@Single
fun provideMdvmKeyManager(context: Context): MdvmKeyManager = AndroidMdvmKeyManager(context)

@Factory
fun provideMdvmController(
    mdvmApiClient: MdvmApiClient,
    mdvmKeyManager: MdvmKeyManager,
    configLogic: ConfigLogic,
): MdvmController = MdvmControllerImpl(
    mdvmApiClient,
    mdvmKeyManager,
    configLogic,
)

@Factory
fun providerRwscaController(
    rwscaApiClient: RwscaApiClient,
    mdvmKeyManager: MdvmKeyManager,
    logController: LogController,
): RwscaController = LoggingRwscaController(
    logController,
    RwscaControllerImpl(
        rwscaApiClient = rwscaApiClient,
        mdvmKeyManager = mdvmKeyManager,
    )
)

@Single
fun provideMdvmRegistrationStorageController(
    storageConfig: StorageConfig,
): MdvmRegistrationStorageController = MdvmRegistrationStorageControllerImpl(
    storageConfig,
)

@Factory
fun provideGson(): Gson = Gson()

@Factory
fun provideJsonStorageProvider(
    prefsController: PrefsController,
    gson: Gson,
): JsonStorageProvider = PrefsJsonStorageProvider(prefsController, gson)

@Factory
fun provideRwscaStorageProvider(
    prefsController: PrefsController,
    gson: Gson,
): RwscaStorageController = RwscaStorageControllerImpl(prefsController, gson)
