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
import project.convention.logic.config.LibraryModule
import project.convention.logic.kover.KoverExclusionRules
import project.convention.logic.kover.excludeFromKoverReport

plugins {
    id("project.android.library")
    // id("project.wallet.core")  //TODO to add after wallet-core library is re added
}

extensions.configure<LibraryExtension>("android") {
    namespace = "eu.europa.ec.corelogic"
}

moduleConfig {
    module = LibraryModule.CoreLogic
}

dependencies {
    implementation(project(LibraryModule.StorageLogic.path))
    implementation(project(LibraryModule.ResourcesLogic.path))
    implementation(project(LibraryModule.BusinessLogic.path))
    implementation(project(LibraryModule.NetworkLogic.path))
    implementation(project(LibraryModule.AuthenticationLogic.path))
    implementation(project(LibraryModule.Core.path))
    implementation(project(LibraryModule.AnalyticsLogic.path))

    implementation(libs.androidx.biometric)
    implementation(libs.nimbus.jose.jwt)

    testImplementation(project(LibraryModule.TestLogic.path))
    testImplementation(project(LibraryModule.TestFeatureLogic.path))
    testImplementation(libs.mockk)

    // Siop-Openid4VP library
    implementation(libs.eudi.lib.jvm.siop.openid4vp.kt) {
        exclude(group = "org.bouncycastle")
    }

    implementation(libs.ausweiss.sdk)
}

excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.CoreLogic.classes,
    excludedPackages = KoverExclusionRules.CoreLogic.packages,
)