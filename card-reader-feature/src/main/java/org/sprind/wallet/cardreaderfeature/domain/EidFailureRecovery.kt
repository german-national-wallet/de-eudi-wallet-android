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

package org.sprind.wallet.cardreaderfeature.domain

/**
 * What can be done about a failed eID workflow, decided from its failure code: the SDK's codes
 * (https://www.ausweisapp.bund.de/sdk/failurecodes.html) or the app's own `EID_` ones.
 */
enum class EidFailureRecovery {
    /** The card, the connection or the network let the read down; running it again can work. */
    RETRY,

    /** The card's online function is off, and only the citizen's office can switch it on. */
    ACTIVATE_AT_CITIZEN_OFFICE,

    /** The card's PUK is used up, so the PIN needs a new PIN letter from the citizen's office. */
    NEW_PIN_AT_CITIZEN_OFFICE,

    /** The service or its certificates are at fault, and nothing on this side changes that. */
    NONE;

    companion object {
        /** Anything not known to be beyond repair is worth another read. */
        fun forErrorCode(errorCode: String): EidFailureRecovery = when {
            errorCode in deactivatedCodes -> ACTIVATE_AT_CITIZEN_OFFICE
            errorCode in pukUsedUpCodes -> NEW_PIN_AT_CITIZEN_OFFICE
            errorCode in unrecoverableCodes ||
                unrecoverablePrefixes.any { errorCode.startsWith(it) } -> NONE

            else -> RETRY
        }
    }
}