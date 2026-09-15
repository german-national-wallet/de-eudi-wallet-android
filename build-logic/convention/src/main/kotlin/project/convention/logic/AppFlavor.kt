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
import java.util.Properties

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

// The name of the properties file we are reading the version name and code from. The file
// must be in the project root.
private const val VERSION_PROPERTIES_FILE_NAME = "version.properties"

private const val VERSION_NAME_PROPERTY_KEY = "VERSION_NAME"

private const val VERSION_CODE_PROPERTY_KEY = "VERSION_CODE"

fun Project.configureFlavors(
    commonExtension: CommonExtension,
    flavorConfigurationBlock: ProductFlavor.(flavor: AppFlavor) -> Unit = {}
) {
    val versionProperties = Properties().apply {
            load(rootProject.file(VERSION_PROPERTIES_FILE_NAME).reader())
    }

    val versionNameProperty = versionProperties.getProperty(VERSION_NAME_PROPERTY_KEY)!!

    val versionCodeProperty = versionProperties.getProperty(VERSION_CODE_PROPERTY_KEY)!!.toInt()

    commonExtension.apply {
        flavorDimensions += FlavorDimension.contentType.name
        productFlavors {
            AppFlavor.entries.forEach {
                create(it.flavorName) {
                    val fullVersion = "$versionNameProperty-${it.name}"
                    dimension = FlavorDimension.contentType.name
                    if (this@apply is ApplicationExtension && this is ApplicationProductFlavor) {
                        versionName = fullVersion
                        versionCode = versionCodeProperty
                        applicationId = it.applicationId
                    }
                    manifestPlaceholders["appNameSuffix"] = it.applicationNameSuffix.orEmpty()
                    addConfigField(
                        "APP_VERSION",
                        fullVersion
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
