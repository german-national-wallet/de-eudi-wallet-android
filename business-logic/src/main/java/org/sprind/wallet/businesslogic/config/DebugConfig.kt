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

package org.sprind.wallet.businesslogic.config

import eu.europa.ec.businesslogic.config.EnvironmentConfig
import eu.europa.ec.businesslogic.config.LogConfig
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController
import eu.europa.ec.resourceslogic.BuildConfig
import org.sprind.wallet.businesslogic.controller.storage.EncryptedPrefsUnavailableException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface DebugConfigOverrides {
    val walletHostUrl: String?
    val walletApiKey: String?
    val otlpHostUrl: String?
    val otlpAuthToken: String?
    val pidProviderUrl: String?
}

interface DebugConfigOverridesController : DebugConfigOverrides {
    override var walletHostUrl: String?
    override var walletApiKey: String?
    override var otlpHostUrl: String?
    override var otlpAuthToken: String?
    override var pidProviderUrl: String?
}

class PrefsDelegate(
    private val prefsController: EncryptedPrefsController,
    private val logController: LogController,
    private val key: String
) : ReadWriteProperty<Any?, String?> {

    /**
     * The stored override, or `null` when none is set or the encrypted preferences cannot be
     * read (see [EncryptedPrefsController]). An unreadable override falls back to the built-in default: these
     * are dev/sandbox debug settings, and failing here would take the whole process down.
     */
    override fun getValue(thisRef: Any?, property: KProperty<*>): String? {
        return try {
            if (prefsController.contains(key)) prefsController.getString(key, "") else null
        } catch (e: EncryptedPrefsUnavailableException) {
            logController.d(LOG_TAG) {
                "[STORAGE] debug override key=$key unreadable, using the default: " +
                        "${e.message} (${e.cause?.message})"
            }
            null
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
        if (value != null) {
            prefsController.setStringSync(key, value)
        } else {
            prefsController.clear(key)
        }
    }

    private companion object {
        const val LOG_TAG = "PrefsDelegate"
    }
}

fun EncryptedPrefsController.withKey(key: String, logController: LogController) =
    PrefsDelegate(this, logController, key)

class DebugConfigOverridesControllerImpl(
    prefsController: EncryptedPrefsController,
    logController: LogController,
) : DebugConfigOverridesController {
    override var walletHostUrl by prefsController.withKey(KEY_WALLET_HOST_URL, logController)
    override var walletApiKey by prefsController.withKey(KEY_WALLET_API_KEY, logController)
    override var otlpHostUrl by prefsController.withKey(KEY_OTLP_HOST_URL, logController)
    override var otlpAuthToken by prefsController.withKey(KEY_OTLP_AUTH_TOKEN, logController)
    override var pidProviderUrl by prefsController.withKey(KEY_PID_PROVIDER_URL, logController)

    companion object {
        const val KEY_WALLET_HOST_URL = "WalletHostUrl"
        const val KEY_WALLET_API_KEY = "WalletApiKey"
        const val KEY_OTLP_HOST_URL = "OtlpHostUrl"
        const val KEY_OTLP_AUTH_TOKEN = "OtlpAuthToken"
        const val KEY_PID_PROVIDER_URL = "PidProviderUrl"
    }
}

@Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
const val debugConfigAvailable =
    BuildConfig.FLAVOR == BuildConfig.FLAVOR_NAME_DEV ||
            BuildConfig.FLAVOR == BuildConfig.FLAVOR_NAME_SANDBOX

class DebugConfig(
    private val delegate: EnvironmentConfig,
    private val overrides: DebugConfigOverrides,
) : EnvironmentConfig() {

    init {
        if (!debugConfigAvailable) error("Never use debug config in production!")
    }

    // Plain getters, so that building the DI graph during a background start does not read
    // encrypted preferences (see [EncryptedPrefsController]), and so that a value resolved while
    // the preferences were unreadable is not cached for the rest of the process.
    override val otelServiceName = delegate.otelServiceName
    override val OTEL_WALLET_URL: String
        get() = overrides.otlpHostUrl ?: delegate.OTEL_WALLET_URL
    override val OTEL_WALLET_AUTH_TOKEN: String
        get() = overrides.otlpAuthToken ?: delegate.OTEL_WALLET_AUTH_TOKEN
    override val WALLET_AUTH_TOKEN: String
        get() = overrides.walletApiKey ?: delegate.WALLET_AUTH_TOKEN
    override val FEATURE_FLAG_API_TOKEN = delegate.FEATURE_FLAG_API_TOKEN
    override val serverHostURL: String
        get() = overrides.walletHostUrl ?: delegate.serverHostURL
    override val pidIssuerSpec: PidIssuerSpec
        get() = overrides.pidProviderUrl
            ?.let { delegate.pidIssuerSpec.copy(url = it) }
            ?: delegate.pidIssuerSpec
    override val walletBackendPinnerSpecs = delegate.walletBackendPinnerSpecs
    override val dataProtectionComplaintUrl = delegate.dataProtectionComplaintUrl
    override val reportIssueUrl = delegate.reportIssueUrl
    override val credentialCatalogueUrl = delegate.credentialCatalogueUrl
    override val featureFlagApiBaseUrl = delegate.featureFlagApiBaseUrl
    override val enableDebugMenu = delegate.enableDebugMenu
    override val enableSimulatedEidCard = delegate.enableSimulatedEidCard
    override val settingsLinks = delegate.settingsLinks
    override val logConfig: LogConfig = delegate.logConfig
}
