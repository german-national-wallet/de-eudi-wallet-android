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

/**
 * Provide a module to store user decisions during runtime to be dropped on app restart.
 */
interface UserRuntimeConfig {
    /**
     * Whether an ID card shall be virtualized in AddDocument process.
     */
    var eidCardType: EidCardType
}

internal class UserRuntimeConfigImpl : UserRuntimeConfig {
    // set to simulated by default, if Simulator is used
    override var eidCardType: EidCardType = if (BuildConfig.IS_SIMULATOR.toBoolean()) EidCardType.VIRTUAL else EidCardType.PHYSICAL
}

enum class EidCardType {
    PHYSICAL,
    VIRTUAL,
}

val EidCardType.isVirtual: Boolean
    get() = this == EidCardType.VIRTUAL
