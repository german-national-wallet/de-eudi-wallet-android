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

package eu.europa.ec.assemblylogic.di

import android.app.Application
import org.sprind.wallet.assemblylogic.controller.AppBlockingController
import org.sprind.wallet.assemblylogic.controller.AppBlockingControllerImpl
import org.sprind.wallet.assemblylogic.controller.PlatformAuthenticationProvider
import org.sprind.wallet.assemblylogic.controller.PlatformAuthenticationProviderImpl
import eu.europa.ec.analyticslogic.di.LogicAnalyticsModule
import eu.europa.ec.authenticationlogic.di.LogicAuthenticationModule
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import eu.europa.ec.businesslogic.di.LogicBusinessModule
import org.sprind.wallet.cardreaderfeature.di.FeatureCardReaderModule
import eu.europa.ec.commonfeature.di.FeatureCommonModule
import eu.europa.ec.corelogic.di.LogicCoreModule
import eu.europa.ec.dashboardfeature.di.FeatureDashboardModule
import eu.europa.ec.issuancefeature.di.FeatureIssuanceModule
import eu.europa.ec.networklogic.di.LogicNetworkModule
import eu.europa.ec.presentationfeature.di.FeaturePresentationModule
import eu.europa.ec.resourceslogic.di.LogicResourceModule
import eu.europa.ec.startupfeature.di.FeatureStartupModule
import eu.europa.ec.uilogic.di.LogicUiModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ksp.generated.module
import org.sprind.wallet.flags.FEATURE_FLAGS_LAST_UPDATE
import org.sprind.wallet.flags.FeatureFlagConfig
import org.sprind.wallet.flags.FeatureFlagStorage
import org.sprind.wallet.flags.models.SAVED_FEATURE_FLAGS_OVERRIDE
import org.sprind.wallet.flags.di.FeatureFlagModule
import org.sprind.wallet.revocationfeature.di.FeatureRevocationModule
import org.sprind.wallet.walletpinfeature.di.FeatureWalletPinModule
import org.sprind.wallet.pushnotificationsfeature.di.FeaturePushNotificationsModule

private val featureFlagStorageModule = module {
    singleOf(::provideFeatureFlagConfig)
    singleOf(::provideFeatureFlagStorage).bind<FeatureFlagStorage>()
    singleOf(::PlatformAuthenticationProviderImpl).bind<PlatformAuthenticationProvider>()
    singleOf(::AppBlockingControllerImpl).bind<AppBlockingController>()
}

private val assembledModules = listOf(

    // Logic Modules
    LogicNetworkModule().module,
    LogicUiModule().module,
    LogicResourceModule().module,
    LogicBusinessModule().module,
    LogicAnalyticsModule().module,
    LogicAuthenticationModule().module,
    LogicCoreModule().module,

    // Feature Modules
    FeatureCommonModule().module,
    FeatureDashboardModule().module,
    FeatureStartupModule().module,
    FeaturePresentationModule().module,
    FeatureIssuanceModule().module,
    FeatureWalletPinModule().module,
    FeatureRevocationModule().module,
    FeaturePushNotificationsModule().module,
    FeatureCardReaderModule().module,
    FeatureFlagModule().module,
    featureFlagStorageModule,
)

fun Application.setupKoin(): KoinApplication {
    return startKoin {
        androidContext(this@setupKoin)
        androidLogger()
        modules(assembledModules)
    }
}

/**
 * Provides a [FeatureFlagStorage] implementation that delegates to [PrefsController].
 *
 * This adapter wraps the Android-specific PrefsController (which uses EncryptedSharedPreferences)
 * and exposes it through the FeatureFlagStorage interface. This allows the feature-flags module
 * to remain independent while the app module provides the concrete implementation.
 *
 * @param prefsController The PrefsController instance used for encrypted storage operations.
 */
private fun provideFeatureFlagStorage(prefsController: PrefsController): FeatureFlagStorage = object : FeatureFlagStorage {
    override fun getStoredFlags(): String =
        prefsController.getString(SAVED_FEATURE_FLAGS_OVERRIDE, "")

    override fun storeFlags(flags: String) =
        prefsController.setString(SAVED_FEATURE_FLAGS_OVERRIDE, flags)

    override fun setOnFlagsChangedListener(listener: (String) -> Unit) =
        prefsController.setOnKeyChangedListener(SAVED_FEATURE_FLAGS_OVERRIDE, "") { newValue ->
            listener(newValue ?: "")
        }

    override fun getLastUpdateTime(): String =
        prefsController.getString(FEATURE_FLAGS_LAST_UPDATE, "")

    override fun storeUpdateTime(time: String) =
        prefsController.setString(FEATURE_FLAGS_LAST_UPDATE, time)
}

/**
 * Provides a [FeatureFlagConfig] instance built from the app's [ConfigLogic].
 *
 * Extracts the minimal configuration required by feature-flags (logging flag and API base URL)
 * from the comprehensive ConfigLogic, avoiding direct dependency on business-logic.
 *
 * @param configLogic The app's ConfigLogic instance containing environment configuration.
 */
private fun provideFeatureFlagConfig(configLogic: ConfigLogic): FeatureFlagConfig =
    FeatureFlagConfig(
        isHttpLoggingEnabled = configLogic.isLogcatEnabled,
        featureFlagApiBaseUrl = configLogic.environmentConfig.featureFlagApiBaseUrl
    )
