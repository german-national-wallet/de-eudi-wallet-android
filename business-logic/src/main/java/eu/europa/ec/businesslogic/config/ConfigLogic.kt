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

package eu.europa.ec.businesslogic.config

import eu.europa.ec.businesslogic.BuildConfig
import org.sprind.wallet.businesslogic.config.PidIssuerSpec

interface ConfigLogic {

    /**
     * Build Type.
     */
    val appBuildType: AppBuildType get() = AppBuildType.getType()

    /**
     * Application Flavor.
     */
    val appFlavor: String get() = BuildConfig.FLAVOR

    /**
     * Server Environment Configuration.
     */
    val environmentConfig: EnvironmentConfig

    /**
     * Application version.
     */
    val appVersion: String get() = BuildConfig.APP_VERSION

    /**
     * Whether the debug menu and the burger icon opening it are part of this build.
     */
    val isDebugMenuEnabled: Boolean get() = environmentConfig.enableDebugMenu

    /**
     * Whether logs are printed to logcat.
     */
    val isLogcatEnabled: Boolean get() = environmentConfig.enableLogcat

    /**
     * Whether logs are written to file, from where the debug menu can export them.
     */
    val isLogWriterEnabled: Boolean get() = environmentConfig.enableLogWriter

}

enum class AppFlavor {
    DEV, DEMO
}

enum class AppBuildType {
    DEBUG, RELEASE;

    companion object {
        fun getType(): AppBuildType {
            return when (BuildConfig.BUILD_TYPE) {
                "debug" -> DEBUG
                else -> RELEASE
            }
        }
    }
}

abstract class EnvironmentConfig {

    val connectTimeoutSeconds: Long get() = 60
    val readTimeoutSeconds: Long get() = 60

    /**
     * Open Telemetry service name (i.e. dev-android)
     */
    abstract val otelServiceName: String

    /**
     * OpenTelemetry endpoint
     */
    abstract val OTEL_WALLET_URL: String

    abstract val OTEL_WALLET_AUTH_TOKEN: String
    abstract val WALLET_AUTH_TOKEN: String
    abstract val FEATURE_FLAG_API_TOKEN: String

    /**
     * Wallet endpoint
     */
    abstract val serverHostURL: String

    abstract val pidIssuerSpec: PidIssuerSpec

    /**
     * VCI Issuer endpoint
     */
    val pidIssuerURL: String
        get() = pidIssuerSpec.url

    /**
     * Whether the debug menu and the burger icon opening it are part of the app. The menu
     * only hosts developer tooling, so it is limited to the flavors we develop and test on.
     *
     * Defaults to false, so a flavor shipped to end users never carries it by accident.
     */
    open val enableDebugMenu: Boolean = false

    /**
     * Whether logs shall be printed to logcat.
     *
     * Defaults to false, so a flavor shipped to end users stays silent unless it opts in.
     */
    open val enableLogcat: Boolean = false

    /**
     * Whether logs shall be written to file and if they shall be exportable by the user.
     * Written logs can only be exported through the debug menu, so this requires
     * [enableDebugMenu] to be of any use.
     *
     * Defaults to false, so a flavor shipped to end users never collects log files.
     */
    open val enableLogWriter: Boolean = false

    /**
     * Feature flag base URL. The URL where feature flags can be downloaded is based
     * on this one with an ?apiKey=... query parameter. This query parameter is
     * added later by an Interceptor (FeatureFlagApikeyInterceptor) because Retrofit
     * requires the baseUrl to end in '/' (doesn't allow query parameters).
     *
     * Implementations must supply a value ending in '/'.
     */
    abstract val featureFlagApiBaseUrl: String
}
