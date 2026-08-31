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

import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import eu.europa.ec.businesslogic.BuildConfig
import eu.europa.ec.commonfeature.ui.issuer_details.model.IssuerInfo
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.sprind.wallet.uilogic.component.CodeEntryBuffer
import org.sprind.wallet.uilogic.component.CodeEntryState
import org.sprind.wallet.uilogic.component.CodeLength
import org.sprind.wallet.businesslogic.config.EidCardType
import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowDefinition
import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowType
import org.sprind.wallet.cardreaderfeature.domain.CardReaderProgress
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute
import org.sprind.wallet.cardreaderfeature.domain.CardScanStatus
import org.sprind.wallet.cardreaderfeature.domain.NfcAntennaPosition
import java.util.Locale

data class CardReaderReturnTarget(
    val route: CardReaderRoute,
    val flowType: CardReaderFlowType,
)

data class CardReaderCodeRetry(
    val step: ReadCardScreenStep,
    val supportingText: String,
)

data class State(
    val currentStep: ReadCardScreenStep = ReadCardScreenStep.OnboardingCard,
    // Route metadata is stored directly on state so toolbar, progress, and
    // navigation helpers can derive behavior without duplicating flow lookups.
    val activeFlowType: CardReaderFlowType = CardReaderFlowType.ISSUANCE,
    val flowDefinition: CardReaderFlowDefinition = flowDefinitionFor(CardReaderFlowType.ISSUANCE),
    val currentRoute: CardReaderRoute = ReadCardScreenStep.OnboardingCard.toRoute(),
    val progress: CardReaderProgress = progressFor(
        flowType = CardReaderFlowType.ISSUANCE,
        route = ReadCardScreenStep.OnboardingCard.toRoute(),
    ),
    val onBackAction: (() -> Unit)? = null,
    val isLoading: Boolean = false,
    // Percentage (0-100) of the current card read, shown under the loader so the
    // user can see progress and notice if a read stalls. Null when not reading.
    val readingProgress: Int? = null,
    val error: ContentErrorConfig? = null,
    val errorDialog: GenericErrorDialogConfig? = null,
    val isInitialised: Boolean = false,
    val isPinError: Boolean = false,
    val isBottomSheetOpen: Boolean = false,
    // Closing the flow throws away everything the user has entered so far, so the toolbar's X asks
    // before it happens and this says whether that question is on screen.
    val isCancelFlowDialogVisible: Boolean = false,
    val bottomSheetTitle: String? = null,
    val pinState: CodeEntryState = CodeEntryState(CodeEntryBuffer(CodeLength.EID_PIN)),
    val canContinue: Boolean = false,
    val returnTarget: CardReaderReturnTarget? = null,
    val onSimulator: Boolean = BuildConfig.IS_SIMULATOR.toBoolean(),
    val eidCardType: EidCardType = EidCardType.PHYSICAL,
    // Last reported platform NFC state; refreshed by the screen on every resume.
    // Defaults to enabled so the flow is never gated before the first report.
    val isNfcEnabled: Boolean = true,
    val nfcAntennaPosition: NfcAntennaPosition = NfcAntennaPosition.MIDDLE,
    val scanStatus: CardScanStatus? = null,
    val codeRetry: CardReaderCodeRetry? = null,
) : ViewState {
    /**
     * Updates the active step and refreshes the route-derived metadata that the
     * UI and navigation helpers read from state.
     */
    fun withStep(
        step: ReadCardScreenStep,
        flowType: CardReaderFlowType = activeFlowType,
    ): State {
        val route = step.toRoute()
        val definition = flowDefinitionFor(flowType)
        return copy(
            currentStep = step,
            activeFlowType = flowType,
            flowDefinition = definition,
            currentRoute = route,
            progress = definition.progressFor(route),
        )
    }
}

data class ReadCardBottomSheetConfig(
    val title: String,
    val sheetState: SheetState,
    val isBottomSheetOpen: Boolean,
    val onBottomSheetDismissRequest: () -> Unit,
)

sealed class Event : ViewEvent {
    data object Init : Event()
    data object AcceptRightsAndEnterPin : Event()
    data object Pop : Event()
    data object Finish : Event()
    data object DismissError : Event()
    data object OnPause : Event()
    data object Pin : Event()
    data object NewPin : Event()
    data object Can : Event()

    /**
     * Reported by the screen on every resume, carrying the platform NFC state so the
     * flow can gate the card PIN step on NFC being switched on and continue by
     * itself once the user comes back from the system settings with it enabled.
     *
     * It carries the antenna position with it: both are device state the screen reads off the
     * platform, and the position decides which tapping animation the scan plays.
     */
    data class OnResume(
        val isNfcEnabled: Boolean,
        val nfcAntennaPosition: NfcAntennaPosition = NfcAntennaPosition.MIDDLE,
    ) : Event()
    sealed class BottomSheet : Event() {
        data class UpdateBottomSheetState(val isOpen: Boolean) : BottomSheet()
    }

    data object TransportPinLetter : Event()
    data object StartTransportPin : Event()
    data object OnEnterCanButtonPress : Event()
    data object OnTransportPinUpdate : Event()
    data object OnPinUpdate : Event()
    data object OnCanUpdate : Event()
    data object OnNewPinUpdate : Event()
    data object OnNewPinConfirmUpdate : Event()

    /**
     * The card PIN question was answered with neither a PIN letter at hand nor a remembered PIN, so
     * the flow can only point the user to a citizen's office.
     */
    data object OnNoPinLetterButtonClick : Event()
    data class OnSearchCitizenOfficeButtonClick(val locale: Locale) : Event()
    data object OnCustomerServiceCallButtonClick : Event()
    /**
     * The scan was asked for. NFC is only needed from here on, so this is where the flow checks it
     * and sends the user to the system settings when it is off.
     */
    data object OnStartScanningClick : Event()

    /**
     * The running scan was given up on. It is the only way off the scan screen, since the read has
     * to be able to take its time without the screen changing under the user's hands.
     */
    data object OnCancelScanClick : Event()

    /** The finished scan was acknowledged, which is what carries the flow to its next screen. */
    data object OnScanContinueClick : Event()

    data object OnEnableNfcButtonClick : Event()
    data object OnSetNewPinPrimaryButtonClick : Event()
    data object OnContinueClickOnSetNewPin : Event()
    data object OnContinueClickOnConfirmNewPin : Event()
    data object OnContinueClickOnboardingPin : Event()
    data object OnContinueClickProgressSteps : Event()
    data object OnContinueClickCardPin : Event()
    data object OnPrivacyPolicyButtonClick : Event()
    /**
     * The toolbar's X was pressed. It asks before leaving, so the flow is only cancelled once the
     * question is answered with [Close].
     */
    data object OnCloseButtonClick : Event()

    /** Keeps the flow where it is and takes the cancel question off screen. */
    data object DismissCancelFlowDialog : Event()

    data object Close : Event()
    data object OnIssuerInformationClick : Event()
    data object Consent : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class Pop(
            val screenRoute: String,
            val inclusive: Boolean,
        ) : Navigation()

        data object Finish : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val inclusive: Boolean,
            val redirectUrl: String,
        ) : Navigation()

        data class NavigateToIssuerDetails(val details: IssuerInfo) : Navigation()
        data class NavigateToPrivacyPolicy(val url: String) : Navigation()
        data class OpenLink(val uri: Uri) : Navigation()
    }

    data object HideKeyboard : Effect()
    data object ShowKeyboard : Effect()
    data class StartCall(val phoneNumber: String) : Effect()

    /**
     * Re-arm NFC reader mode so a card already resting on the sensor is
     * re-discovered and read without requiring the user to lift and re-tap.
     */
    data object RestartNfcReader : Effect()

    /**
     * Open the system NFC settings so the user can switch NFC on. Turning it on is
     * a device-level setting, so the app can only take the user there; the flow
     * continues once it comes back to the foreground with NFC enabled.
     */
    data object OpenNfcSettings : Effect()
}

/**
 * Renders [content] as the card reader's modal bottom sheet when [config] says it is open.
 *
 * Wraps the open-check plus [WrapModalBottomSheet] wiring that every card-reader route otherwise
 * repeats verbatim.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadCardBottomSheet(
    config: ReadCardBottomSheetConfig,
    content: @Composable () -> Unit,
) {
    if (!config.isBottomSheetOpen) return

    WrapModalBottomSheet(
        onDismissRequest = config.onBottomSheetDismissRequest,
        sheetState = config.sheetState,
    ) {
        content()
    }
}
