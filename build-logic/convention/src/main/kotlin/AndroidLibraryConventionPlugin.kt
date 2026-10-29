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
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.google.android.libraries.mapsplatform.secrets_gradle_plugin.SecretsPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import project.convention.logic.AppFlavor
import project.convention.logic.addConfigField
import project.convention.logic.config.LibraryModule
import project.convention.logic.config.LibraryPluginConfig
import project.convention.logic.configureFlavors
import project.convention.logic.configureGradleManagedDevices
import project.convention.logic.configureKotlinAndroid
import project.convention.logic.configurePrintApksTask
import project.convention.logic.disableUnnecessaryAndroidTests
import project.convention.logic.libs
import java.util.Properties

class AndroidLibraryConventionPlugin : Plugin<Project> {
    private companion object {
        /*
         * MDVM skip-integrity-checks header values are intentionally duplicated here instead of
         * imported from runtime code, because build-logic must stay independent from app modules.
         *
         * When changing these values, keep the following runtime files in sync:
         * - network-logic/.../mdvm/api/MdvmApi.kt:
         *   MdvmSkipIntegrityChecksHeaderValue lists all backend-supported header values.
         * - authentication-logic/.../controller/mdvm/MdvmController.kt:
         *   reads BuildConfig.MDVM_SKIP_INTEGRITY_CHECKS_HEADER_VALUE and passes it to the
         *   MDVM register/renewal requests.
         *
         * Backend semantics:
         * - play-integrity: skip Play Integrity (no-op on backends where Play Integrity has been removed).
         * - key-attestation: skip key attestation.
         * - true/all: skip all checks.
         * - false/missing header: skip no checks.
         *
         * The default value can be overridden with:
         * ./gradlew assembleDevRelease -PmdvmSkipIntegrityChecks=key-attestation
         */
        const val MDVM_SKIP_INTEGRITY_CHECKS_PLAY_INTEGRITY = "play-integrity"
        const val MDVM_SKIP_INTEGRITY_CHECKS_KEY_ATTESTATION = "key-attestation"
        const val MDVM_SKIP_INTEGRITY_CHECKS_TRUE = "true"
        const val MDVM_SKIP_INTEGRITY_CHECKS_ALL = "all"
        const val MDVM_SKIP_INTEGRITY_CHECKS_FALSE = "false"

        private const val REMOVED_IS_SIMULATOR = "IS_SIMULATOR"

        val MDVM_SKIP_INTEGRITY_CHECKS_VALUES = setOf(
            MDVM_SKIP_INTEGRITY_CHECKS_PLAY_INTEGRITY,
            MDVM_SKIP_INTEGRITY_CHECKS_KEY_ATTESTATION,
            MDVM_SKIP_INTEGRITY_CHECKS_TRUE,
            MDVM_SKIP_INTEGRITY_CHECKS_ALL,
            MDVM_SKIP_INTEGRITY_CHECKS_FALSE,
        )
    }

    override fun apply(target: Project) {

        with(target) {

            val config =
                extensions.create<LibraryPluginConfig>("moduleConfig", LibraryModule.Unspecified)

            val walletScheme = "eudi-wallet"
            val walletHost = "*"

            val eudiOpenId4VpScheme = "eudi-openid4vp"
            val eudiOpenid4VpHost = "*"

            val mdocOpenId4VpScheme = "mdoc-openid4vp"
            val mdocOpenid4VpHost = "*"

            val openId4VpScheme = "openid4vp"
            val openid4VpHost = "*"

            val haipOpenId4VpScheme = "haip-vp"
            val haipOpenid4VpHost = "*"

            val credentialOfferScheme = "openid-credential-offer"
            val credentialOfferHost = "*"

            val credentialOfferHaipScheme = "haip-vci"
            val credentialOfferHaipHost = "*"

            val openId4VciAuthorizationScheme = "eu.europa.ec.euidi"
            val openId4VciAuthorizationHost = "authorization"

            // Warn once per build (from a single module) if the removed flag is still set anywhere.
            if (path == LibraryModule.BusinessLogic.path &&
                (System.getenv(REMOVED_IS_SIMULATOR) != null || readProperty(REMOVED_IS_SIMULATOR) != null)
            ) {
                logger.warn(
                    "$REMOVED_IS_SIMULATOR is no longer read. Software keys are enabled by default " +
                        "on the dev flavor; override with -PenableSoftwareKeys=true|false. The " +
                        "platform-authentication blocking screen is no longer suppressed (set a " +
                        "screen lock on the device) and the eID card type no longer defaults to virtual."
                )
            }

            // Emulators only have a software keystore. Defaults to enabled on dev only.
            val enableSoftwareKeysProperty =
                readProperty("enableSoftwareKeys")?.toBooleanStrictOrNull()

            val enableEaaBiometricsProperty =
                readProperty("enableEaaBiometrics")?.toBooleanStrictOrNull() ?: true
            val enablePnsPermissionPopUpProperty = 
                readProperty("enablePNSPermissionPopUp")?.toBooleanStrictOrNull() ?: true
            val mdvmSkipIntegrityChecksProperty =
                readProperty("mdvmSkipIntegrityChecks")
                    ?.also {
                        require(it in MDVM_SKIP_INTEGRITY_CHECKS_VALUES) {
                            "Unsupported mdvmSkipIntegrityChecks value '$it'. " +
                                "Supported values: ${MDVM_SKIP_INTEGRITY_CHECKS_VALUES.joinToString()}"
                        }
                    }

            with(pluginManager) {
                apply("com.android.library")
                apply("project.android.library.kover")
                apply("project.android.lint")
                apply("project.android.koin")
                apply("kotlinx-serialization")
                apply("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
            }

            extensions.configure<LibraryExtension>("android") {
                configureKotlinAndroid(this)
                with(defaultConfig) {

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

                    addConfigField("DEEPLINK", "$walletScheme://")
                    addConfigField("EUDI_OPENID4VP_SCHEME", eudiOpenId4VpScheme)
                    addConfigField("MDOC_OPENID4VP_SCHEME", mdocOpenId4VpScheme)
                    addConfigField("OPENID4VP_SCHEME", openId4VpScheme)
                    addConfigField("HAIP_OPENID4VP_SCHEME", haipOpenId4VpScheme)
                    addConfigField("CREDENTIAL_OFFER_SCHEME", credentialOfferScheme)
                    addConfigField("CREDENTIAL_OFFER_HAIP_SCHEME", credentialOfferHaipScheme)
                    addConfigField("ISSUE_AUTHORIZATION_SCHEME", openId4VciAuthorizationScheme)
                    addConfigField("ISSUE_AUTHORIZATION_HOST", openId4VciAuthorizationHost)
                    addConfigField(
                        "ISSUE_AUTHORIZATION_DEEPLINK",
                        "$openId4VciAuthorizationScheme://$openId4VciAuthorizationHost"
                    )

                    // Manifest placeholders for Wallet deepLink
                    manifestPlaceholders["deepLinkScheme"] = walletScheme
                    manifestPlaceholders["deepLinkHost"] = walletHost

                    // Manifest placeholders used for OpenId4VP
                    manifestPlaceholders["eudiOpenid4vpScheme"] = eudiOpenId4VpScheme
                    manifestPlaceholders["eudiOpenid4vpHost"] = eudiOpenid4VpHost
                    manifestPlaceholders["mdocOpenid4vpScheme"] = mdocOpenId4VpScheme
                    manifestPlaceholders["mdocOpenid4vpHost"] = mdocOpenid4VpHost
                    manifestPlaceholders["openid4vpScheme"] = openId4VpScheme
                    manifestPlaceholders["openid4vpHost"] = openid4VpHost
                    manifestPlaceholders["haipOpenid4vpScheme"] = haipOpenId4VpScheme
                    manifestPlaceholders["haipOpenid4vpHost"] = haipOpenid4VpHost

                    // Manifest placeholders used for OpenId4VCI
                    manifestPlaceholders["credentialOfferHost"] = credentialOfferHost
                    manifestPlaceholders["credentialOfferScheme"] = credentialOfferScheme
                    manifestPlaceholders["credentialOfferHaipHost"] = credentialOfferHaipHost
                    manifestPlaceholders["credentialOfferHaipScheme"] = credentialOfferHaipScheme

                    // Manifest placeholders used for OpenId4VCI Authorization
                    manifestPlaceholders["openId4VciAuthorizationScheme"] =
                        openId4VciAuthorizationScheme
                    manifestPlaceholders["openId4VciAuthorizationHost"] =
                        openId4VciAuthorizationHost

                }
                configureFlavors(this)
                configureGradleManagedDevices(this)
            }

            extensions.configure<LibraryAndroidComponentsExtension> {
                configurePrintApksTask(this)
                disableUnnecessaryAndroidTests(target)

                onVariants { variant ->
                    val mdvmSkipIntegrityChecksHeaderValue =
                        mdvmSkipIntegrityChecksProperty ?: MDVM_SKIP_INTEGRITY_CHECKS_PLAY_INTEGRITY

                    val enableEaaBiometrics = variant.flavorName != AppFlavor.Dev.flavorName || enableEaaBiometricsProperty
                    val enablePnsPermissionPopUp = variant.flavorName != AppFlavor.Dev.flavorName || enablePnsPermissionPopUpProperty
                    val enableSoftwareKeys = enableSoftwareKeysProperty ?: (variant.flavorName == AppFlavor.Dev.flavorName)

                    variant.buildConfigFields?.apply {
                        put(
                            "MDVM_SKIP_INTEGRITY_CHECKS_HEADER_VALUE",
                            BuildConfigField(
                                "String",
                                "\"$mdvmSkipIntegrityChecksHeaderValue\"",
                                null
                            )
                        )
                        put("ENABLE_EAA_BIOMETRICS", BuildConfigField("boolean", "$enableEaaBiometrics", null))
                        put("ENABLE_PNS_PERMISSION_POP_UP", BuildConfigField("boolean", "$enablePnsPermissionPopUp", null))
                        put("ENABLE_SOFTWARE_KEYS", BuildConfigField("boolean", "$enableSoftwareKeys", null))
                    }
                }
            }

            extensions.configure<SecretsPluginExtension> {
                defaultPropertiesFileName = "secrets.defaults.properties"
                ignoreList.add("sdk.*")
                ignoreList.add(REMOVED_IS_SIMULATOR)
            }
            dependencies {
                add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
                add("implementation", libs.findLibrary("kotlinx-coroutines-guava").get())
                add("implementation", libs.findLibrary("kotlinx.serialization.json").get())
            }
            afterEvaluate {
                if (!config.module.isLogicModule && !config.module.isFeatureCommon) {
                    dependencies {
                        add("implementation", project(LibraryModule.CommonFeature.path))
                    }
                }
            }
        }
    }

    // Build flags come from -P or local.properties only, never from the per-flavor secret files.
    private fun Project.readProperty(key: String): String? {
        return providers.gradleProperty(key).orNull
            ?: readPropertiesFile(this, "local.properties").getProperty(key)
    }

    private fun readPropertiesFile(project: Project, fileName: String): Properties {
        return try {
            Properties().apply {
                load(project.rootProject.file(fileName).reader())
            }
        } catch (_: Exception) {
            Properties()
        }
    }
}
