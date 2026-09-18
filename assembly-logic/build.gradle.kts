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

import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.project
import project.convention.logic.config.LibraryModule
import project.convention.logic.config.LibraryModule.AnalyticsLogic
import project.convention.logic.config.LibraryModule.AssemblyLogic
import project.convention.logic.config.LibraryModule.AuthenticationLogic
import project.convention.logic.config.LibraryModule.BusinessLogic
import project.convention.logic.config.LibraryModule.CardReaderFeature
import project.convention.logic.config.LibraryModule.CommonFeature
import project.convention.logic.config.LibraryModule.Core
import project.convention.logic.config.LibraryModule.CoreLogic
import project.convention.logic.config.LibraryModule.DashboardFeature
import project.convention.logic.config.LibraryModule.FeatureFlags
import project.convention.logic.config.LibraryModule.IssuanceFeature
import project.convention.logic.config.LibraryModule.NetworkLogic
import project.convention.logic.config.LibraryModule.OnboardingFeature
import project.convention.logic.config.LibraryModule.PresentationFeature
import project.convention.logic.config.LibraryModule.PushNotificationsFeature
import project.convention.logic.config.LibraryModule.WalletPinFeature
// BEGIN EUDI-removed
// import project.convention.logic.config.LibraryModule.ProximityFeature
// END EUDI-removed
import project.convention.logic.config.LibraryModule.ResourcesLogic
import project.convention.logic.config.LibraryModule.RevocationFeature
import project.convention.logic.config.LibraryModule.StartupFeature
import project.convention.logic.config.LibraryModule.StorageLogic
import project.convention.logic.config.LibraryModule.TestLogic
// BEGIN EUDI-removed
// import project.convention.logic.config.LibraryModule.StorageLogic
// END EUDI-removed
import project.convention.logic.config.LibraryModule.UiLogic
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport
import project.convention.logic.kover.koverModules

plugins {
    id("project.android.library")
    id("project.android.library.compose")
    // BEGIN EUDI-removed
    // id("project.rqes.sdk")
    // END EUDI-removed
}

extensions.configure<LibraryExtension>("android") {
    namespace = "eu.europa.ec.assemblylogic"

    defaultConfig {
        // App name
        manifestPlaceholders["appName"] = "d-you"
    }

    packaging {
        jniLibs.pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
        jniLibs.pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
        jniLibs.pickFirsts.add("lib/x86/libc++_shared.so")
        jniLibs.pickFirsts.add("lib/x86_64/libc++_shared.so")
    }
}

moduleConfig {
    module = AssemblyLogic
}

dependencies {

    // Logic Modules
    api(project(ResourcesLogic.path))
    api(project(BusinessLogic.path))
    api(project(UiLogic.path))
    api(project(NetworkLogic.path))
    api(project(AnalyticsLogic.path))
    api(project(AuthenticationLogic.path))
    api(project(CoreLogic.path))
    // BEGIN EUDI-removed
    // api(project(StorageLogic.path))
    // END EUDI-removed
    api(project(Core.path))

    // Feature Modules
    api(project(CommonFeature.path))
    api(project(StartupFeature.path))
    api(project(OnboardingFeature.path))
    api(project(RevocationFeature.path))
    api(project(DashboardFeature.path))
    api(project(PresentationFeature.path))
    // BEGIN EUDI-removed
    // api(project(ProximityFeature.path))
    // END EUDI-removed
    api(project(CardReaderFeature.path))
    api(project(IssuanceFeature.path))
    // BEGIN EUDI-added
    api(libs.ausweiss.sdk)
    api(project(FeatureFlags.path))
    api(project(WalletPinFeature.path))
    api(project(PushNotificationsFeature.path))
    testImplementation(project(TestLogic.path))
    // END EUDI-added

    // Test Cover Report
    koverModules.forEach {
        kover(project(it.key.path)) {
            excludeFromKoverReport(
                excludedClasses = it.value.classes,
                excludedPackages = it.value.packages,
            )
        }
    }
}

// Current Module Kover Report
excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.AssemblyLogic.classes,
    excludedPackages = KoverExclusionRules.AssemblyLogic.packages,
)