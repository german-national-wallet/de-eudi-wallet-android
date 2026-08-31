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

/**
 * Legacy screen model still used by the ViewModel and compatibility helpers
 * while the feature transitions toward a fully route-driven flow.
 */
sealed class ReadCardScreenStep {
    data object Consent : ReadCardScreenStep()
    data object NfcActivation : ReadCardScreenStep()
    data object EnterPin : ReadCardScreenStep()
    data object EnterNewPin : ReadCardScreenStep()
    data object ConfirmNewPin : ReadCardScreenStep()
    data object EnterNewPinInstructions : ReadCardScreenStep()
    data object PinBlockedError : ReadCardScreenStep()
    data object EnterCan : ReadCardScreenStep()
    data object EnterCanSuccess : ReadCardScreenStep()
    data object EnterPuk : ReadCardScreenStep()
    data object EnterTransportPin : ReadCardScreenStep()
    data object Completed : ReadCardScreenStep()
    data object OnboardingCard : ReadCardScreenStep()
    data object OnboardingPin : ReadCardScreenStep()
    data object ProgressSteps : ReadCardScreenStep()
    data object NoPinLetterInfo : ReadCardScreenStep()
    data object TransportPinLetter : ReadCardScreenStep()

    sealed class NfcScanPrompt : ReadCardScreenStep() {
        data object EidPin : NfcScanPrompt()
        data object TransportPin : NfcScanPrompt()
        data object Can : NfcScanPrompt()
        data object Puk : NfcScanPrompt()
        data object EidNewPinSet : NfcScanPrompt()
    }
}
