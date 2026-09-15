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

package org.sprind.wallet.walletpinfeature.ui.document.pinset.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.CancellableTopAppBar
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import org.sprind.wallet.uilogic.component.IssuanceJourneyProgress
import org.sprind.wallet.walletpinfeature.ui.document.pinset.WalletPinStep
import org.sprind.wallet.walletpinfeature.ui.document.pinset.journeyStep
import org.sprind.wallet.walletpinfeature.ui.document.pinset.journeyStepCompleted

/**
 * The header the wallet code screens share: the flow's actions over the journey [step] sits in.
 *
 * [ContentScreen] swaps only its body for the loading indicator and keeps the top bar, so a busy
 * screen has to strip its own header: [isBusy] drops both the actions, which must not offer a way
 * to interrupt a half-finished registration, and the journey, which would otherwise be the one thing
 * left on an otherwise empty screen.
 */
@Composable
fun WalletCodeJourneyHeader(
    step: WalletPinStep,
    onCloseClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    isBusy: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CancellableTopAppBar(
            onBackClick = onBackClick.takeIf { !isBusy },
            onCloseClick = onCloseClick.takeIf { !isBusy },
        )
        step.journeyStep?.takeIf { !isBusy }?.let { journeyStep ->
            IssuanceJourneyProgress(
                modifier = Modifier.padding(bottom = SPACING_SMALL.dp),
                step = journeyStep,
                stepCompleted = step.journeyStepCompleted,
            )
        }
    }
}