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

@file:OptIn(ExperimentalMaterial3Api::class)

package org.sprind.wallet.cardreaderfeature.ui.document.read

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import org.sprind.wallet.uilogic.component.CodeLength
import org.sprind.wallet.uilogic.component.codeEntryStateForPreview
import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowType

@Composable
private fun PreviewCardReaderRoute(
    state: State,
) {
    PreviewTheme {
        CardReaderRouteHost(
            state = state,
            onEventSend = {}
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun OnboardingCardPreview() {
    PreviewCardReaderRoute(
        state = State().withStep(ReadCardScreenStep.OnboardingCard)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun OnboardingPinPreview() {
    PreviewCardReaderRoute(
        state = State(
            bottomSheetTitle = stringResource(R.string.pid_issuance_card_pin_entry_title)
        ).withStep(ReadCardScreenStep.OnboardingPin)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ProgressStepsPreview() {
    PreviewCardReaderRoute(
        state = State().withStep(ReadCardScreenStep.ProgressSteps)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NoPinLetterInfoPreview() {
    PreviewCardReaderRoute(
        state = State().withStep(ReadCardScreenStep.NoPinLetterInfo)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CancelFlowDialogPreview() {
    PreviewCardReaderRoute(
        state = State(isCancelFlowDialogVisible = true)
            .withStep(ReadCardScreenStep.OnboardingPin)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun EnterCanPreview() {
    PreviewCardReaderRoute(
        state = State(
            bottomSheetTitle = stringResource(R.string.pid_issuance_sheet_can_title)
        ).withStep(ReadCardScreenStep.EnterCan)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun EnterCanWithErrorPreview() {
    PreviewCardReaderRoute(
        state = State(
            bottomSheetTitle = stringResource(R.string.pid_issuance_sheet_can_title),
            pinState = codeEntryStateForPreview(
                capacity = CodeLength.CAN,
                supportingText = stringResource(R.string.pid_issuance_can_entry_error_wrong_can),
            )
        ).withStep(ReadCardScreenStep.EnterCan)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun EnterPinPreview() {
    PreviewCardReaderRoute(
        state = State(
            bottomSheetTitle = stringResource(R.string.pid_issuance_card_pin_entry_title)
        ).withStep(ReadCardScreenStep.EnterPin)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PinBlockedPreview() {
    PreviewCardReaderRoute(
        state = State(
            bottomSheetTitle = stringResource(R.string.pid_issuance_sheet_can_title)
        ).withStep(ReadCardScreenStep.PinBlockedError)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NfcCanPreview() {
    PreviewCardReaderRoute(
        state = State(
            bottomSheetTitle = stringResource(R.string.pid_issuance_sheet_can_title)
        ).withStep(ReadCardScreenStep.NfcScanPrompt.Can)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NfcActivationPreview() {
    PreviewCardReaderRoute(
        state = State(isNfcEnabled = false).withStep(ReadCardScreenStep.NfcActivation)
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NewPinInstructionsPreview() {
    PreviewCardReaderRoute(
        state = State(
            activeFlowType = CardReaderFlowType.CHANGE_PIN,
            flowDefinition = flowDefinitionFor(CardReaderFlowType.CHANGE_PIN)
        ).withStep(
            step = ReadCardScreenStep.EnterNewPinInstructions,
            flowType = CardReaderFlowType.CHANGE_PIN
        )
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CompletedPreview() {
    PreviewCardReaderRoute(
        state = State().withStep(ReadCardScreenStep.Completed)
    )
}
