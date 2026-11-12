/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import project.convention.logic.configureFlavors
import project.convention.logic.configureKotlinAndroid

/**
 * End-to-end tests that drive an installed wallet from the outside, like `:baseline-profile` does.
 *
 * The test APK is self-instrumenting: it instruments its own package, not the wallet's, so it does
 * not have to be signed with the wallet's key and the wallet does not have to be debuggable. The
 * same test APK therefore runs against any build of a flavor: debug, minified release, or the
 * signed nightly.
 *
 * The wallet's package defaults to the flavor's application ID (the `targetAppId` manifest
 * placeholder, read back as meta-data) and can be overridden at run time with
 * `-e targetAppId <package>`. Gradle-driven runs (`:automation-feature:connectedDevDebugAndroidTest`) pass the
 * application ID of the actually built wallet APK as that argument, as `:baseline-profile` does,
 * which also picks up any build-type suffix. The meta-data default is what raw `am instrument` runs
 * (CI) fall back to. The stories themselves live in `:automation-logic`.
 *
 * The test APK's own package is `org.sprind.wallet.automationfeature` for every flavor, on purpose: the flavors
 * differ only in the `targetAppId` default. So installing another flavor's e2e APK replaces the
 * previous one, and the runner name in `CI/run_automation_tests.sh` does not depend on the flavor.
 * Do not add an application ID suffix here.
 */
@Suppress("UnstableApiUsage")
class AndroidAutomationFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.test")

            extensions.configure<TestExtension> {
                configureKotlinAndroid(this)
                configureFlavors(this) { flavor ->
                    manifestPlaceholders["targetAppId"] = flavor.applicationId
                }
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                // Only used to match variants; the wallet APK is installed separately.
                targetProjectPath = ":app"
                experimentalProperties["android.experimental.self-instrumenting"] = true
            }

            extensions.configure<TestAndroidComponentsExtension> {
                onVariants { v ->
                    v.instrumentationRunnerArguments.put(
                        "targetAppId",
                        v.testedApks.map {
                            v.artifacts.getBuiltArtifactsLoader().load(it)?.applicationId.orEmpty()
                        }
                    )
                }
            }
        }
    }
}
