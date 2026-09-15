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

import eu.europa.ec.businesslogic.BuildConfig
import eu.europa.ec.businesslogic.config.EnvironmentConfig

/**
 * Binds the [EnvironmentConfig] contract to concrete values.
 *
 * The endpoints and pin patterns below are PLACEHOLDERS. `.example.invalid` is a reserved
 * name that can never resolve, so the app builds and starts but every request against these
 * hosts fails - by design, rather than quietly reaching a host you do not control.
 * Substitute your own services to get a working wallet; see "Supplying your own services"
 * in README.md.
 *
 * This is the one place under `src/main` that holds service endpoints. Keep hostnames
 * here or in a flavor source set - never in another `src/main` class.
 */
abstract class WalletBackendEnvironmentConfig : EnvironmentConfig() {

    override val OTEL_WALLET_URL: String
        get() = "https://telemetry.example.invalid"

    override val featureFlagApiBaseUrl: String
        get() = "https://feature-flags.example.invalid/features/"

    override val OTEL_WALLET_AUTH_TOKEN: String
        get() = BuildConfig.OTEL_WALLET_AUTH_TOKEN

    override val WALLET_AUTH_TOKEN: String
        get() = BuildConfig.WALLET_AUTH_TOKEN

    override val FEATURE_FLAG_API_TOKEN: String
        get() = BuildConfig.FEATURE_FLAG_API_TOKEN

    override val walletBackendPinnerSpecs: List<OkCertificatePinnerSpec>
        get() = WALLET_BACKEND_PINNER_SPECS

    companion object {

        private val LETS_ENCRYPT_ROOT_PINS = setOf(
            "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=", // ISRG Root X1
            "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=", // ISRG Root X2
            "sha256/sCkq5UWXjg+7mKu9lMhhYF5bGLsy7VI/UNW3tccdR7w=", // Root YE
            "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88=", // Root YR
        )

        val WALLET_BACKEND_PINNER_SPECS = listOf(
            OkCertificatePinnerSpec(pattern = "**.wallet-backend.example.invalid", pins = LETS_ENCRYPT_ROOT_PINS),
            OkCertificatePinnerSpec(pattern = "**.eudi-wallet.example.invalid", pins = LETS_ENCRYPT_ROOT_PINS),
        )
    }
}
