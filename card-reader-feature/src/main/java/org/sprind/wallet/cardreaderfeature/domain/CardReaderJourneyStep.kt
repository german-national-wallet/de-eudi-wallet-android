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
 * The steps the flow announces on its overview screen, which the header then counts off.
 *
 * These are the steps as the user was promised them, not the routes it takes to walk them: several
 * routes share a step, and the routes off the journey have none.
 */
enum class CardReaderJourneyStep {
    DATA_RELEASE,
    CARD_PIN,
    CARD_SCAN,
    CREDENTIAL,
    ;

    /** Position in the announced journey, counting from 1. */
    val number: Int get() = ordinal + 1

    companion object {
        val TOTAL: Int = entries.size
    }
}

/**
 * The announced step this route belongs to, or `null` for the routes that are not part of the
 * journey: the questions asked before it starts, and the detours off it.
 *
 * Adding a route to the header is adding it here.
 */
val CardReaderRoute.journeyStep: CardReaderJourneyStep?
    get() = when (this) {
        CardReaderRoute.CONSENT -> CardReaderJourneyStep.DATA_RELEASE

        CardReaderRoute.ENTER_PIN,
        CardReaderRoute.PIN_BLOCKED_ERROR,
        CardReaderRoute.ENTER_CAN,
        CardReaderRoute.ENTER_CAN_SUCCESS,
        CardReaderRoute.ENTER_PUK,
        -> CardReaderJourneyStep.CARD_PIN

        CardReaderRoute.NFC_SCAN_EID_PIN,
        CardReaderRoute.NFC_SCAN_CAN,
        CardReaderRoute.NFC_SCAN_PUK,
        // Reached from a scan that could not start, so it counts as part of it.
        CardReaderRoute.NFC_ACTIVATION,
        -> CardReaderJourneyStep.CARD_SCAN

        CardReaderRoute.COMPLETED -> CardReaderJourneyStep.CREDENTIAL

        // Before the journey: the two onboarding questions and the overview that announces it.
        // Off the journey: the citizen office dead end, and the change PIN detour, which walks its
        // own steps and gets them here once their design lands.
        CardReaderRoute.ONBOARDING_CARD,
        CardReaderRoute.ONBOARDING_PIN,
        CardReaderRoute.PROGRESS_STEPS,
        CardReaderRoute.NO_PIN_LETTER_INFO,
        CardReaderRoute.TRANSPORT_PIN_LETTER,
        CardReaderRoute.ENTER_TRANSPORT_PIN,
        CardReaderRoute.NFC_SCAN_TRANSPORT_PIN,
        CardReaderRoute.ENTER_NEW_PIN_INSTRUCTIONS,
        CardReaderRoute.ENTER_NEW_PIN,
        CardReaderRoute.CONFIRM_NEW_PIN,
        CardReaderRoute.NFC_SCAN_NEW_PIN,
        -> null
    }

val CardReaderRoute.journeyStepCompleted: Boolean
    get() = this == CardReaderRoute.COMPLETED