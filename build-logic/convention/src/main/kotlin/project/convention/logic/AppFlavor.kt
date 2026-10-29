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

package project.convention.logic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke

@Suppress("EnumEntryName")
enum class FlavorDimension {
    contentType
}

enum class AppFlavor(
    val flavorName: String,
    val applicationId: String,
    val applicationNameSuffix: String? = null,
) {
    Dev("dev", "org.sprind.wallet.dev", " Dev"),
    Staging("staging", "org.sprind.wallet", " Staging"),
    Sandbox("sandbox", "org.sprind.wallet.sandbox", " Sandbox"),
    Production("production", "codes.common.dyou.prod");

    init {
        require(flavorName.matches(Regex("[a-z]+")))
    }
}

// Both files are tracked at the repository root: VERSION is bumped on merge to
// main, VERSION_CODE by the nightly.
private const val VERSION_NAME_FILE = "VERSION"
private const val VERSION_CODE_FILE = "VERSION_CODE"

// Strips all whitespace, exactly like the CI build hook (`tr -d '[:space:]'`),
// so Gradle and the artifact names CI derives always agree on the value.
private fun Project.readVersionFile(path: String): String = rootProject
    .file(path)
    .takeIf { it.isFile }
    ?.readText()
    ?.filterNot(Char::isWhitespace)
    .orEmpty()

fun Project.appVersionName(): String = readVersionFile(VERSION_NAME_FILE)
    .ifEmpty { error("$VERSION_NAME_FILE is missing or empty") }

fun Project.appVersionCode(): Int = readVersionFile(VERSION_CODE_FILE)
    .toIntOrNull()
    ?.takeIf { it > 0 }
    ?: error("$VERSION_CODE_FILE is missing or not a positive integer")

fun Project.configureFlavors(
    commonExtension: CommonExtension,
    flavorConfigurationBlock: ProductFlavor.(flavor: AppFlavor) -> Unit = {}
) {
    val baseVersionName = appVersionName()
    val baseVersionCode = appVersionCode()

    commonExtension.apply {
        flavorDimensions += FlavorDimension.contentType.name
        productFlavors {
            AppFlavor.entries.forEach {
                create(it.flavorName) {
                    val fullVersion = "$baseVersionName-${it.name}"
                    dimension = FlavorDimension.contentType.name
                    if (this@apply is ApplicationExtension && this is ApplicationProductFlavor) {
                        versionName = fullVersion
                        versionCode = baseVersionCode
                        applicationId = it.applicationId
                    }
                    manifestPlaceholders["appNameSuffix"] = it.applicationNameSuffix.orEmpty()
                    addConfigField(
                        "APP_VERSION",
                        fullVersion
                    )
                    addConfigField(
                        "APP_VERSION_CODE",
                        baseVersionCode
                    )
                    // Generates a set of constants such as FLAVOR_NAME_DEV, those are useful to
                    // compare the current flavor with a target one, i.e.:
                    // BuildConfig.FLAVOR == BuildConfig.FLAVOR_NAME_DEV
                    AppFlavor.entries.forEach {
                        addConfigField(
                            name = "FLAVOR_NAME_${it.flavorName.uppercase()}",
                            value = it.flavorName
                        )
                    }
                    flavorConfigurationBlock(this, it)
                }
            }
        }
    }
}
