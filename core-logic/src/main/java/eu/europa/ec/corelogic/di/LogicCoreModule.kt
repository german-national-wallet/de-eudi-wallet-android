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

package eu.europa.ec.corelogic.di

import android.content.Context
import com.governikus.ausweisapp.sdkwrapper.SDKWrapper
import com.governikus.ausweisapp.sdkwrapper.card.core.WorkflowCallbacks
import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.storage.HardwareKeyStorageController
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.config.WalletCoreConfigImpl
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsControllerImpl
import eu.europa.ec.corelogic.controller.WalletCoreLogController
import eu.europa.ec.corelogic.controller.WalletCoreLogControllerImpl
import eu.europa.ec.corelogic.handler.AusweisSdkAuthorizationHandler
import eu.europa.ec.corelogic.handler.pid.RefreshUrlInteractor
import eu.europa.ec.corelogic.handler.pid.RefreshUrlInteractorImpl
import eu.europa.ec.corelogic.handler.reader.AusweisCallbacksImpl
import eu.europa.ec.corelogic.handler.reader.CardReaderInteractor
import eu.europa.ec.corelogic.handler.reader.CardReaderInteractorImpl
import eu.europa.ec.corelogic.handler.reader.WorkflowEvent
import eu.europa.ec.corelogic.interactor.walletattestation.WalletAttestationInteractor
import eu.europa.ec.corelogic.interactor.walletattestation.WalletAttestationInteractorImpl
import eu.europa.ec.corelogic.provider.WalletCoreAttestationProvider
import eu.europa.ec.corelogic.provider.WalletCoreAttestationProviderImpl
import eu.europa.ec.corelogic.securearea.SignatureProvider
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.provider.SecureAreaWalletKeyManager
import eu.europa.ec.eudi.wallet.provider.WalletKeyManager
import eu.europa.ec.networklogic.di.DoNotFollowRedirects
import eu.europa.ec.networklogic.di.OpenId4VciVp
import eu.europa.ec.networklogic.repository.WalletAttestationRepository
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Single
import org.koin.core.scope.ScopeCallback
import org.koin.mp.KoinPlatform
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.Algorithm
import org.multipaz.securearea.AndroidKeystoreCreateKeySettings
import org.multipaz.securearea.AndroidKeystoreSecureArea
import org.multipaz.securearea.SecureArea
import org.sprind.wallet.authenticationlogic.controller.rwsca.RwscaController
import org.sprind.wallet.authenticationlogic.provider.RwscaPinSessionHolder
import org.sprind.wallet.authenticationlogic.provider.RwscaRegistrationsProvider
import org.sprind.wallet.corelogic.platformauth.PlatformAuthInvariant
import org.sprind.wallet.corelogic.securearea.RwscaSecureArea
import org.sprind.wallet.corelogic.storage.EncryptedStorageKeyManager
import org.sprind.wallet.corelogic.storage.EncryptedStorageKeyManagerImpl
import org.sprind.wallet.storagelogic.storage.EncryptedStorage
import java.security.SecureRandom

const val PRESENTATION_SCOPE_ID = "presentation_scope_id"

@Module
@ComponentScan("eu.europa.ec.corelogic")
class LogicCoreModule

@Factory
fun provideWorkFlowCallbacks(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    events: MutableSharedFlow<WorkflowEvent> = MutableSharedFlow(),
    logController: LogController,
    resourceProvider: ResourceProvider
): WorkflowCallbacks =
    AusweisCallbacksImpl(coroutineScope, events, logController, resourceProvider)

@Single
fun provideCardReaderInteractor(
    logController: LogController,
    context: Context,
    auswiesCallbacks: WorkflowCallbacks,
    sdkWrapper: SDKWrapper,
    configLogic: ConfigLogic,
): CardReaderInteractor =
    CardReaderInteractorImpl(
        logController,
        context,
        auswiesCallbacks,
        sdkWrapper,
        configLogic
    )

/**
 * Application-lifetime coroutine scope for fire-and-forget background work that must outlive a
 * screen's ViewModel — e.g. the post-presentation credential batch refresh, which is launched as
 * the presentation redirects away and would otherwise be cancelled with the ViewModel's scope.
 */
class AppCoroutineScope(scope: CoroutineScope) : CoroutineScope by scope

@Single
fun provideAppCoroutineScope(): AppCoroutineScope =
    AppCoroutineScope(CoroutineScope(SupervisorJob() + Dispatchers.IO))

@Single
fun provideSDKController() = SDKWrapper

@Single
fun provideAusweisAuthorizationHandler(
    refreshUrlInteractor: RefreshUrlInteractor,
): AusweisSdkAuthorizationHandler =
    AusweisSdkAuthorizationHandler(refreshUrlInteractor)

@Factory
fun provideRefreshUrlInteractor(
    logController: LogController,
    resourceProvider: ResourceProvider,
    @DoNotFollowRedirects
    okHttpClient: OkHttpClient,
): RefreshUrlInteractor =
    RefreshUrlInteractorImpl(logController, resourceProvider, okHttpClient)

@Single // needs to be single because the jwtBuilder
fun provideWalletAttestationInteractor(
    appAttestationController: AppAttestationController,
): WalletAttestationInteractor =
    WalletAttestationInteractorImpl(appAttestationController)

@Single
fun provideEncryptedStorageKeyManager(prefsController: PrefsController): EncryptedStorageKeyManager =
    EncryptedStorageKeyManagerImpl(prefsController)

@Single
fun provideMultipazStorage(context: Context, encryptedStorageKeyManager: EncryptedStorageKeyManager): org.multipaz.storage.Storage =
    EncryptedStorage(
        databasePath = context.noBackupFilesDir.resolve(
            "${EudiWalletConfig.DEFAULT_DOCUMENT_MANAGER_IDENTIFIER}.bin").path,
        passphrase = encryptedStorageKeyManager.getOrGenerateEncryptedStorageKey(),
    )

@Single
fun provideAndroidKeystoreSecureArea(
    multipazStorage: org.multipaz.storage.Storage,
): AndroidKeystoreSecureArea = runBlocking { AndroidKeystoreSecureArea.create(multipazStorage) }

@Single
fun provideWalletKeyManager(
    androidKeystoreSecureArea: AndroidKeystoreSecureArea,
): WalletKeyManager = SecureAreaWalletKeyManager(
    secureArea = androidKeystoreSecureArea,
    createKeySettingsProvider = { algorithm ->
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        AndroidKeystoreCreateKeySettings.Builder(ByteString(challenge))
            .setAlgorithm(algorithm)
            .build()
    },
)

@Single
fun provideWalletCoreConfig(
    context: Context,
    configLogic: ConfigLogic,
    authorizationHandler: AusweisSdkAuthorizationHandler,
    @Rwsc rwscSecureArea: SecureArea,
    androidKeystoreSecureArea: AndroidKeystoreSecureArea,
    multipazStorage: org.multipaz.storage.Storage,
): WalletCoreConfig = WalletCoreConfigImpl(
    context,
    configLogic,
    authorizationHandler,
    rwscSecureArea,
    androidKeystoreSecureArea,
    multipazStorage
)

@Single
fun provideWalletCoreLogController(
    logController: LogController,
): WalletCoreLogController = WalletCoreLogControllerImpl(logController)

@Single
fun provideWalletCoreDocumentsController(
    resourceProvider: ResourceProvider,
    eudiWallet: EudiWallet,
    walletCoreConfig: WalletCoreConfig,
    configLogic: ConfigLogic,
    authorizationHandler: AusweisSdkAuthorizationHandler,
    hardwareKeyStorageController: HardwareKeyStorageController,
    appAttestationController: AppAttestationController,
    logController: LogController,
    @OpenId4VciVp openId4VciVpKtorHttpClient: HttpClient,
    telemetry: org.sprind.wallet.analyticslogic.controller.Telemetry,
): WalletCoreDocumentsController {
    return WalletCoreDocumentsControllerImpl(
        resourceProvider,
        eudiWallet,
        walletCoreConfig,
        configLogic.environmentConfig.pidIssuerURL,
        authorizationHandler,
        hardwareKeyStorageController,
        appAttestationController = appAttestationController,
        logController = logController,
        ktorHttpClientFactory = { openId4VciVpKtorHttpClient },
        telemetry = telemetry,
    )
}
@Single
fun provideSignatureProvider() = SignatureProvider()

@Single
fun providePlatformAuthInvariant(
    context: Context,
    walletCoreDocumentsController: WalletCoreDocumentsController,
): PlatformAuthInvariant = PlatformAuthInvariant(
    keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager,
    walletCoreDocumentsController = walletCoreDocumentsController,
)

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Rwsc

@Rwsc
@Single
fun provideRwscSecureArea(
    multipazStorage: org.multipaz.storage.Storage,
    logController: LogController,
    rwscaController: RwscaController,
    registrationsProvider: RwscaRegistrationsProvider,
    rwscaPinSessionHolder: RwscaPinSessionHolder,
): SecureArea = RwscaSecureArea(
    rwscaController = rwscaController,
    registrationsProvider = registrationsProvider,
    pinSessionHolder = rwscaPinSessionHolder,
    logController = logController,
    storage = multipazStorage,
    supportedAlgorithms = listOf(Algorithm.ESP256),
)

@Single
fun provideEudiWallet(
    context: Context,
    walletCoreConfig: WalletCoreConfig,
    walletCoreLogController: WalletCoreLogController,
    @Rwsc rwscSecureArea: SecureArea,
    androidKeystoreSecureArea: AndroidKeystoreSecureArea,
    walletKeyManager: WalletKeyManager,
    walletCoreAttestationProvider: WalletCoreAttestationProvider,
    @OpenId4VciVp openId4VciVpKtorHttpClient: HttpClient,
): EudiWallet = EudiWallet(context, walletCoreConfig.config, walletProvider = walletCoreAttestationProvider) {
    withLogger(walletCoreLogController)
    withSecureAreas(
        listOf(
            rwscSecureArea,
            androidKeystoreSecureArea,
        )
    )
    withStorage(walletCoreConfig.storageToBeUsed)
    withWalletKeyManager(walletKeyManager)
    withKtorHttpClientFactory { openId4VciVpKtorHttpClient }
}

@Single
fun provideWalletCoreAttestationProvider(
    walletAttestationRepository: WalletAttestationRepository,
    walletCoreConfig: WalletCoreConfig
): WalletCoreAttestationProvider =
    WalletCoreAttestationProviderImpl(
        walletCoreConfig = walletCoreConfig,
        walletAttestationRepository = walletAttestationRepository
    )

/**
 * Koin scope that lives for all the document presentation flow. It is manually handled from the
 * ViewModels that start and participate on the presentation process
 * */
@Scope
class WalletPresentationScope

/**
 * Clears the [RwscaPinSessionHolder] when the presentation scope closes.
 *
 * This is the authoritative mechanism for clearing the rWSCA PIN session token at the end of a
 * presentation transaction (see WD-2861). The holder remains a process-wide `@Single` so that
 * [org.sprind.wallet.corelogic.securearea.RwscaSecureArea] can keep constructor-injecting it;
 * clearing is tied to the transaction boundary by this scope-close callback instead of by
 * per-branch `clearPinSession()` calls in feature code.
 *
 * Re-registering the same callback instance is a no-op because `Scope` dedups callbacks by
 * identity (`LinkedHashSet`), so calling [getOrCreatePresentationScope] repeatedly is safe.
 */
private val pinSessionClearCallback = object : ScopeCallback {
    override fun onScopeClose(scope: org.koin.core.scope.Scope) {
        KoinPlatform.getKoin().get<RwscaPinSessionHolder>().clear()
    }
}

/**
 * Get Koin scope that lives during document presentation flow
 * */
fun getOrCreatePresentationScope(): org.koin.core.scope.Scope =
    KoinPlatform.getKoin().getOrCreateScope<WalletPresentationScope>(PRESENTATION_SCOPE_ID)
        .also { it.registerCallback(pinSessionClearCallback) }

fun closePresentationScope() {
    runCatching {
        KoinPlatform.getKoin().getScopeOrNull(PRESENTATION_SCOPE_ID)?.close()
    }
}
