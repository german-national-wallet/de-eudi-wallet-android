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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyButtonAction
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomColumn
import eu.europa.ec.uilogic.component.wrap.WrapStickyButton
import eu.europa.ec.uilogic.component.wrap.WrapStickyPrimaryButton
import eu.europa.ec.uilogic.component.wrap.WrapStickySecondaryButton
import eu.europa.ec.uilogic.component.wrap.WrapStickyStackedButtons
import eu.europa.ec.uilogic.component.wrap.WrapStickyTwoButtons
import eu.europa.ec.uilogic.component.wrap.stickyBottomInsets
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute
import org.sprind.wallet.cardreaderfeature.domain.isNfcPrompt
import org.sprind.wallet.cardreaderfeature.ui.document.read.Event.BottomSheet.UpdateBottomSheetState
import org.sprind.wallet.uilogic.component.ButtonNavigationBottom
import org.sprind.wallet.uilogic.component.NavigationCardButton
import org.sprind.wallet.uilogic.component.NavigationTopAction
import java.util.Locale

/**
 * The bottom bar of the card reader flow, picked from the route the flow currently sits on.
 *
 * Every branch is one of the shared `WrapSticky*` bottom bars, so the buttons keep the same width,
 * insets and label metrics from route to route; the two routes that answer a question with a list of
 * choices use [ButtonNavigationBottom] with the same [stickyBottomInsets] instead.
 *
 * @param padding the [eu.europa.ec.uilogic.component.content.ContentScreen] sticky-bottom padding.
 * @param state the flow state; its route decides which bar is shown.
 * @param locale used by the routes that open a locale-dependent web page.
 * @param onEventSend forwards the pressed action to the view model.
 */
@Composable
internal fun CardReaderStickyButtons(
    padding: PaddingValues,
    state: State,
    locale: Locale,
    onEventSend: (Event) -> Unit,
) {
    // While the card is being read the screen speaks for itself; no action is offered.
    if (state.scanStatus != null) return

    if (state.currentRoute.isNfcPrompt) {
        WrapStickyStackedButtons(
            primaryText = stringResource(R.string.nfc_scanning_nfc_tap_prim_button),
            secondaryText = stringResource(R.string.nfc_scanning_nfc_tap_sec_button),
            paddingValues = padding,
            onPrimaryClick = { onEventSend(Event.OnStartScanningClick) },
            onSecondaryClick = { onEventSend(UpdateBottomSheetState(isOpen = true)) }
        )
        return
    }

    when (state.currentRoute) {
        CardReaderRoute.ENTER_PIN -> {
            WrapStickyStackedButtons(
                primaryText = stringResource(R.string.pid_issuance_card_pin_entry_prim_button),
                secondaryText = stringResource(R.string.pid_issuance_card_pin_entry_sec_button),
                paddingValues = padding,
                primaryEnabled = state.pinState.buffer.isComplete,
                primaryTrailingIcon = AppIcons.ArrowRightLong,
                secondaryLeadingIcon = AppIcons.Help,
                onPrimaryClick = { onEventSend(Event.OnContinueClickCardPin) },
                onSecondaryClick = { onEventSend(UpdateBottomSheetState(isOpen = true)) }
            )
        }

        CardReaderRoute.ONBOARDING_PIN -> {
            ButtonNavigationBottom(
                modifier = Modifier.stickyBottomInsets(padding),
                topAction = NavigationTopAction(
                    text = stringResource(R.string.pid_issuance_onboarding_eid_tert_button_2),
                    icon = AppIcons.Help,
                    onClick = { onEventSend(UpdateBottomSheetState(isOpen = true)) },
                ),
                buttons = listOf(
                    NavigationCardButton(
                        text = stringResource(R.string.pid_issuance_onboarding_eid_prim_button),
                        onClick = { onEventSend(Event.OnContinueClickProgressSteps) }
                    ),
                    NavigationCardButton(
                        text = stringResource(R.string.pid_issuance_onboarding_eid_sec_button),
                        onClick = { onEventSend(Event.TransportPinLetter) }
                    ),
                    NavigationCardButton(
                        text = stringResource(R.string.pid_issuance_onboarding_eid_tert_button_1),
                        onClick = { onEventSend(Event.OnNoPinLetterButtonClick) }
                    ),
                ),
            )
        }

        CardReaderRoute.PROGRESS_STEPS -> {
            WrapStickyBottomColumn(paddingValues = padding) {
                WrapStickyButton(
                    action = StickyButtonAction(
                        text = stringResource(R.string.pid_issuance_process_overview_tert_button),
                        onClick = { onEventSend(Event.OnPrivacyPolicyButtonClick) },
                        leadingIcon = AppIcons.Info,
                    ),
                    type = ButtonType.TEXT,
                )
                WrapStickyButton(
                    action = StickyButtonAction(
                        text = stringResource(R.string.pid_issuance_process_overview_prim_button),
                        onClick = { onEventSend(Event.Consent) },
                        trailingIcon = AppIcons.ArrowRightLong,
                    ),
                    type = ButtonType.PRIMARY,
                )
            }
        }

        CardReaderRoute.NO_PIN_LETTER_INFO -> {
            WrapStickyPrimaryButton(
                text = stringResource(R.string.global_office_button),
                enabled = true,
                paddingValues = padding,
                trailingIcon = AppIcons.ArrowOutward,
                onClick = { onEventSend(Event.OnSearchCitizenOfficeButtonClick(locale)) }
            )
        }

        CardReaderRoute.ONBOARDING_CARD -> {
            ButtonNavigationBottom(
                modifier = Modifier.stickyBottomInsets(padding),
                topAction = NavigationTopAction(
                    text = stringResource(R.string.pid_issuance_onboarding_cards_tert_button),
                    icon = AppIcons.EidLogo,
                    tintIcon = false,
                ),
                buttons = listOf(
                    NavigationCardButton(
                        text = stringResource(R.string.pid_issuance_onboarding_cards_prim_button),
                        onClick = { onEventSend(Event.OnContinueClickOnboardingPin) }
                    ),
                    NavigationCardButton(
                        text = stringResource(R.string.pid_issuance_onboarding_cards_sec_button),
                        onClick = { onEventSend(Event.Close) }
                    ),
                ),
            )
        }

        CardReaderRoute.PIN_BLOCKED_ERROR -> {
            WrapStickyStackedButtons(
                primaryText = stringResource(R.string.pid_issuance_can_intro_prim_button),
                secondaryText = stringResource(R.string.pid_issuance_can_entry_sec_button),
                paddingValues = padding,
                primaryTrailingIcon = AppIcons.ArrowRightLong,
                onPrimaryClick = { onEventSend(Event.OnEnterCanButtonPress) },
                onSecondaryClick = { onEventSend(UpdateBottomSheetState(isOpen = true)) }
            )
        }

        CardReaderRoute.ENTER_NEW_PIN_INSTRUCTIONS -> {
            WrapStickyPrimaryButton(
                text = stringResource(R.string.eid_setup_card_pin_intro_prim_button),
                enabled = true,
                paddingValues = padding,
                trailingIcon = AppIcons.ArrowRightLong,
                onClick = { onEventSend(Event.OnSetNewPinPrimaryButtonClick) }
            )
        }

        CardReaderRoute.ENTER_NEW_PIN -> {
            WrapStickyPrimaryButton(
                text = stringResource(R.string.pid_issuance_wallet_pin_setup_prim_button),
                enabled = state.canContinue,
                paddingValues = padding,
                trailingIcon = AppIcons.ArrowRightLong,
                onClick = { onEventSend(Event.OnContinueClickOnSetNewPin) }
            )
        }

        CardReaderRoute.CONFIRM_NEW_PIN -> {
            WrapStickyPrimaryButton(
                text = stringResource(R.string.eid_setup_card_pin_reenter_prim_button),
                enabled = state.canContinue,
                paddingValues = padding,
                trailingIcon = AppIcons.ArrowRightLong,
                onClick = { onEventSend(Event.OnContinueClickOnConfirmNewPin) }
            )
        }

        CardReaderRoute.ENTER_TRANSPORT_PIN -> {
            WrapStickySecondaryButton(
                text = stringResource(R.string.eid_setup_transport_pin_intro_sec_button),
                paddingValues = padding,
                onClick = { onEventSend(UpdateBottomSheetState(isOpen = true)) }
            )
        }

        CardReaderRoute.TRANSPORT_PIN_LETTER -> {
            WrapStickyStackedButtons(
                primaryText = stringResource(R.string.eid_setup_transport_pin_intro_prim_button),
                secondaryText = stringResource(R.string.eid_setup_transport_pin_intro_sec_button),
                paddingValues = padding,
                onPrimaryClick = { onEventSend(Event.StartTransportPin) },
                onSecondaryClick = { onEventSend(UpdateBottomSheetState(isOpen = true)) }
            )
        }

        CardReaderRoute.CONSENT -> {
            WrapStickyTwoButtons(
                secondaryText = stringResource(R.string.pid_issuance_data_consent_sec_button),
                primaryText = stringResource(R.string.pid_issuance_data_consent_prim_button),
                paddingValues = padding,
                onSecondaryClick = { onEventSend(Event.OnCloseButtonClick) },
                onPrimaryClick = { onEventSend(Event.AcceptRightsAndEnterPin) }
            )
        }

        CardReaderRoute.NFC_ACTIVATION -> {
            WrapStickyPrimaryButton(
                text = stringResource(R.string.nfc_scanning_nfc_not_activated_prim_button),
                enabled = true,
                paddingValues = padding,
                onClick = { onEventSend(Event.OnEnableNfcButtonClick) }
            )
        }

        CardReaderRoute.ENTER_CAN_SUCCESS -> {
            WrapStickyStackedButtons(
                primaryText = stringResource(R.string.pid_issuance_can_success_prim_button),
                secondaryText = stringResource(R.string.pid_issuance_can_success_sec_button),
                paddingValues = padding,
                onPrimaryClick = { onEventSend(Event.AcceptRightsAndEnterPin) },
                onSecondaryClick = { onEventSend(UpdateBottomSheetState(isOpen = true)) }
            )
        }

        else -> {
            state.bottomSheetTitle?.let { title ->
                WrapStickySecondaryButton(
                    text = title,
                    paddingValues = padding,
                    leadingIcon = AppIcons.Info,
                    onClick = { onEventSend(UpdateBottomSheetState(isOpen = true)) }
                )
            }
        }
    }
}