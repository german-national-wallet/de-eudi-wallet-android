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

package org.sprind.wallet.walletpinfeature.ui.document.pinset

import org.sprind.wallet.uilogic.component.IssuanceJourneyStep

/**
 * The announced step this wallet code step belongs to, or `null` for the ones outside the journey.
 *
 * The counterpart of `CardReaderRoute.journeyStep`: between them the two features map the whole
 * issuance journey onto [IssuanceJourneyStep], so the header counts the same four steps wherever the
 * flow happens to be.
 *
 * Adding a wallet code step to the header is adding it here.
 */
val WalletPinStep.journeyStep: IssuanceJourneyStep?
    get() = when (this) {
        WalletPinStep.Info,
        WalletPinStep.Set,
        WalletPinStep.Confirm,
        -> IssuanceJourneyStep.CREDENTIAL

        // The success screen is the end of the journey and its design drops the header entirely,
        // so there is nothing left to count off.
        WalletPinStep.Success -> null
    }

/**
 * Whether the step's [journeyStep] is finished rather than being worked through.
 *
 * Always true here: the credential was accepted on the consent screen before the flow ever reached
 * the wallet code, so the last announced step is already behind the user by this point.
 */
val WalletPinStep.journeyStepCompleted: Boolean
    get() = journeyStep != null