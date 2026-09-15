/*
 * Copyright (c) 2025 European Commission
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
}

extensions.configure<LibraryExtension>("android") {
    namespace = "eu.europa.ec.analyticslogic"
}

moduleConfig {
    module = LibraryModule.AnalyticsLogic
}
dependencies {
    implementation(project(LibraryModule.BusinessLogic.path))
    api (libs.kotlinx.collections.immutable)
    api(platform(libs.opentelemetry.android.bom))
    api(libs.opentelemetry.android.agent) //parent dir
    api(libs.opentelemetry.android.sessions)
    api(libs.opentelemetry.exporter.otlp)
    api(libs.opentelemetry.extension.kotlin)
    api(libs.okhttp.logging)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver3)

    testImplementation(project(LibraryModule.TestLogic.path))
    testImplementation(project(LibraryModule.TestFeatureLogic.path))
}
excludeFromKoverReport(
    excludedClasses = KoverExclusionRules.AnalyticsLogic.classes,
    excludedPackages = KoverExclusionRules.AnalyticsLogic.packages,
)
