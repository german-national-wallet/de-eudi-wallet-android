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

import eu.europa.ec.businesslogic.config.ConfigLogic

/**
 * Provide a module to store user decisions during runtime to be dropped on app restart.
 */
interface UserRuntimeConfig {
    /**
     * Whether an ID card shall be virtualized in AddDocument process.
     */
    var eidCardType: EidCardType

    /**
     * The pseudonym key of the simulated eID card, made when [eidCardType] is switched to
     * [EidCardType.VIRTUAL] and kept until it is switched back, so the card stays the same card
     * across scans. Null while the card is physical.
     *
     * Switching to [EidCardType.VIRTUAL] throws [SimulatedEidCardKeyException] if no key could be
     * made, and the card stays physical.
     */
    val simulatedEidCardPseudonymKey: String?
}

internal class UserRuntimeConfigImpl(
    configLogic: ConfigLogic,
    private val simulatedEidCardKeyGenerator: SimulatedEidCardKeyGenerator,
) : UserRuntimeConfig {

    private val simulationEnabled = configLogic.isSimulatedEidCardEnabled

    override var simulatedEidCardPseudonymKey: String? = null
        private set

    override var eidCardType: EidCardType = EidCardType.PHYSICAL
        get() = if (simulationEnabled) field else EidCardType.PHYSICAL
        set(value) {
            val switchedOn = simulationEnabled && value.isVirtual
            if (switchedOn != eidCardType.isVirtual) {
                simulatedEidCardPseudonymKey =
                    if (switchedOn) simulatedEidCardKeyGenerator.generatePseudonymKeyHex() else null
            }
            field = value
        }
}

enum class EidCardType {
    PHYSICAL,
    VIRTUAL,
}

val EidCardType.isVirtual: Boolean
    get() = this == EidCardType.VIRTUAL
