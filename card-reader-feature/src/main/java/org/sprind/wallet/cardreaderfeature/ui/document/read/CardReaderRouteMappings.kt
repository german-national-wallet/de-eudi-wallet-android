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

package org.sprind.wallet.cardreaderfeature.ui.document.read

import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowDefinition
import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowType
import org.sprind.wallet.cardreaderfeature.domain.CardReaderProgress
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute

/**
 * Converts UI-facing steps into the route model used by flow definitions and
 * route-aware navigation helpers.
 *
 * Input screens and NFC prompt screens are kept distinct, for example
 * [ReadCardScreenStep.EnterCan] and [ReadCardScreenStep.NfcScanPrompt.Can], so
 * callers can reason about the current interaction state without inspecting
 * the legacy step hierarchy.
 */
internal fun ReadCardScreenStep.toRoute(): CardReaderRoute = when (this) {
    ReadCardScreenStep.Consent -> CardReaderRoute.CONSENT
    ReadCardScreenStep.NfcActivation -> CardReaderRoute.NFC_ACTIVATION
    ReadCardScreenStep.EnterPin -> CardReaderRoute.ENTER_PIN
    ReadCardScreenStep.EnterNewPin -> CardReaderRoute.ENTER_NEW_PIN
    ReadCardScreenStep.ConfirmNewPin -> CardReaderRoute.CONFIRM_NEW_PIN
    ReadCardScreenStep.EnterNewPinInstructions -> CardReaderRoute.ENTER_NEW_PIN_INSTRUCTIONS
    ReadCardScreenStep.PinBlockedError -> CardReaderRoute.PIN_BLOCKED_ERROR
    ReadCardScreenStep.EnterCan -> CardReaderRoute.ENTER_CAN
    ReadCardScreenStep.EnterCanSuccess -> CardReaderRoute.ENTER_CAN_SUCCESS
    ReadCardScreenStep.EnterPuk -> CardReaderRoute.ENTER_PUK
    ReadCardScreenStep.EnterTransportPin -> CardReaderRoute.ENTER_TRANSPORT_PIN
    ReadCardScreenStep.Completed -> CardReaderRoute.COMPLETED
    ReadCardScreenStep.OnboardingCard -> CardReaderRoute.ONBOARDING_CARD
    ReadCardScreenStep.OnboardingPin -> CardReaderRoute.ONBOARDING_PIN
    ReadCardScreenStep.ProgressSteps -> CardReaderRoute.PROGRESS_STEPS
    ReadCardScreenStep.NoPinLetterInfo -> CardReaderRoute.NO_PIN_LETTER_INFO
    ReadCardScreenStep.TransportPinLetter -> CardReaderRoute.TRANSPORT_PIN_LETTER
    ReadCardScreenStep.NfcScanPrompt.EidPin -> CardReaderRoute.NFC_SCAN_EID_PIN
    ReadCardScreenStep.NfcScanPrompt.TransportPin -> CardReaderRoute.NFC_SCAN_TRANSPORT_PIN
    ReadCardScreenStep.NfcScanPrompt.Can -> CardReaderRoute.NFC_SCAN_CAN
    ReadCardScreenStep.NfcScanPrompt.Puk -> CardReaderRoute.NFC_SCAN_PUK
    ReadCardScreenStep.NfcScanPrompt.EidNewPinSet -> CardReaderRoute.NFC_SCAN_NEW_PIN
}

/**
 * Converts a route back into the screen-step model for APIs that still depend
 * on [ReadCardScreenStep].
 */
internal fun CardReaderRoute.toStep(): ReadCardScreenStep = when (this) {
    CardReaderRoute.CONSENT -> ReadCardScreenStep.Consent
    CardReaderRoute.NFC_ACTIVATION -> ReadCardScreenStep.NfcActivation
    CardReaderRoute.ENTER_PIN -> ReadCardScreenStep.EnterPin
    CardReaderRoute.ENTER_NEW_PIN -> ReadCardScreenStep.EnterNewPin
    CardReaderRoute.CONFIRM_NEW_PIN -> ReadCardScreenStep.ConfirmNewPin
    CardReaderRoute.ENTER_NEW_PIN_INSTRUCTIONS -> ReadCardScreenStep.EnterNewPinInstructions
    CardReaderRoute.PIN_BLOCKED_ERROR -> ReadCardScreenStep.PinBlockedError
    CardReaderRoute.ENTER_CAN -> ReadCardScreenStep.EnterCan
    CardReaderRoute.ENTER_CAN_SUCCESS -> ReadCardScreenStep.EnterCanSuccess
    CardReaderRoute.ENTER_PUK -> ReadCardScreenStep.EnterPuk
    CardReaderRoute.ENTER_TRANSPORT_PIN -> ReadCardScreenStep.EnterTransportPin
    CardReaderRoute.COMPLETED -> ReadCardScreenStep.Completed
    CardReaderRoute.ONBOARDING_CARD -> ReadCardScreenStep.OnboardingCard
    CardReaderRoute.ONBOARDING_PIN -> ReadCardScreenStep.OnboardingPin
    CardReaderRoute.PROGRESS_STEPS -> ReadCardScreenStep.ProgressSteps
    CardReaderRoute.NO_PIN_LETTER_INFO -> ReadCardScreenStep.NoPinLetterInfo
    CardReaderRoute.TRANSPORT_PIN_LETTER -> ReadCardScreenStep.TransportPinLetter
    CardReaderRoute.NFC_SCAN_EID_PIN -> ReadCardScreenStep.NfcScanPrompt.EidPin
    CardReaderRoute.NFC_SCAN_TRANSPORT_PIN -> ReadCardScreenStep.NfcScanPrompt.TransportPin
    CardReaderRoute.NFC_SCAN_CAN -> ReadCardScreenStep.NfcScanPrompt.Can
    CardReaderRoute.NFC_SCAN_PUK -> ReadCardScreenStep.NfcScanPrompt.Puk
    CardReaderRoute.NFC_SCAN_NEW_PIN -> ReadCardScreenStep.NfcScanPrompt.EidNewPinSet
}

internal fun flowDefinitionFor(type: CardReaderFlowType): CardReaderFlowDefinition =
    CardReaderFlowDefinition.forType(type)

/**
 * Resolves progress for a route within the selected flow definition.
 */
internal fun progressFor(
    flowType: CardReaderFlowType,
    route: CardReaderRoute,
): CardReaderProgress = flowDefinitionFor(flowType).progressFor(route)
