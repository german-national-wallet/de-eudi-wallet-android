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

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import project.convention.logic.configureFlavors
import project.convention.logic.configureKotlinAndroid
import project.convention.logic.libs

/**
 * The wallet stories (robots, `DependencyOrderedRunner` and the abstract suites), shared by the
 * app's androidTest, which runs them in the wallet's process, and by `:automation-feature`, which runs them from
 * outside. Flavored like the other libraries, so CI's `lintDevDebug` lints it too.
 */
class AndroidAutomationLogicPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("project.android.lint")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                configureFlavors(this)
            }

            dependencies.apply {
                add("api", libs.findLibrary("androidx-test-uiautomator").get())
                add("api", libs.findLibrary("android-junit").get())
                add("api", libs.findLibrary("test-rules").get())
            }
        }
    }
}
