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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.configure
import project.convention.logic.isGitSubmodule
import java.io.File

class AndroidLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val isGitSubmodule = project.isGitSubmodule()
            if (isGitSubmodule) {
                // Skip the linting tasks for git submodules
                afterEvaluate {
                    tasks
                        .filter { it.name.startsWith("lint", ignoreCase = true) }
                        .forEach {
                            it.enabled = false
                        }
                }
            }

            when {
                pluginManager.hasPlugin("com.android.application") ->
                    configure<ApplicationExtension> { lint { configureLint(target) } }

                pluginManager.hasPlugin("com.android.library") ->
                    configure<LibraryExtension> { lint { configureLint(target) } }

                else -> {
                    pluginManager.apply("com.android.lint")
                    extensions.configure<Lint> { configureLint(target) }
                }
            }
        }
    }
}

private fun Lint.configureLint(project: Project) {
    xmlReport = true
    htmlReport = true
    abortOnError = true
    warningsAsErrors = true
    checkDependencies = false
    checkTestSources = true
    checkGeneratedSources = false
    baseline = project.projectDir.resolve("lint-baseline.xml")
}
