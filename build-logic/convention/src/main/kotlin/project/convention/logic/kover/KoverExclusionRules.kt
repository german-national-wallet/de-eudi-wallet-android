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

package project.convention.logic.kover

import project.convention.logic.config.LibraryModule

private const val KOIN = "*.ksp.*"
private const val BUILD_CONFIG = "*BuildConfig*"
private const val SCREEN_COMPOSABLES = "*Screen*"

private val packagePrefixes = listOf(
    "eu.europa.ec.*.",
    "org.sprind.wallet.*.",
)

private val MODELS = packagePrefixes.map { it  + "model" }
private val DI = packagePrefixes.map { it + "di" }
private val ROUTER_GRAPH = packagePrefixes.map { it + "router" }

val koverModules: Map<LibraryModule, KoverExclusionRules> = mapOf(
    LibraryModule.AnalyticsLogic to KoverExclusionRules.AnalyticsLogic,
    LibraryModule.AuthenticationLogic to KoverExclusionRules.AuthenticationLogic,
    LibraryModule.BusinessLogic to KoverExclusionRules.BusinessLogic,
    LibraryModule.CoreLogic to KoverExclusionRules.CoreLogic,
    LibraryModule.NetworkLogic to KoverExclusionRules.NetworkLogic,
    LibraryModule.UiLogic to KoverExclusionRules.UiLogic,
    LibraryModule.CardReaderFeature to KoverExclusionRules.CardReaderFeature,
    LibraryModule.CommonFeature to KoverExclusionRules.CommonFeature,
    LibraryModule.DashboardFeature to KoverExclusionRules.DashboardFeature,
    LibraryModule.FeatureFlags to KoverExclusionRules.FeatureFlagFeature,
    LibraryModule.IssuanceFeature to KoverExclusionRules.IssuanceFeature,
    LibraryModule.PresentationFeature to KoverExclusionRules.PresentationFeature,
    LibraryModule.StartupFeature to KoverExclusionRules.StartupFeature,
    LibraryModule.StorageLogic to KoverExclusionRules.StorageLogic,
)

sealed interface KoverExclusionRules {
    val commonClasses: List<String>
        get() = listOf(
            BUILD_CONFIG,
            SCREEN_COMPOSABLES,
        )

    val commonPackages: List<String>
        get() = listOf(
            KOIN,
        ) + DI + MODELS + ROUTER_GRAPH

    val classes: List<String>
    val packages: List<String>

    sealed interface LogicModule : KoverExclusionRules
    sealed interface FeatureModule : KoverExclusionRules

    object AssemblyLogic : LogicModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages + listOf(
                "eu.europa.ec.assemblylogic",
            )
    }

    object BusinessLogic : LogicModule {
        override val classes: List<String>
            get() = commonClasses + listOf(
            )

        override val packages: List<String>
            get() = commonPackages + listOf(
            )
    }

    object UiLogic : LogicModule {
        override val classes: List<String>
            get() = commonClasses + listOf(
            )

        override val packages: List<String>
            get() = commonPackages + listOf(
                "eu.europa.ec.uilogic.component",
                "eu.europa.ec.uilogic.config",
                "eu.europa.ec.uilogic.container",
                "eu.europa.ec.uilogic.extension",
                "eu.europa.ec.uilogic.mvi",
                "org.sprind.wallet.uilogic.component",
            )
    }

    object NetworkLogic : LogicModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages + listOf(
            )
    }

    object CardReaderFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses
        override val packages: List<String>
            get() = commonPackages
    }

    object CommonFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses + listOf(
            )

        override val packages: List<String>
            get() = commonPackages + listOf(
                "eu.europa.ec.commonfeature.config",
                "eu.europa.ec.commonfeature.ui.*.model",
            )
    }

    object StartupFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses
        override val packages: List<String>
            get() = commonPackages
    }

    object DashboardFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages + listOf(
            )
    }

    object PresentationFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages
    }

    /*
    object ProximityFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages + listOf(
                "eu.europa.ec.proximityfeature.ui.qr.component",
            )
    }
    */

    object IssuanceFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages
    }

    object FeatureFlagFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages + listOf(
            )
    }

    object WalletPinFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages
    }

    object RevocationFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages
    }

    object PushNotificationsFeature : FeatureModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages
    }

    object AuthenticationLogic : FeatureModule {
        override val classes: List<String>
            get() = commonClasses + listOf(
                // Wraps Android KeyStore directly; can only be tested via androidTest on a device.
                "org.sprind.wallet.authenticationlogic.controller.mdvm.AndroidMdvmKeyManager",
            )

        override val packages: List<String>
            get() = commonPackages + listOf(
            )
    }

    object AnalyticsLogic : FeatureModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages + listOf(
            )
    }

    object CoreLogic : FeatureModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages + listOf(
            )
    }

    object StorageLogic : LogicModule {
        override val classes: List<String>
            get() = commonClasses

        override val packages: List<String>
            get() = commonPackages + listOf(
                "eu.europa.ec.storagelogic",
            )
    }
}
