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
import eu.europa.ec.businesslogic.config.LogConfigImpl
import org.koin.core.annotation.Single

internal class EnvironmentConfigImpl(override val logConfig: LogConfig) :
    WalletBackendEnvironmentConfig() {

    override val serverHostURL: String
        get() = "https://sandbox.wallet-backend.example.invalid"

    override val pidIssuerSpec
        get() = PidIssuerSpec.PREPROD

    override val otelServiceName: String
        get() = "sandbox-android"

    // testing flavor, so it carries the full debug tooling on every build type
    override val enableDebugMenu: Boolean = true
    override val enableSimulatedEidCard: Boolean = true
}

@Single
fun provideLogConfig(): LogConfig =
    LogConfigImpl(isLogcatEnabled = true, isLogWriterEnabled = true)

@Single
fun provideEnvironmentConfig(
    overrides: DebugConfigOverrides,
    logConfig: LogConfig
): EnvironmentConfig =
    DebugConfig(delegate = EnvironmentConfigImpl(logConfig = logConfig), overrides = overrides)
