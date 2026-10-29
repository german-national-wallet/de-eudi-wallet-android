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
import eu.europa.ec.businesslogic.config.LogConfig
import eu.europa.ec.businesslogic.config.LogConfigImpl
import org.koin.core.annotation.Single

internal class EnvironmentConfigImpl(override val logConfig: LogConfig) :
    WalletBackendEnvironmentConfig() {

    override val serverHostURL: String
        get() = "https://staging.wallet-backend.example.invalid"

    override val OTEL_WALLET_AUTH_TOKEN: String
        get() = BuildConfig.OTEL_WALLET_AUTH_TOKEN

    override val WALLET_AUTH_TOKEN: String
        get() = BuildConfig.WALLET_AUTH_TOKEN

    override val pidIssuerSpec
        get() = PidIssuerSpec.PREPROD

    override val otelServiceName: String
        get() = "pentest-android"

    override val FEATURE_FLAG_API_TOKEN: String
        get() = BuildConfig.FEATURE_FLAG_API_TOKEN
    override val enableDebugMenu: Boolean = false

    override val enableSimulatedEidCard: Boolean = true
}

@Single
fun provideLogConfig(): LogConfig = LogConfigImpl()

@Single
fun provideEnvironmentConfig(logConfig: LogConfig): EnvironmentConfig =
    EnvironmentConfigImpl(logConfig)
