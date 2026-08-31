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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.commonfeature.ui.success.SuccessView
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.wrap.WrapImage
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute
import org.sprind.wallet.cardreaderfeature.domain.isNfcPrompt
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.CanBottomSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.CardPinLetterInfoSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.EidFunctionInfoSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.NewPinInfoSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.NoKnownPinFindTownHallBottomSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.can.CanSuccessView
import org.sprind.wallet.cardreaderfeature.ui.document.can.EidPinBlockedView
import org.sprind.wallet.cardreaderfeature.ui.document.consent.ConsentView
import org.sprind.wallet.cardreaderfeature.ui.document.newpin.NewPinSetInfoView
import org.sprind.wallet.cardreaderfeature.ui.document.nfc.CardScanConfig
import org.sprind.wallet.cardreaderfeature.ui.document.nfc.CardScanView
import org.sprind.wallet.cardreaderfeature.ui.document.nfc.NfcActivationView
import org.sprind.wallet.cardreaderfeature.ui.document.nfc.NfcScanPromptView
import org.sprind.wallet.cardreaderfeature.ui.document.nfc.NfcScanReason
import org.sprind.wallet.cardreaderfeature.ui.document.onboarding.NoPinLetterInfoView
import org.sprind.wallet.cardreaderfeature.ui.document.onboarding.OnboardingCardView
import org.sprind.wallet.cardreaderfeature.ui.document.onboarding.OnboardingPinView
import org.sprind.wallet.cardreaderfeature.ui.document.onboarding.ProgressStepsView
import org.sprind.wallet.cardreaderfeature.ui.document.read.Event.BottomSheet.UpdateBottomSheetState
import org.sprind.wallet.cardreaderfeature.ui.document.read.Event.OnCanUpdate
import org.sprind.wallet.cardreaderfeature.ui.document.read.Event.OnNewPinConfirmUpdate
import org.sprind.wallet.cardreaderfeature.ui.document.read.Event.OnNewPinUpdate
import org.sprind.wallet.cardreaderfeature.ui.document.read.Event.OnPinUpdate
import org.sprind.wallet.cardreaderfeature.ui.document.read.Event.OnTransportPinUpdate
import org.sprind.wallet.cardreaderfeature.ui.document.transport.TransportPinLetterContent
import org.sprind.wallet.uilogic.component.CodeEntryBody
import java.util.Locale

@Composable
internal fun CardReaderRouteScreen(
    route: CardReaderRoute,
    state: State,
    locale: Locale,
    bottomSheetState: SheetState,
    onEventSend: (Event) -> Unit,
) {
    if (route.isNfcPrompt) {
        state.scanStatus?.let { scanStatus ->
            CardScanView(
                modifier = Modifier.fillMaxSize(),
                config = CardScanConfig(
                    antennaPosition = state.nfcAntennaPosition,
                    status = scanStatus,
                    scanReason = route.toNfcScanReason(),
                    readingProgress = state.readingProgress,
                ),
                onCancelClick = { onEventSend(Event.OnCancelScanClick) },
                onContinueClick = { onEventSend(Event.OnScanContinueClick) },
            )
            return
        }

        NfcScanPromptView(
            modifier = Modifier.fillMaxSize(),
            nfcScanReason = route.toNfcScanReason(),
            bottomCardSheetConfig = state.bottomSheetConfig(bottomSheetState, onEventSend),
            onCustomerServiceCallButtonClick = {
                onEventSend(Event.OnCustomerServiceCallButtonClick)
            }
        )
        return
    }

    when (route) {
        CardReaderRoute.ENTER_PIN -> {
            CodeEntryBody(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.pid_issuance_card_pin_entry_title),
                state = state.pinState,
                onCodeChange = { onEventSend(OnPinUpdate) },
            )
            val sheetConfig = state.bottomSheetConfig(bottomSheetState, onEventSend)
            ReadCardBottomSheet(sheetConfig) {
                CardPinLetterInfoSheetContent(
                    onSetCardPinClick = { onEventSend(Event.TransportPinLetter) },
                    onCloseClick = sheetConfig.onBottomSheetDismissRequest,
                )
            }
        }

        CardReaderRoute.PIN_BLOCKED_ERROR -> EidPinBlockedView(
            modifier = Modifier.fillMaxSize(),
            bottomCardSheetConfig = state.bottomSheetConfig(
                sheetState = bottomSheetState,
                onEventSend = onEventSend,
                title = ""
            )
        )

        CardReaderRoute.ENTER_CAN -> {
            CodeEntryBody(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.pid_issuance_can_entry_title),
                state = state.pinState,
                onCodeChange = { onEventSend(OnCanUpdate) },
                // Shows where the CAN is printed on the card, so it is instruction, not decoration:
                // it stays in the accessibility tree and its description says where to look.
                illustration = { WrapImage(iconData = AppIcons.EidCanShow) },
            )
            ReadCardBottomSheet(state.bottomSheetConfig(bottomSheetState, onEventSend)) {
                CanBottomSheetContent()
            }
        }

        CardReaderRoute.ENTER_CAN_SUCCESS -> CanSuccessView(
            modifier = Modifier.fillMaxSize(),
            bottomCardSheetConfig = state.bottomSheetConfig(
                sheetState = bottomSheetState,
                onEventSend = onEventSend,
                title = ""
            ),
            onSetCardPinButtonClick = { onEventSend(Event.TransportPinLetter) },
            onSearchCitizenOfficeButtonClick = {
                onEventSend(Event.OnSearchCitizenOfficeButtonClick(locale))
            }
        )

        CardReaderRoute.COMPLETED -> SuccessView(
            title = stringResource(R.string.nfc_scanning_success_error_success_card_pin_title_android),
            modifier = Modifier.fillMaxSize()
        )

        CardReaderRoute.ENTER_PUK -> Unit // TODO add a dedicated PUK entry screen once the SDK branch is modeled.

        CardReaderRoute.ENTER_TRANSPORT_PIN -> {
            CodeEntryBody(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.eid_setup_transport_pin_entry_title),
                state = state.pinState,
                onCodeChange = { onEventSend(OnTransportPinUpdate) },
                // Shows the PIN letter the transport PIN is read from, so it is instruction too and
                // keeps its description rather than being hidden.
                illustration = {
                    WrapImage(
                        iconData = AppIcons.LetterHighlightingTransportPin,
                        modifier = Modifier.height(TRANSPORT_PIN_LETTER_HEIGHT),
                        contentScale = ContentScale.FillHeight,
                    )
                },
            )
            ReadCardBottomSheet(state.bottomSheetConfig(bottomSheetState, onEventSend)) {
                NoKnownPinFindTownHallBottomSheetContent(
                    onSecondaryButtonClick = {
                        onEventSend(Event.OnSearchCitizenOfficeButtonClick(locale))
                    },
                )
            }
        }

        CardReaderRoute.NFC_ACTIVATION -> NfcActivationView(
            modifier = Modifier.fillMaxSize()
        )

        CardReaderRoute.ENTER_NEW_PIN_INSTRUCTIONS -> NewPinSetInfoView(
            modifier = Modifier.fillMaxSize(),
            bottomCardSheetConfig = state.bottomSheetConfig(bottomSheetState, onEventSend)
        )

        CardReaderRoute.ENTER_NEW_PIN -> {
            CodeEntryBody(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.eid_setup_card_pin_setup_title),
                state = state.pinState,
                onCodeChange = { onEventSend(OnNewPinUpdate) },
            )
            val sheetConfig = state.bottomSheetConfig(bottomSheetState, onEventSend)
            ReadCardBottomSheet(sheetConfig) {
                NewPinInfoSheetContent(title = sheetConfig.title)
            }
        }

        CardReaderRoute.CONFIRM_NEW_PIN -> {
            CodeEntryBody(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.eid_setup_card_pin_reenter_title),
                state = state.pinState,
                onCodeChange = { onEventSend(OnNewPinConfirmUpdate) },
            )
            val sheetConfig = state.bottomSheetConfig(bottomSheetState, onEventSend)
            ReadCardBottomSheet(sheetConfig) {
                NewPinInfoSheetContent(title = sheetConfig.title)
            }
        }

        CardReaderRoute.ONBOARDING_CARD -> {
            OnboardingCardView(
                modifier = Modifier.fillMaxSize()
            )
            val sheetConfig = state.bottomSheetConfig(bottomSheetState, onEventSend)
            ReadCardBottomSheet(sheetConfig) {
                EidFunctionInfoSheetContent(
                    onCloseClick = sheetConfig.onBottomSheetDismissRequest,
                )
            }
        }

        CardReaderRoute.ONBOARDING_PIN -> {
            OnboardingPinView(
                modifier = Modifier.fillMaxSize()
            )
            val sheetConfig = state.bottomSheetConfig(bottomSheetState, onEventSend)
            ReadCardBottomSheet(sheetConfig) {
                CardPinLetterInfoSheetContent(
                    onSetCardPinClick = { onEventSend(Event.TransportPinLetter) },
                    onCloseClick = sheetConfig.onBottomSheetDismissRequest,
                )
            }
        }

        CardReaderRoute.PROGRESS_STEPS -> ProgressStepsView(
            modifier = Modifier.fillMaxSize()
        )

        CardReaderRoute.NO_PIN_LETTER_INFO -> NoPinLetterInfoView(
            modifier = Modifier.fillMaxSize()
        )

        CardReaderRoute.TRANSPORT_PIN_LETTER -> TransportPinLetterContent(
            modifier = Modifier.fillMaxSize(),
            bottomCardSheetConfig = state.bottomSheetConfig(bottomSheetState, onEventSend),
            onSearchCitizenOfficeButtonClick = {
                onEventSend(Event.OnSearchCitizenOfficeButtonClick(locale))
            }
        )

        CardReaderRoute.CONSENT -> ConsentView(
            modifier = Modifier.fillMaxSize(),
            claimLabels = pidClaimLabels(),
            bottomCardSheetConfig = state.bottomSheetConfig(bottomSheetState, onEventSend),
            onSetCardPinButtonClick = { onEventSend(Event.TransportPinLetter) },
            onSearchCitizenOfficeButtonClick = {
                onEventSend(Event.OnSearchCitizenOfficeButtonClick(locale))
            },
            onIssuerTitleClick = { onEventSend(Event.OnIssuerInformationClick) }
        )

        else -> error("Unhandled non-NFC card reader route: $route")
    }
}

@Composable
private fun pidClaimLabels(): List<String> = listOf(
    stringResource(R.string.pid_issuance_data_consent_label_name),
    stringResource(R.string.pid_issuance_data_consent_label_birth_name),
    stringResource(R.string.pid_issuance_data_consent_label_first_names),
    stringResource(R.string.pid_issuance_data_consent_label_title),
    stringResource(R.string.pid_issuance_data_consent_label_artist_name),
    stringResource(R.string.pid_issuance_data_consent_label_address),
    stringResource(R.string.pid_issuance_data_consent_label_nationality),
    stringResource(R.string.pid_issuance_data_consent_label_birth_date),
    stringResource(R.string.pid_issuance_data_consent_label_place_of_birth),
    stringResource(R.string.pid_issuance_data_consent_label_document_type),
    stringResource(R.string.pid_presentation_data_consent_label_issuing_country),
    stringResource(R.string.pid_issuance_data_consent_label_expire_date),
)

private val TRANSPORT_PIN_LETTER_HEIGHT = 94.dp

internal fun State.bottomSheetConfig(
    sheetState: SheetState,
    onEventSend: (Event) -> Unit,
    title: String = bottomSheetTitle.orEmpty(),
): ReadCardBottomSheetConfig = ReadCardBottomSheetConfig(
    title = title,
    sheetState = sheetState,
    isBottomSheetOpen = isBottomSheetOpen,
    onBottomSheetDismissRequest = {
        onEventSend(UpdateBottomSheetState(isOpen = false))
    }
)

internal fun CardReaderRoute.toNfcScanReason(): NfcScanReason = when (this) {
    CardReaderRoute.NFC_SCAN_CAN -> NfcScanReason.CAN
    CardReaderRoute.NFC_SCAN_EID_PIN -> NfcScanReason.EID_PIN
    CardReaderRoute.NFC_SCAN_PUK -> NfcScanReason.PUK
    CardReaderRoute.NFC_SCAN_TRANSPORT_PIN -> NfcScanReason.TRANSPORT_PIN
    CardReaderRoute.NFC_SCAN_NEW_PIN -> NfcScanReason.EID_NEW_PIN_SET
    else -> error("Route $this is not an NFC prompt route.")
}
