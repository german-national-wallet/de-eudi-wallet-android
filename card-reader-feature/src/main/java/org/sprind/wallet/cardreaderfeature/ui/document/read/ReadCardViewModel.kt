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

import android.nfc.Tag
import androidx.annotation.RestrictTo
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import eu.europa.ec.businesslogic.config.ConfigLogic
import org.sprind.wallet.uilogic.component.CodeEntryBuffer
import org.sprind.wallet.uilogic.component.CodeLength
import org.sprind.wallet.uilogic.component.cleared
import org.sprind.wallet.businesslogic.config.UserRuntimeConfig
import org.sprind.wallet.businesslogic.config.isVirtual
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.commonfeature.interactor.IssuanceEvent
import eu.europa.ec.commonfeature.ui.issuer_details.model.IssuerInfo
import eu.europa.ec.corelogic.controller.AttestationState
import eu.europa.ec.corelogic.controller.IssuanceMethod
import eu.europa.ec.corelogic.handler.reader.CardReaderInteractor
import eu.europa.ec.corelogic.handler.reader.WorkflowEvent
import eu.europa.ec.corelogic.interactor.walletattestation.WalletAttestationInteractor
import eu.europa.ec.corelogic.interactor.walletattestation.WalletAttestationResult
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import org.sprind.wallet.cardreaderfeature.domain.CardReaderBackBehavior
import org.sprind.wallet.cardreaderfeature.domain.CardReaderCloseBehavior
import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowType
import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowNavigator
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute
import org.sprind.wallet.cardreaderfeature.domain.CardScanStatus
import org.sprind.wallet.cardreaderfeature.domain.NfcAntennaPosition
import org.sprind.wallet.cardreaderfeature.domain.isNfcPrompt
import org.sprind.wallet.cardreaderfeature.ui.document.privacy.PrivacyPolicyRoute
import org.sprind.wallet.cardreaderfeature.ui.document.read.Effect.Navigation.NavigateToIssuerDetails
import org.sprind.wallet.cardreaderfeature.ui.document.read.Effect.Navigation.OpenLink
import org.sprind.wallet.cardreaderfeature.ui.document.read.Effect.Navigation.Pop
import org.sprind.wallet.cardreaderfeature.ui.document.read.Effect.StartCall
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@KoinViewModel
class ReadCardViewModel(
    private val configLogic: ConfigLogic,
    private val resourceProvider: ResourceProvider,
    private val cardReaderInteractor: CardReaderInteractor,
    private val walletAttestationInteractor: WalletAttestationInteractor,
    private val addDocumentsInteractor: AddDocumentInteractor,
    private val logController: LogController,
    private val telemetry: Telemetry,
    private val userRuntimeConfig: UserRuntimeConfig,
    @InjectedParam private val flowType: IssuanceFlowUiConfig,
    @InjectedParam private val credentialTypes: Set<CredentialConfigurationIdentifier>,
) : MviViewModel<Event, State, Effect>() {
    private val logTag = javaClass.simpleName

    private val citizenOfficeUrl = "https://servicesuche.bund.de".toUri()

    // TODO WD-4131: the issuance policy page is still to be published; until then the screen opens
    // blank rather than pointing somewhere that is not it.
    private val privacyPolicyUrl = PrivacyPolicyRoute.BLANK_PAGE
    /**
     * The new eID PIN from its first entry, kept only so the confirmation step can be compared
     * against it. Erased as soon as that comparison is done with, and never copied into a [String].
     */
    private val newPinFirstEntry = CodeEntryBuffer(CodeLength.EID_PIN)

    /**
     * The eID or transport PIN already sent to the card, kept for exactly one more send. The SDK
     * asks for the PIN again when a card read drops mid-scan, and replaying it is what saves the
     * user from retyping; erased on that replay, and on any path that gives up on the scan.
     * Six digits covers both, since a transport PIN is shorter.
     */
    private val replayCode = CodeEntryBuffer(CodeLength.EID_PIN)
    private var isWorkflowControllerReady = false

    private var scanCompletion: ScanCompletion? = null
    private var pendingAuthenticationUrl: String? = null

    override fun setInitialState(): State = State(
        onBackAction = { setEvent(Event.Pop) },
        isLoading = true,
        eidCardType = userRuntimeConfig.eidCardType,
    )

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.DismissError -> {
                setState { copy(error = null, errorDialog = null, isLoading = false) }
            }

            Event.Finish -> setEffect { Effect.Navigation.Finish }

            is Event.Init -> {
                isWorkflowControllerReady = false
                pendingAuthenticationUrl = null
                cardReaderInteractor.startCardReader()
                subscribeToCardReaderInteractorEvents()
                performAttestation()
                // we set the first screen and OnAccessRights will remove the loader
                transitionTo(
                    step = ReadCardScreenStep.OnboardingCard,
                    flowType = CardReaderFlowType.ISSUANCE,
                ) {
                    copy(isLoading = true)
                }
            }

            is Event.AcceptRightsAndEnterPin -> acceptRightsAndEnterPin()

            // NFC is only needed once the card is to be read, so the scan is where it is checked.
            Event.OnStartScanningClick -> startScan()

            Event.OnCancelScanClick -> cancelScan()

            Event.OnScanContinueClick -> continueAfterScan()

            Event.OnEnableNfcButtonClick -> setEffect { Effect.OpenNfcSettings }

            Event.Pop -> handleBackNavigation()
            Event.Close -> handleCloseNavigation()

            // Leaving costs the user everything entered so far, so the X only raises the question;
            // Close, sent by the dialog, is what carries it out.
            Event.OnCloseButtonClick -> {
                val closeBehavior = viewState.value.let { state ->
                    state.flowDefinition.navigationPolicyFor(state.currentRoute).closeBehavior
                }
                if (closeBehavior == CardReaderCloseBehavior.CANCEL_AND_EXIT_TO_DASHBOARD) {
                    setState { copy(isCancelFlowDialogVisible = true) }
                }
            }

            Event.DismissCancelFlowDialog -> setState { copy(isCancelFlowDialogVisible = false) }

            is Event.Pin -> {
                val entered = viewState.value.pinState.buffer
                val pin = if (entered.length > 0) {
                    // First send: hand the code over but keep it for one possible replay.
                    replayCode.copyFrom(entered)
                    entered.wipe()
                    replayCode.toPin()
                } else {
                    // A replay, and the last one, so the cache goes with it.
                    replayCode.consumeAsPin()
                }
                setState { copy(isLoading = scanStatus == null) }
                if (viewState.value.eidCardType.isVirtual) {
                    // A simulated card takes no PIN, so the digits are discarded rather than sent.
                    pin.getAndClear().close()
                    replayCode.wipe()
                    cardReaderInteractor.providePin(null)
                } else {
                    cardReaderInteractor.providePin(pin)
                }
            }

            is Event.Can -> {
                val can = viewState.value.pinState.buffer.consumeAsPin()
                setState { copy(isLoading = scanStatus == null) }
                cardReaderInteractor.provideCan(can)
            }

            is Event.NewPin -> {
                val newPin = viewState.value.pinState.buffer.consumeAsPin()
                setState { copy(isLoading = scanStatus == null) }
                cardReaderInteractor.provideNewPin(newPin)
            }

            Event.OnPause -> {
                // TODO WD-48
            }

            is Event.OnResume -> handleResume(
                isNfcEnabled = event.isNfcEnabled,
                nfcAntennaPosition = event.nfcAntennaPosition,
            )

            is Event.BottomSheet.UpdateBottomSheetState -> setState { copy(isBottomSheetOpen = event.isOpen) }
            Event.OnNoPinLetterButtonClick -> {
                val currentState = viewState.value
                transitionTo(ReadCardScreenStep.NoPinLetterInfo) {
                    copy(
                        isBottomSheetOpen = false,
                        returnTarget = CardReaderReturnTarget(
                            route = currentState.currentRoute,
                            flowType = currentState.activeFlowType,
                        ),
                    )
                }
            }

            Event.OnEnterCanButtonPress -> {
                transitionTo(ReadCardScreenStep.EnterCan) {
                    copy(pinState = pinState.cleared(CodeLength.CAN))
                }
            }

            is Event.OnPinUpdate -> {
                if (viewState.value.pinState.supportingText != null) {
                    setState { copy(pinState = pinState.copy(supportingText = null)) }
                }
            }

            Event.OnContinueClickCardPin -> {
                transitionTo(ReadCardScreenStep.NfcScanPrompt.EidPin) {
                    copy(bottomSheetTitle = resourceProvider.getString(R.string.nfc_scanning_nfc_tap_sec_button))
                }
            }

            is Event.OnCanUpdate -> {
                if (viewState.value.pinState.buffer.isComplete) {
                    transitionTo(ReadCardScreenStep.NfcScanPrompt.Can) {
                        copy(bottomSheetTitle = resourceProvider.getString(R.string.nfc_scanning_nfc_tap_sec_button))
                    }
                }
            }

            is Event.OnTransportPinUpdate -> {
                if (viewState.value.pinState.buffer.isComplete) {
                    transitionTo(ReadCardScreenStep.NfcScanPrompt.TransportPin) {
                        copy(bottomSheetTitle = resourceProvider.getString(R.string.nfc_scanning_nfc_tap_sec_button))
                    }
                }
            }

            is Event.OnNewPinUpdate -> {
                val entered = viewState.value.pinState.buffer
                setState { copy(canContinue = entered.isComplete) }
            }

            is Event.OnNewPinConfirmUpdate -> {
                val entered = viewState.value.pinState.buffer
                setState {
                    copy(canContinue = entered.isComplete && entered.contentEquals(newPinFirstEntry))
                }
            }

            is Event.OnSearchCitizenOfficeButtonClick -> {
                setEffect { OpenLink(citizenOfficeUrl.buildUpon().fragment(event.locale.language).build()) }
            }

            Event.OnCustomerServiceCallButtonClick -> {
                setEffect {
                    val customerServiceNumber =
                        resourceProvider.getString(R.string.eid_scan_explanation_customer_service_number)
                    StartCall(customerServiceNumber)
                }
            }

            Event.StartTransportPin -> {
                cardReaderInteractor.acceptRights()
                cardReaderInteractor.startChangePin()
                transitionTo(
                    step = ReadCardScreenStep.EnterTransportPin,
                    flowType = CardReaderFlowType.CHANGE_PIN,
                ) {
                    copy(
                        bottomSheetTitle = resourceProvider.getString(R.string.pid_issuance_no_letter_forgot_info_title),
                        pinState = pinState.cleared(CodeLength.TRANSPORT_PIN),
                        isLoading = false,
                    )
                }
            }

            Event.OnSetNewPinPrimaryButtonClick -> {
                transitionTo(ReadCardScreenStep.EnterNewPin) {
                    // The transport PIN this may be arriving from is five digits, so the field has
                    // to be resized as well as emptied.
                    copy(pinState = pinState.cleared(CodeLength.EID_PIN))
                }
            }

            Event.OnContinueClickOnSetNewPin -> {
                newPinFirstEntry.copyFrom(viewState.value.pinState.buffer)
                transitionTo(ReadCardScreenStep.ConfirmNewPin) {
                    copy(
                        canContinue = false,
                        pinState = pinState.cleared(CodeLength.EID_PIN)
                    )
                }
            }

            Event.OnContinueClickOnConfirmNewPin -> {
                transitionTo(ReadCardScreenStep.NfcScanPrompt.EidNewPinSet) {
                    copy(bottomSheetTitle = resourceProvider.getString(R.string.nfc_scanning_nfc_tap_sec_button))
                }
            }

            Event.OnPrivacyPolicyButtonClick -> {
                setEffect { Effect.Navigation.NavigateToPrivacyPolicy(privacyPolicyUrl) }
            }

            Event.OnContinueClickProgressSteps -> {
                transitionTo(ReadCardScreenStep.ProgressSteps) {
                    copy(isBottomSheetOpen = false)
                }
            }

            Event.OnContinueClickOnboardingPin -> {
                transitionTo(ReadCardScreenStep.OnboardingPin) {
                    copy(isBottomSheetOpen = false)
                }
            }

            Event.TransportPinLetter -> {
                val currentState = viewState.value
                setEffect {
                    Effect.HideKeyboard
                }
                transitionTo(
                    step = ReadCardScreenStep.TransportPinLetter,
                    flowType = CardReaderFlowType.CHANGE_PIN,
                ) {
                    copy(
                        isBottomSheetOpen = false,
                        returnTarget = CardReaderReturnTarget(
                            route = currentState.currentRoute,
                            flowType = currentState.activeFlowType,
                        ),
                    )
                }
            }

            Event.Consent -> {
                transitionTo(ReadCardScreenStep.Consent) {
                    copy(
                        isLoading = false,
                        isBottomSheetOpen = false
                    )
                }
            }

            Event.OnIssuerInformationClick -> {
                setEffect {
                    //TODO to pass the data from the certificate
                    NavigateToIssuerDetails(
                        details = IssuerInfo(
                            issuerName = "Bundesdruckerei",
                            imageRes = R.drawable.bundesdruckerei_logo_squared,
                            logoUri = null,
                            address = "Kommandantenstraße 18\n10969 Berlin",
                            email = "info@bdr.de",
                            privacyPolicy = "bundesdruckerei.de/de/datenschutz",
                            certificateValidUntil = "23.05.2030"
                        ),
                    )
                }
            }

        }
    }

    /**
     * Whether the user has to switch NFC on before the card can be read.
     *
     * A virtual card is served by the simulator instead of the NFC sensor, so for it
     * the state of the adapter is irrelevant and the flow continues as usual.
     */
    private fun requiresNfcActivation(): Boolean = with(viewState.value) {
        !isNfcEnabled && !eidCardType.isVirtual
    }

    private fun startScan() {
        if (requiresNfcActivation()) {
            val currentState = viewState.value
            transitionTo(ReadCardScreenStep.NfcActivation) {
                copy(
                    isLoading = false,
                    isBottomSheetOpen = false,
                    returnTarget = CardReaderReturnTarget(
                        route = currentState.currentRoute,
                        flowType = currentState.activeFlowType,
                    ),
                )
            }
            return
        }

        setState {
            copy(
                scanStatus = CardScanStatus.READY,
                codeRetry = null,
                readingProgress = null,
                isBottomSheetOpen = false,
                isLoading = false,
            )
        }
        setEffect { Effect.RestartNfcReader }

        if (viewState.value.eidCardType.isVirtual) {
            cardReaderInteractor.setVirtualCard()
        }
    }

    private fun cancelScan() {
        val codeRetry = viewState.value.codeRetry
        if (codeRetry == null) {
            setState { copy(scanStatus = null, readingProgress = null) }
            return
        }

        transitionTo(codeRetry.step) {
            copy(
                bottomSheetTitle = resourceProvider.getString(R.string.pid_issuance_sheet_eid_pin_not_set_title),
                pinState = pinState.cleared(
                    capacity = codeRetry.step.codeLength(),
                    supportingText = codeRetry.supportingText,
                ),
                isLoading = false,
            )
        }
    }

    private fun continueAfterScan() {
        when (val completion = scanCompletion) {
            is ScanCompletion.Issuance -> navigateToWalletPinSet(completion.redirectUrl)
            ScanCompletion.PinChanged -> navigateToAddDocument()
            null -> Unit
        }
        scanCompletion = null
    }

    /**
     * Accepts the read rights and moves on to the card PIN entry.
     *
     * Shared by the consent screen and the NFC activation screen: the latter only
     * postpones this step until NFC is switched on.
     */
    private fun acceptRightsAndEnterPin() {
        // right should be accepted before entering pin
        cardReaderInteractor.acceptRights()
        transitionTo(ReadCardScreenStep.EnterPin) {
            copy(
                bottomSheetTitle = resourceProvider.getString(R.string.pid_issuance_sheet_eid_pin_not_set_title),
                pinState = pinState.cleared(CodeLength.EID_PIN),
                isLoading = false,
            )
        }
    }

    /**
     * Records the platform NFC state and resumes the flow the activation screen
     * interrupted, so returning from the system settings with NFC switched on
     * continues straight to the card PIN entry without another tap on the button.
     */
    private fun handleResume(
        isNfcEnabled: Boolean,
        nfcAntennaPosition: NfcAntennaPosition,
    ) {
        val returnTarget = viewState.value.takeIf {
            it.currentRoute == CardReaderRoute.NFC_ACTIVATION
        }?.returnTarget
        setState {
            copy(
                isNfcEnabled = isNfcEnabled,
                nfcAntennaPosition = nfcAntennaPosition,
            )
        }

        // The activation screen is a detour off the scan, so switching NFC on resumes that scan
        // rather than restarting the flow.
        if (isNfcEnabled && returnTarget != null) {
            transitionTo(step = returnTarget.route.toStep(), flowType = returnTarget.flowType) {
                copy(returnTarget = null)
            }

            if (returnTarget.route.isNfcPrompt) {
                startScan()
            } else {
                setEffect { Effect.RestartNfcReader }
            }
        }
    }

    private fun performAttestation() {
        viewModelScope.launch {
            walletAttestationInteractor.generateAttestation()
                .collect { response ->
                    when (response) {
                        is WalletAttestationResult.Failure -> {

                            val errorCode = runCatching {
                                AttestationState.ErrorCode.valueOf(response.errorCode)
                            }.getOrDefault(AttestationState.ErrorCode.UNKNOWN)

                            val (titleRes, bodyTextRes, primaryButtonTextRes) = when (errorCode) {
                                AttestationState.ErrorCode.WB_ACCOUNT_UNKNOWN -> Triple(
                                    R.string.pid_issuance_wb_account_unkown_title,
                                    R.string.pid_issuance_wb_account_unkown_paragraph,
                                    R.string.pid_issuance_wb_account_unkown_prim_button
                                )

                                AttestationState.ErrorCode.WB_AUTH_VERIFICATION_FAILED -> Triple(
                                    R.string.pid_issuance_wb_auth_verification_failed_title,
                                    R.string.pid_issuance_wb_auth_verification_failed_paragraph,
                                    R.string.pid_issuance_wb_auth_verification_failed_prim_button
                                )

                                else -> Triple(
                                    R.string.global_error_title,
                                    R.string.global_error_paragraph,
                                    R.string.global_error_prim_button
                                )
                            }

                            setState {
                                copy(
                                    errorDialog =  GenericErrorDialogConfig(
                                        titleRes = titleRes,
                                        bodyTextRes = bodyTextRes,
                                        // The code as the backend reported it, so that a code this
                                        // app does not recognise is still shown to the user.
                                        errorCode = response.errorCode,
                                        traceId = response.traceId,
                                        primaryButtonTextRes = primaryButtonTextRes,
                                        onDismiss = { setEvent(Event.DismissError)},
                                        onPrimaryButtonClick = { setEvent(Event.DismissError) }
                                    ),
                                    isLoading = false
                                )
                            }
                             cancelIssuanceAndIdentification()
                        }

                        is WalletAttestationResult.Success ->
                            startIssuanceAndSubscribeToEvents(response.walletInstanceAttestationSpec)
                    }
                }
        }
    }

    private fun startIssuanceAndSubscribeToEvents(walletInstanceAttestationSpec: WalletInstanceAttestationSpec) {
        // subscribe to the events
        viewModelScope.launch {
            addDocumentsInteractor.issuanceEvents.collect { event ->
                when (event) {
                    IssuanceEvent.Completed -> {
                        setState {
                            setInitialState().copy(isLoading = false)
                        }
                    }

                    is IssuanceEvent.Failed -> {
                        setState {
                            copy(
                                isLoading = false,
                                errorDialog = GenericErrorDialogConfig(
                                    titleRes = R.string.global_error_title,
                                    bodyTextRes = R.string.global_error_paragraph,
                                    errorCode = "ISSUANCE_FAILED",
                                    traceId = telemetry.currentTraceId(),
                                    primaryButtonTextRes = R.string.global_error_prim_button,
                                    onDismiss = { Event.DismissError },
                                    onPrimaryButtonClick = { Event.DismissError }
                                )
                            )
                        }
                        cancelIssuanceAndIdentification()
                    }
                }
            }
        }
        // start issuance
        telemetry.startSpan(TelemetryConstants.ISSUANCE)
        addDocumentsInteractor.startIssueDocumentAttested(
            configIds = credentialTypes,
            issuanceMethod = IssuanceMethod.OPENID4VCI,
            issuerId = configLogic.environmentConfig.pidIssuerURL,
            walletInstanceAttestationSpec = walletInstanceAttestationSpec
        )
    }

    private fun subscribeToCardReaderInteractorEvents(showLoader: Boolean = true) {
        viewModelScope.launch {
            addDocumentsInteractor.authorizationHandler.authorizationRequest.collect { url ->
                logController.d(logTag) { "Authorization requested via Handler: $url" }
                requestAuthentication(url)
            }
        }

        viewModelScope.launch {
            cardReaderInteractor.eidFlow.onStart {
                setState { copy(isLoading = showLoader) }
            }.collect { event ->
                logController.d(logTag) { "Event: $event" }
                when (event) {
                    WorkflowEvent.InsertCardRequested -> {
                        logController.d(logTag) { "InsertCardRequested" }
                        if (viewState.value.isLoading) {
                            setState {
                                copy(
                                    isLoading = false
                                )
                            }
                        }

                        // Re-arm reader mode so a card resting on the sensor (the CAN /
                        // Transport PIN follow-up reads) is re-discovered when the SDK
                        // re-requests the card, without needing a re-tap.
                        // Skip while a read is already in progress (readingProgress != null) so a
                        // repeated request does not toggle reader mode mid-read.
                        if (viewState.value.currentRoute.isNfcPrompt &&
                            viewState.value.readingProgress == null
                        ) {
                            logController.d(logTag) { "InsertCardRequested while NFC prompt active -> restarting NFC reader" }
                            setEffect { Effect.RestartNfcReader }
                        }
                    }

                    WorkflowEvent.Idle -> {
                        //TODO
                    }

                    WorkflowEvent.NewPinRequested -> {
                        //TODO
                    }

                    is WorkflowEvent.AuthenticationCompleted -> {
                        wipeEnteredCodes()

                        if (viewState.value.scanStatus != null) {
                            cardReaderInteractor.cancelIdentification()
                            scanCompletion = ScanCompletion.Issuance(event.redirectURL)
                            setState {
                                copy(
                                    scanStatus = CardScanStatus.SUCCESS,
                                    codeRetry = null,
                                    isLoading = false,
                                    readingProgress = null,
                                    pinState = pinState.cleared(),
                                    bottomSheetTitle = null,
                                )
                            }
                        } else {
                            transitionTo(ReadCardScreenStep.Completed) {
                                copy(
                                    isLoading = false,
                                    pinState = pinState.cleared(),
                                    bottomSheetTitle = null
                                )
                            }

                            // needed to display success screen
                            delay(2.toDuration(DurationUnit.SECONDS))
                            navigateToWalletPinSet(event.redirectURL)

                            cardReaderInteractor.cancelIdentification()
                        }
                    }

                    is WorkflowEvent.Return -> {
                        cardReaderInteractor.cancelIdentification()
                    }

                    WorkflowEvent.CardDeactivated -> {
                        // TODO WD-48
                    }

                    WorkflowEvent.CardRecognized -> {
                        handleCardRecognized()
                    }

                    WorkflowEvent.CardRemoved -> {
                        if (viewState.value.isLoading && viewState.value.currentRoute.isNfcPrompt) {
                            setState {
                                copy(isLoading = false, readingProgress = null)
                            }
                        }
                    }

                    WorkflowEvent.IdentificationCancelled -> {
                        //TODO
                    }

                    is WorkflowEvent.ReadingProgress -> {
                        setState {
                            copy(
                                readingProgress = event.progress,
                                bottomSheetTitle = null,
                                scanStatus = scanStatus?.let { CardScanStatus.IN_PROGRESS },
                            )
                        }
                    }

                    WorkflowEvent.ReadyToStart -> {
                        isWorkflowControllerReady = true
                        pendingAuthenticationUrl?.let { requestUrl ->
                            pendingAuthenticationUrl = null
                            requestAuthentication(requestUrl)
                        }
                    }

                    is WorkflowEvent.PinRequested -> handlePinRequested(event.pinRetryCounter)

                    is WorkflowEvent.ShowError -> {
                        // TODO handle showing error
                        setState {
                            copy(
                                pinState = pinState.cleared(),
                                bottomSheetTitle = null
                            )
                        }
                    }

                    is WorkflowEvent.OnAccessRights -> {
                        // TODO make a list of the right to read from the card
                        // IdentificationRequest(requiredAttributes=[ADDRESS, BIRTH_NAME, NATIONALITY, PLACE_OF_BIRTH, DATE_OF_BIRTH, FAMILY_NAME, GIVEN_NAMES, PSEUDONYM, AGE_VERIFICATION], transactionInfo=null)
                        // action would be taken in consent
                        setState { copy(isLoading = false) }
                    }

                    is WorkflowEvent.AuthenticationFailed -> {
                        //TODO
                        setState {
                            copy(
                                bottomSheetTitle = null,
                                pinState = pinState.cleared(),
                                scanStatus = scanStatus?.let { CardScanStatus.FAILED },
                            )
                        }
                    }

                    is WorkflowEvent.AuthenticationStartFailed -> {
                        //TODO
                        setState {
                            copy(
                                bottomSheetTitle = null,
                                pinState = pinState.cleared()
                            )
                        }
                    }

                    WorkflowEvent.AuthenticationStarted -> {
                        // TODO something here
                    }

                    is WorkflowEvent.BadState -> {
                        //TODO handle error
                        setState {
                            copy(
                                bottomSheetTitle = null,
                                pinState = pinState.cleared(),
                                isLoading = false,
                            )
                        }
                    }

                    WorkflowEvent.ChangePinError -> {
                        //TODO handle
                        setState {
                            copy(
                                bottomSheetTitle = null,
                                pinState = pinState.cleared(),
                            )
                        }
                    }

                    WorkflowEvent.ChangePinStarted -> {
                        //TODO handle
                    }

                    WorkflowEvent.ChangePinSuccess -> {
                        cardReaderInteractor.cancelIdentification()

                        if (viewState.value.scanStatus != null) {
                            scanCompletion = ScanCompletion.PinChanged
                            setState {
                                copy(
                                    scanStatus = CardScanStatus.SUCCESS,
                                    codeRetry = null,
                                    isLoading = false,
                                    readingProgress = null,
                                    pinState = pinState.cleared(),
                                    bottomSheetTitle = null,
                                )
                            }
                        } else {
                            transitionTo(ReadCardScreenStep.Completed) {
                                copy(
                                    isLoading = false,
                                    pinState = pinState.cleared(),
                                    bottomSheetTitle = null
                                )
                            }

                            delay(2.toDuration(DurationUnit.SECONDS))
                            navigateToAddDocument()
                        }
                    }

                    WorkflowEvent.EnterCan -> {
                        setState {
                            copy(
                                // If eid pin is not requested and directly can is requested, then we refresh the cached pin
                                pinState = pinState.cleared(CodeLength.CAN).also { replayCode.wipe() },
                            )
                        }
                        val nextStep = if (viewState.value.currentRoute.isInPinEntryStage()) {
                            ReadCardScreenStep.PinBlockedError
                        } else {
                            ReadCardScreenStep.EnterCan
                        }

                        // if current step was already EnterCan, show warning message
                        val pinState =
                            if (viewState.value.currentRoute == CardReaderRoute.ENTER_CAN) {
                                viewState.value.pinState.cleared(
                                    capacity = CodeLength.CAN,
                                    supportingText = resourceProvider.getString(R.string.pid_issuance_can_entry_error_wrong_can),
                                )
                            } else viewState.value.pinState.cleared(CodeLength.CAN)

                        transitionTo(nextStep) {
                            copy(
                                isLoading = false,
                                bottomSheetTitle = resourceProvider.getString(R.string.pid_issuance_sheet_can_title),
                                pinState = pinState,
                            )
                        }
                    }

                    WorkflowEvent.EnterCanError -> {
                        transitionTo(ReadCardScreenStep.EnterCan) {
                            copy(
                                pinState = pinState.cleared(
                                    capacity = CodeLength.CAN,
                                    supportingText = resourceProvider.getString(R.string.pid_issuance_can_entry_error_wrong_can),
                                ),
                                isLoading = false,
                                bottomSheetTitle = resourceProvider.getString(R.string.pid_issuance_sheet_can_title),
                            )
                        }
                    }

                    WorkflowEvent.EnterNewPin -> {
                        if (viewState.value.currentRoute == CardReaderRoute.NFC_SCAN_TRANSPORT_PIN) {
                            transitionTo(ReadCardScreenStep.EnterNewPinInstructions) {
                                copy(
                                    isLoading = false,
                                    bottomSheetTitle = resourceProvider.getString(R.string.new_pin_set_bottom_sheet_title),
                                    pinState = pinState.cleared(CodeLength.EID_PIN),
                                )
                            }
                        } else {
                            transitionTo(ReadCardScreenStep.EnterNewPin) {
                                copy(
                                    isLoading = false,
                                    bottomSheetTitle = resourceProvider.getString(R.string.new_pin_set_bottom_sheet_title),
                                    pinState = pinState.cleared(CodeLength.EID_PIN),
                                )
                            }
                        }
                    }

                    WorkflowEvent.EnterNewPinError -> {
                        transitionTo(ReadCardScreenStep.EnterNewPin) {
                            copy(
                                pinState = pinState.cleared(CodeLength.EID_PIN),
                                isLoading = false,
                            )
                        }
                    }

                    WorkflowEvent.EnterPuk -> {
                        //TODO handle
                        setState {
                            copy(
                                isLoading = false,
                                pinState = pinState.cleared(),
                                bottomSheetTitle = null,
                            )
                        }
                    }

                    WorkflowEvent.EnterPukError -> {
                        //TODO handle
                        setState {
                            copy(
                                isLoading = false,
                                bottomSheetTitle = null,
                                pinState = pinState.cleared()
                            )
                        }
                    }
                }
            }
        }
    }

    fun onNfcTagDetected(tag: Tag) {
        logController.d(logTag) { "onNfcTagDetected: NFC tag detected, forwarding to reader" }
        cardReaderInteractor.handleNfcTag(tag)
    }

    @RestrictTo(RestrictTo.Scope.TESTS)
    public override fun onCleared() {
        // do not use outside VM lifecycle
        super.onCleared()
        // Do NOT cancel the in-flight issuance here. Issuance runs in a process-scoped
        // (singleton) coroutine scope that is deliberately decoupled from this ViewModel's
        // lifecycle: after the eID authorization redirect, wallet-core keeps driving the
        // issuance and the redirect is resumed from a later screen (WalletPinSet). Only tear down the identification session here; issuance is cancelled
        // explicitly on user abort (exitToDashboard) and on failure.
        cancelIdentification()
        wipeEnteredCodes()
    }

    /**
     * Erases every code this screen is holding. Called when the flow ends, however it ends, so that
     * abandoning a card read leaves no more behind than finishing one.
     */
    private fun wipeEnteredCodes() {
        newPinFirstEntry.wipe()
        replayCode.wipe()
        viewState.value.pinState.buffer.wipe()
    }

    private fun cancelIssuanceAndIdentification() {
        cancelIdentification()
        addDocumentsInteractor.cancelIssuance()
    }

    private fun cancelIdentification() {
        isWorkflowControllerReady = false
        pendingAuthenticationUrl = null
        telemetry.endSpan(TelemetryConstants.ISSUANCE)
        cardReaderInteractor.cancelIdentification()
    }

    private fun transitionTo(
        step: ReadCardScreenStep,
        flowType: CardReaderFlowType = viewState.value.activeFlowType,
        update: State.() -> State = { this },
    ) {
        val enteringNfcPrompt = step.toRoute().isNfcPrompt
        setState {
            val next = withStep(step = step, flowType = flowType)
                .copy(scanStatus = null, codeRetry = null)
                .update()
            // Reset any progress from a previous read so the next one starts clean.
            if (enteringNfcPrompt) next.copy(readingProgress = null) else next
        }
    }

    private fun handleCardRecognized() {
        if (viewState.value.scanStatus != null) {
            setState { copy(scanStatus = CardScanStatus.IN_PROGRESS) }
        }

        // Each of these events reads the entered code out of the buffer and erases it as it goes.
        when (viewState.value.currentRoute) {
            CardReaderRoute.NFC_SCAN_CAN -> setEvent(Event.Can)
            CardReaderRoute.NFC_SCAN_EID_PIN,
            CardReaderRoute.NFC_SCAN_TRANSPORT_PIN,
            -> setEvent(Event.Pin)

            CardReaderRoute.NFC_SCAN_NEW_PIN -> setEvent(Event.NewPin)
            else -> Unit
        }
    }

    /** How many digits the code field on [this] step asks for. */
    private fun ReadCardScreenStep.codeLength(): Int = when (this) {
        ReadCardScreenStep.EnterTransportPin -> CodeLength.TRANSPORT_PIN
        ReadCardScreenStep.EnterCan -> CodeLength.CAN
        else -> CodeLength.EID_PIN
    }

    private fun handlePinRequested(pinRetryCounter: Int) {
        val currentState = viewState.value
        if (currentState.currentRoute.canReplayCachedPin() && replayCode.length > 0) {
            setEvent(Event.Pin)
            return
        }

        replayCode.wipe()
        if (currentState.scanStatus != null && currentState.eidCardType.isVirtual) return

        val pinStateWarning = if (pinRetryCounter == 1) {
            resourceProvider.getString(R.string.pid_issuance_puk_entry_warning_wrong_puk_1)
        } else {
            resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin)
        }
        val fallbackStep = pinRequestedFallbackStep(currentState.currentRoute)

        if (currentState.scanStatus != null) {
            setState {
                copy(
                    scanStatus = CardScanStatus.FAILED,
                    codeRetry = CardReaderCodeRetry(
                        step = fallbackStep,
                        supportingText = pinStateWarning,
                    ),
                    isLoading = false,
                    readingProgress = null,
                )
            }
            return
        }

        transitionTo(fallbackStep) {
            copy(
                bottomSheetTitle = resourceProvider.getString(R.string.pid_issuance_sheet_eid_pin_not_set_title),
                pinState = pinState.cleared(
                    capacity = fallbackStep.codeLength(),
                    supportingText = pinStateWarning,
                ),
                isLoading = false,
            )
        }
    }

    private fun pinRequestedFallbackStep(currentRoute: CardReaderRoute): ReadCardScreenStep {
        return when (currentRoute) {
            CardReaderRoute.NFC_SCAN_TRANSPORT_PIN -> ReadCardScreenStep.EnterTransportPin
            CardReaderRoute.NFC_SCAN_CAN -> ReadCardScreenStep.EnterCanSuccess
            else -> ReadCardScreenStep.EnterPin
        }
    }

    private fun CardReaderRoute.canReplayCachedPin(): Boolean {
        return this == CardReaderRoute.NFC_SCAN_EID_PIN ||
            this == CardReaderRoute.NFC_SCAN_TRANSPORT_PIN
    }

    private fun CardReaderRoute.isInPinEntryStage(): Boolean {
        return this != CardReaderRoute.PIN_BLOCKED_ERROR &&
            this != CardReaderRoute.ENTER_CAN &&
            this != CardReaderRoute.NFC_SCAN_CAN &&
            this != CardReaderRoute.ENTER_CAN_SUCCESS
    }

    private fun handleBackNavigation() {
        val currentState = viewState.value
        val navigationPolicy = currentState.flowDefinition.navigationPolicyFor(currentState.currentRoute)

        when (navigationPolicy.backBehavior) {
            CardReaderBackBehavior.PREVIOUS_ROUTE -> {
                val previousRoute = CardReaderFlowNavigator(currentState.flowDefinition)
                    .previousRoute(currentState.currentRoute)

                if (previousRoute != null) {
                    transitionTo(previousRoute.toStep())
                } else {
                    currentState.returnTarget?.let { target ->
                        transitionTo(step = target.route.toStep(), flowType = target.flowType) {
                            copy(returnTarget = null)
                        }
                    } ?: exitToDashboard()
                }
            }

            CardReaderBackBehavior.EXIT_TO_DASHBOARD -> exitToDashboard()
            CardReaderBackBehavior.DISABLED -> Unit
        }
    }

    private fun handleCloseNavigation() {
        val currentState = viewState.value
        val navigationPolicy = currentState.flowDefinition.navigationPolicyFor(currentState.currentRoute)

        when (navigationPolicy.closeBehavior) {
            CardReaderCloseBehavior.CANCEL_AND_EXIT_TO_DASHBOARD -> exitToDashboard()
            CardReaderCloseBehavior.NO_ACTION -> Unit
        }
    }

    private fun exitToDashboard() {
        wipeEnteredCodes()
        setState {
            copy(
                isLoading = false,
                isBottomSheetOpen = false,
                isCancelFlowDialogVisible = false,
                bottomSheetTitle = null,
                pinState = pinState.cleared(),
            )
        }
        cancelIssuanceAndIdentification()
        setEffect {
            Pop(
                screenRoute = generateComposableNavigationLink(
                    screen = DashboardScreens.Dashboard,
                    arguments = generateComposableArguments(
                        mapOf(
                            "flowType" to flowType
                        ),
                    )
                ), inclusive = false
            )
        }
    }

    private fun navigateToWalletPinSet(redirectUrl: String) {
        val encodedRedirectUrl = URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8.toString())
        setEffect {
            //TODO WD-203 refactoring navigations
            Effect.Navigation.SwitchScreen(
                screenRoute = generateComposableNavigationLink(
                    screen = IssuanceScreens.WalletPinSet,
                    arguments = generateComposableArguments(
                        mapOf(
                            "flowType" to IssuanceFlowUiConfig.fromIssuanceFlowUiConfig(flowType),
                            "redirectUrl" to encodedRedirectUrl,
                        )
                    )
                ),
                redirectUrl = redirectUrl,
                inclusive = false
            )
        }
    }

    private fun navigateToAddDocument() {
        setEffect {
            Pop(
                screenRoute = generateComposableNavigationLink(
                    screen = IssuanceScreens.AddDocument,
                    arguments = generateComposableArguments(
                        mapOf(
                            "flowType" to IssuanceFlowUiConfig.fromIssuanceFlowUiConfig(
                                IssuanceFlowUiConfig.NO_DOCUMENT
                            )
                        )
                    )
                ),
                inclusive = false
            )
        }
    }

    private fun requestAuthentication(url: String) {
        if (!isWorkflowControllerReady) {
            pendingAuthenticationUrl = url
            logController.d(logTag) {
                "Queued authentication until WorkflowController reports ReadyToStart."
            }
            return
        }

        cardReaderInteractor.startAuthentication(url.toUri())
    }
}

private sealed interface ScanCompletion {

    /** The card was read for the issuance, which continues at [redirectUrl]. */
    data class Issuance(val redirectUrl: String) : ScanCompletion

    /** The card's PIN was changed, which is a flow of its own and ends here. */
    data object PinChanged : ScanCompletion
}