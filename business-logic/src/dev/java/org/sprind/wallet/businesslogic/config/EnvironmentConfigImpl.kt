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

internal class EnvironmentConfigImpl : WalletBackendEnvironmentConfig() {

    override val serverHostURL: String
        get() = "https://dev.wallet-backend.example.invalid"

    override val pidIssuerSpec
        get() = PidIssuerSpec.DEMO

    override val otelServiceName: String
        get() = "dev-android"

    // development flavor, so it carries the full debug tooling on every build type
    override val enableDebugMenu: Boolean = true
    override val enableLogcat: Boolean = true
    override val enableLogWriter: Boolean = true

}
