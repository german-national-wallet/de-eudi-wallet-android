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
 * Route-level representation of the card reader journey.
 *
 * This is intentionally independent from Navigation Compose for now so the
 * flow order, progress, and route transitions can be tested in isolation
 * before the UI is split into multiple destinations.
 */
enum class CardReaderRoute {
    ONBOARDING_CARD,
    ONBOARDING_PIN,
    // What the flow is about to do, confirmed before it starts asking for data.
    PROGRESS_STEPS,
    // Dead end of the card PIN question: without a PIN letter, or with the PIN forgotten, the PIN
    // can only be set at a citizen's office, so the flow stops here and points the user there.
    NO_PIN_LETTER_INFO,
    CONSENT,
    // Shown right after the consent when the device has NFC switched off, so the
    // user can turn it on before the card ever has to be tapped.
    NFC_ACTIVATION,
    ENTER_PIN,
    NFC_SCAN_EID_PIN,
    PIN_BLOCKED_ERROR,
    // Input screen where the user types the CAN.
    ENTER_CAN,
    // Follow-up NFC prompt shown after the CAN has been entered.
    NFC_SCAN_CAN,
    ENTER_CAN_SUCCESS,
    ENTER_PUK,
    NFC_SCAN_PUK,
    TRANSPORT_PIN_LETTER,
    ENTER_TRANSPORT_PIN,
    NFC_SCAN_TRANSPORT_PIN,
    ENTER_NEW_PIN_INSTRUCTIONS,
    ENTER_NEW_PIN,
    CONFIRM_NEW_PIN,
    NFC_SCAN_NEW_PIN,
    COMPLETED,
}

val CardReaderRoute.isNfcPrompt: Boolean
    get() = when (this) {
        CardReaderRoute.NFC_SCAN_EID_PIN,
        CardReaderRoute.NFC_SCAN_CAN,
        CardReaderRoute.NFC_SCAN_PUK,
        CardReaderRoute.NFC_SCAN_TRANSPORT_PIN,
        CardReaderRoute.NFC_SCAN_NEW_PIN,
        -> true

        else -> false
    }

/**
 * Whether the toolbar offers the close ("X") action on this route.
 *
 * The questions that carry their own way out drop it: answering that none of the documents is at
 * hand ends the flow by itself, and the citizen's office dead end has nowhere to continue to. Both
 * keep only the back arrow, as the designs show.
 */
val CardReaderRoute.showsCloseAction: Boolean
    get() = when (this) {
        CardReaderRoute.ONBOARDING_CARD,
        CardReaderRoute.NO_PIN_LETTER_INFO,
        -> false

        else -> true
    }

/**
 * Whether the toolbar offers the help action, which opens the route's explanation sheet.
 *
 * Only routes that have such a sheet show the icon: the NFC activation step just hands the user to
 * the system settings, the PUK step is not modelled yet, and the success screen hides the toolbar
 * altogether, so on those the icon would open nothing. The steps overview shows it because its
 * design does, though the sheet behind it is still to come.
 */
val CardReaderRoute.hasHelpSheet: Boolean
    get() = when (this) {
        CardReaderRoute.NFC_ACTIVATION,
        CardReaderRoute.NO_PIN_LETTER_INFO,
        CardReaderRoute.ENTER_PUK,
        CardReaderRoute.COMPLETED,
        -> false

        else -> true
    }

fun CardReaderRoute.defaultNavigationPolicy(): CardReaderNavigationPolicy = CardReaderNavigationPolicy(
    backBehavior = when {
        // Detours rather than steps along the flow: they are not in the ordered route list, so back
        // resolves through the return target the flow remembered when it opened the detour.
        this == CardReaderRoute.NO_PIN_LETTER_INFO ||
            this == CardReaderRoute.NFC_ACTIVATION -> CardReaderBackBehavior.PREVIOUS_ROUTE
        isNfcPrompt -> CardReaderBackBehavior.PREVIOUS_ROUTE
        else -> CardReaderBackBehavior.EXIT_TO_DASHBOARD
    },
)
