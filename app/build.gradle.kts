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

import org.cyclonedx.Version as CycloneDxSchema
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component as CycloneDxComponent
import project.convention.logic.AppBuildType
import project.convention.logic.config.LibraryModule
import java.util.Properties

plugins {
    id("project.android.application")
    id("project.android.application.compose")
    alias(libs.plugins.bytebuddy)
    alias(libs.plugins.cyclonedx)
}

android {
    // BEGIN EUDI-removed
    /*
    signingConfigs {
        create("release") {

            storeFile = file("${rootProject.projectDir}/sign")

            keyAlias = getProperty("androidKeyAlias") ?: System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = getProperty("androidKeyPassword") ?: System.getenv("ANDROID_KEY_PASSWORD")
            storePassword =
                getProperty("androidKeyPassword") ?: System.getenv("ANDROID_KEY_PASSWORD")

            enableV2Signing = true
        }
    }
    */
    // END EUDI-removed
    packaging {
        jniLibs.pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
        jniLibs.pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
        jniLibs.pickFirsts.add("lib/x86/libc++_shared.so")
        jniLibs.pickFirsts.add("lib/x86_64/libc++_shared.so")
    }

    defaultConfig {
        applicationId = "org.sprind.wallet"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments += mapOf(
            "clearPackageData" to "true"
        )
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = AppBuildType.DEBUG.applicationIdSuffix
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            applicationIdSuffix = AppBuildType.RELEASE.applicationIdSuffix
            //signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-openid4vci.pro",
                "proguard-dontwarn.pro",
                "proguard-opentelemetry.pro",
            )
        }
    }

    namespace = "eu.europa.ec.wallet"
}

dependencies {
    implementation(project(LibraryModule.AssemblyLogic.path))
    "baselineProfile"(project(LibraryModule.BaselineProfileLogic.path))
    implementation(libs.opentelemetry.android.agent)
    implementation(libs.opentelemetry.android.sessions)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.extension.kotlin)
    implementation(libs.instrumentation.android.log.library)
    byteBuddy(libs.instrumentation.android.log.agent)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.android.junit)
    androidTestImplementation(libs.test.rules)
    androidTestUtil(libs.androidx.test.orchestrator)
}
configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "com.squareup.okhttp3" && requested.name == "okhttp-jvm") {
                useTarget("com.squareup.okhttp3:okhttp:${requested.version}")
                because("choosing okhttp over okhttp-jvm")
            }
            if (requested.group == "com.squareup.okhttp3" && requested.name == "okhttp-release-runtime") {
                useTarget("com.squareup.okhttp3:okhttp:${requested.version}")
                because("choosing okhttp over okhttp-release-runtime")
            }
        }
    }
}

// CycloneDX SBOM for the shipped app.
//
// Covers the minimum information an SBOM has to carry (BSI TR-03183-2 section
// 5.2, and the same list under the NTIA minimum elements): component name,
// component version, supplier of each component, author of the SBOM itself,
// and a creation timestamp.
//
// The plugin supplies three of those five. It does NOT set `supplier` on
// components -- it writes the POM's <organization> into `publisher`, which is a
// different field with different semantics -- and it does not set
// `metadata.authors` at all. Both gaps are closed afterwards by
// config/sbom/enrich_sbom.py, which is also the piece the iOS and backend
// pipelines reuse; see config/sbom/README.md.
//
// Distinct from the dependency submission in sbom.yml, which feeds GitHub's
// vulnerability alerting. That snapshot format has no supplier or author field,
// so it cannot be the artifact handed to an auditor.
tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    // The runtime classpath of the shipped app: exactly the artifacts that end
    // up in the APK, transitive dependencies included. Flavors carry no
    // dependency differences -- they vary only in config fields and
    // applicationId -- so staging stands in for all four.
    includeConfigs.set(listOf("stagingReleaseRuntimeClasspath"))

    // Resolves each dependency's POM, which is where `publisher` comes from.
    // Turning this off would strip the strongest supplier signal we have and
    // push far more components onto the fallback map.
    includeMetadataResolution.set(true)

    // Build-time-only dependencies are not distributed, so they are not part of
    // what the product is made of.
    includeBuildEnvironment.set(false)

    projectType.set(CycloneDxComponent.Type.APPLICATION)
    schemaVersion.set(CycloneDxSchema.VERSION_16)

    // Names the SBOM after the artifact a reader actually installs, rather than
    // after the Gradle project (":app", version "unspecified").
    componentGroup.set("org.sprind")
    componentName.set("org.sprind.wallet")
    componentVersion.set(
        providers.provider {
            Properties()
                .apply { rootProject.file("version.properties").reader().use(::load) }
                .getProperty("VERSION_NAME")!!
        }
    )

    // JSON only. An unenriched bom.xml written beside the enriched bom.json is
    // a trap: it looks like the same document and is missing the two fields the
    // whole exercise is about.
    jsonOutput.set(layout.buildDirectory.file("reports/cyclonedx/bom.json"))
    xmlOutput.unsetConvention()
}
