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
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.commonfeature.interactor.IssuanceEvent
import eu.europa.ec.commonfeature.model.DocumentType
import eu.europa.ec.commonfeature.ui.document_details.transformer.DocumentDetailsTransformer
import eu.europa.ec.commonfeature.ui.issuer_details.model.IssuerInfo
import eu.europa.ec.corelogic.controller.AttestationState
import eu.europa.ec.corelogic.controller.IssuanceMethod
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.interactor.walletattestation.WalletAttestationInteractor
import eu.europa.ec.corelogic.interactor.walletattestation.WalletAttestationResult
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import org.sprind.wallet.businesslogic.config.UserRuntimeConfig
import org.sprind.wallet.businesslogic.config.isVirtual
import org.sprind.wallet.businesslogic.model.UserPin
import org.sprind.wallet.cardreaderfeature.domain.CARD_DEACTIVATED
import org.sprind.wallet.cardreaderfeature.domain.CardReaderBackBehavior
import org.sprind.wallet.cardreaderfeature.domain.CardReaderCloseBehavior
import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowNavigator
import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowType
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute
import org.sprind.wallet.cardreaderfeature.domain.CardScanStatus
import org.sprind.wallet.cardreaderfeature.domain.EidFailureRecovery
import org.sprind.wallet.cardreaderfeature.domain.NfcAntennaPosition
import org.sprind.wallet.cardreaderfeature.domain.PIN_BLOCKED
import org.sprind.wallet.cardreaderfeature.domain.isNfcPrompt
import org.sprind.wallet.cardreaderfeature.ui.document.read.Effect.Navigation.NavigateToIssuerDetails
import org.sprind.wallet.cardreaderfeature.ui.document.read.Effect.Navigation.OpenLink
import org.sprind.wallet.cardreaderfeature.ui.document.read.Effect.Navigation.Pop
import org.sprind.wallet.cardreaderfeature.ui.document.read.Effect.StartCall
import org.sprind.wallet.commonfeature.ui.error.toPidIssuanceErrorDialogConfig
import org.sprind.wallet.commonfeature.ui.transformer.toListItemData
import org.sprind.wallet.corelogic.eid.AuthOutcome
import org.sprind.wallet.corelogic.eid.EidEngine
import org.sprind.wallet.corelogic.eid.EidRequest
import org.sprind.wallet.corelogic.eid.EidResult
import org.sprind.wallet.corelogic.eid.EidWorkflow
import org.sprind.wallet.corelogic.eid.PinChanged
import org.sprind.wallet.corelogic.eid.PreviousAttempt
import org.sprind.wallet.corelogic.eid.ReaderState
import org.sprind.wallet.corelogic.eid.discard
import org.sprind.wallet.corelogic.eid.eidCardSource
import org.sprind.wallet.uilogic.component.CodeEntryBuffer
import org.sprind.wallet.uilogic.component.CodeLength
import org.sprind.wallet.uilogic.component.CredentialAttribute
import org.sprind.wallet.networklogic.utils.HSM_UNAVAILABLE_ERROR_CODE
import org.sprind.wallet.uilogic.component.cleared
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@KoinViewModel
class ReadCardViewModel(
    private val configLogic: ConfigLogic,
    private val resourceProvider: ResourceProvider,
    private val eidEngine: EidEngine,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
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

    // TODO WD-4847: URL NEEDED for PID Privacy Policy for issuance once it is published.
    private val privacyPolicyUrl =
        configLogic.settingsLinks.pidPrivacyPolicy.forLocale(resourceProvider.getLocale())
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

    /**
     * The collectors bridging the authorization handler and the eID workflow to this screen. Held
     * so that both starting them again and leaving the flow can stop the running ones.
     */
    private var workflowSubscription: Job? = null
    private var authorizationSubscription: Job? = null
    private var hsmDelayTimer: Job? = null

    /** The eID workflow the screen takes the user through: the issuance's authentication or a PIN change. */
    private var workflow: EidWorkflow<*>? = null

    /**
     * The request a code has been queued for. [setEvent] is asynchronous, so the request counts as
     * answered from the moment its code is queued: a card recognised in between must not queue it a
     * second code, drawn from a buffer the first one already erased.
     */
    private var answeredRequest: EidRequest? = null

    /** What the reader last reported, which tells a card taken off it from one never put on it. */
    private var lastReaderState: ReaderState? = null

    private var scanCompletion: ScanCompletion? = null

    private var attestationJob: Job? = null
    private var issuanceSubscription: Job? = null

    /**
     * The SDK takes the rights only once it has asked for them, and the user may consent before or
     * after that, so the rights go once both have happened.
     */
    private var hasUserAcceptedRights = false

    /** Whether a physical card lies on the reader, as the SDK last reported. */
    private var isCardPresent = false

    /** The issuance's authentication was ended for the transport PIN flow and waits to be started again. */
    private var isIssuanceSuspended = false

    override fun setInitialState(): State = State(
        onBackAction = { setEvent(Event.Pop) },
        isLoading = true,
        eidCardType = userRuntimeConfig.eidCardType,
        onboardingPin = OnboardingPinConfig(
            pinExplanationLink = resourceProvider.getString(R.string.pid_issuance_onboarding_eid_tert_button_2),
            hasSetPinAnswer = resourceProvider.getMarkdownString(R.string.pid_issuance_onboarding_eid_prim_button),
            setPinWithLetterAnswer = resourceProvider.getMarkdownString(R.string.pid_issuance_onboarding_eid_sec_button),
            noLetterOrForgotPinAnswer = resourceProvider.getMarkdownString(R.string.pid_issuance_onboarding_eid_tert_button_1),
        ),
    )

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.DismissError -> {
                setState { copy(error = null, errorDialog = null, isLoading = false) }
            }

            Event.Finish -> setEffect { Effect.Navigation.Finish }

            is Event.Init -> {
                hasUserAcceptedRights = false
                isIssuanceSuspended = false
                resetCardExchange()
                subscribeToAuthentication()
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

            Event.OnScanRetryClick -> retryWorkflow()

            Event.OnEnableNfcButtonClick -> setEffect { Effect.OpenNfcSettings }

            // Only once the button is shown, so that a double tap does not start the issuance twice.
            Event.RetryAfterHsmDelay -> if (viewState.value.hsmDelay?.canRetry == true) {
                retryAfterHsmDelay()
            }

            Event.CloseHsmDelay -> {
                leaveHsmDelay()
                exitToDashboard()
            }

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

            Event.OnCredentialDetailsClick -> setState { copy(isBottomSheetOpen = true) }

            Event.OnRejectIssuanceClick -> setState { copy(isRejectIssuanceDialogVisible = true) }

            Event.DismissRejectIssuanceDialog -> setState {
                copy(isRejectIssuanceDialogVisible = false)
            }

            Event.OnAcceptIssuanceClick -> {
                (scanCompletion as? ScanCompletion.Issuance)?.let {
                    navigateToWalletPinSet(it.redirectUrl)
                }
                scanCompletion = null
            }

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
                answerAwaited<EidRequest.Pin>(pin) { it.answer(pin) }
            }

            is Event.Can -> {
                val can = viewState.value.pinState.buffer.consumeAsPin()
                setState { copy(isLoading = scanStatus == null) }
                answerAwaited<EidRequest.Can>(can) { it.answer(can) }
            }

            is Event.NewPin -> {
                val newPin = viewState.value.pinState.buffer.consumeAsPin()
                setState { copy(isLoading = scanStatus == null) }
                answerAwaited<EidRequest.NewPin>(newPin) { it.answer(newPin) }
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
                            previous = currentState.returnTarget,
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
                // The field reports its contents once as it is created, before the user has
                // touched it, so an empty entry is not the warning being answered — taking it for
                // one is what wiped the wrong-PIN warning in the instant the entry screen came
                // back with it. The first digit typed is what says the user has read it.
                val entered = viewState.value.pinState
                if (entered.supportingText != null && entered.buffer.length > 0) {
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
                switchToChangePin()
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
                            previous = currentState.returnTarget,
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
                            certificateValidUntil = "23.05.2030",
                            documentType = DocumentType.PID,
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
                        previous = currentState.returnTarget,
                    ),
                )
            }
            return
        }

        val isPhysicalCardOnReader = isCardPresent && !viewState.value.eidCardType.isVirtual
        setState {
            copy(
                scanStatus = if (isPhysicalCardOnReader) CardScanStatus.IN_PROGRESS else CardScanStatus.READY,
                readingProgress = null,
                isBottomSheetOpen = false,
                isLoading = false,
            )
        }
        // Re-arming reader mode re-discovers a card left on the sensor, but one the SDK already holds
        // would lose its connection to it, and with it a read under way.
        if (!isPhysicalCardOnReader) {
            setEffect { Effect.RestartNfcReader }
        }
        presentVirtualCardIfRequested()
        // A card left in place, simulated or resting on the phone, is not recognised again, so a
        // code the SDK asked for while it was being typed goes now. One that was lifted would
        // spend it on no card, so its recognition sends it instead.
        if (viewState.value.eidCardType.isVirtual || isCardPresent) {
            answerAwaitedCode()
        }
    }

    private fun presentVirtualCardIfRequested() {
        if (viewState.value.eidCardType.isVirtual) {
            awaited<EidRequest.InsertCard>()
                ?.takeIf { it.acceptsSimulatedCard }
                ?.let {
                    answeredRequest = it
                    it.presentSimulatedCard()
                }
        }
    }

    private fun cancelScan() {
        setState { copy(scanStatus = null, readingProgress = null) }
    }

    private fun continueAfterScan() {
        when (scanCompletion) {
             is ScanCompletion.Issuance -> {
                val attributes = readCredentialAttributes()
                transitionTo(ReadCardScreenStep.IssuanceConsent) {
                    copy(
                        isBottomSheetOpen = false,
                        bottomSheetTitle = null,
                        issuedCredentialAttributes = attributes,
                    )
                }
            }

            ScanCompletion.PinChanged -> returnToIssuanceAfterPinChange()

            null -> Unit
        }
    }

    /**
     * Accepts the read rights and moves on to the card PIN entry.
     *
     * Shared by the consent screen and the NFC activation screen: the latter only
     * postpones this step until NFC is switched on.
     */
    private fun acceptRightsAndEnterPin() {
        hasUserAcceptedRights = true
        acceptRightsIfReady()
        transitionTo(ReadCardScreenStep.EnterPin) {
            copy(
                bottomSheetTitle = codeEntryTitle(ReadCardScreenStep.EnterPin),
                pinState = pinState.cleared(CodeLength.EID_PIN),
                isLoading = false,
            )
        }
    }

    /**
     * A retry restarts the same issuance the user already consented to, so the consent stays given
     * and the restarted authentication's rights are accepted with it.
     */
    private fun acceptRightsIfReady() {
        if (hasUserAcceptedRights) {
            awaited<EidRequest.AccessRights>()?.let {
                answeredRequest = it
                it.accept()
            }
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
                copy(returnTarget = returnTarget.previous)
            }

            if (returnTarget.route.isNfcPrompt) {
                startScan()
            } else {
                setEffect { Effect.RestartNfcReader }
            }
        }
    }

    private fun performAttestation() {
        attestationJob?.cancel()
        attestationJob = viewModelScope.launch {
            // A slow HSM is not an endless loader to the user, but a wait (WD-5013). Only the loader
            // is replaced: a screen the user is still on (the scan, the steps) is left alone.
            val slowAttestation = launch {
                delay(HSM_SLOW_AFTER)
                if (viewState.value.isLoading) {
                    showHsmDelay()
                }
            }
            walletAttestationInteractor.generateAttestation()
                .collect { response ->
                    slowAttestation.cancel()
                    when (response) {
                        is WalletAttestationResult.Failure -> {
                            // Nor is an overloaded one an error.
                            if (response.errorCode == HSM_UNAVAILABLE_ERROR_CODE) {
                                logController.d(logTag) {
                                    "Attestation deferred, HSM unavailable (trace ${response.traceId})"
                                }
                                // The patience screen stands outside the flow, which ends here.
                                unsubscribeFromWorkflows()
                                cancelIssuanceAndIdentification()
                                setState { copy(isLoading = false, errorDialog = null) }
                                showHsmDelay()
                                return@collect
                            }

                            leaveHsmDelay()
                            setState {
                                copy(
                                    errorDialog = response.networkFailure?.toPidIssuanceErrorDialogConfig(
                                        onRetry = ::retryFromAttestation,
                                        onCancel = ::exitToDashboard,
                                        onDismiss = ::leaveFailedStep,
                                    ) ?: attestationFailedDialog(response),
                                    isLoading = false
                                )
                            }
                            cancelIssuanceAndIdentification()
                        }

                        is WalletAttestationResult.Success -> {
                            leaveHsmDelay()
                            startIssuanceAndSubscribeToEvents(response.walletInstanceAttestationSpec)
                        }
                    }
                }
            slowAttestation.cancel()
        }
    }

    private fun attestationFailedDialog(
        response: WalletAttestationResult.Failure,
    ): GenericErrorDialogConfig {
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

        return GenericErrorDialogConfig(
            titleRes = titleRes,
            bodyTextRes = bodyTextRes,
            // The code as the backend reported it, so that a code this
            // app does not recognise is still shown to the user.
            errorCode = response.errorCode,
            traceId = response.traceId,
            primaryButtonTextRes = primaryButtonTextRes,
            onDismiss = { setEvent(Event.DismissError) },
            onPrimaryButtonClick = { setEvent(Event.DismissError) }
        )
    }

    /**
     * Leaves the step an issuance failure stopped, as the issuance and the identification have
     * already been cancelled and it cannot be continued.
     */
    private fun leaveFailedStep() {
        setEvent(Event.DismissError)
        setEvent(Event.Pop)
    }

    /**
     * Shows the patience screen while the attestation is slow or the wallet backend's HSM is
     * overloaded, and offers a retry after [HSM_DELAY_RETRY_AFTER] (WD-5013). Already shown, it
     * keeps waiting from when it appeared.
     */
    private fun showHsmDelay() {
        if (viewState.value.hsmDelay != null) return
        setState { copy(hsmDelay = HsmDelay()) }
        hsmDelayTimer?.cancel()
        hsmDelayTimer = viewModelScope.launch {
            delay(HSM_DELAY_RETRY_AFTER)
            setState { copy(hsmDelay = hsmDelay?.copy(canRetry = true)) }
        }
    }

    private fun leaveHsmDelay() {
        hsmDelayTimer?.cancel()
        hsmDelayTimer = null
        setState { copy(hsmDelay = null) }
    }

    /**
     * Starts over from the attestation. An attestation still waiting on the HSM is given up, and
     * with it the issuance and the identification started alongside; an overloaded HSM has already
     * cancelled them.
     */
    private fun retryAfterHsmDelay() {
        if (attestationJob?.isActive == true) {
            attestationJob?.cancel()
            unsubscribeFromWorkflows()
            cancelIssuanceAndIdentification()
        }
        leaveHsmDelay()
        retryFromAttestation()
    }

    /**
     * Starts over from the attestation after a failure before the eID step, which the user chose
     * to retry. The failure has already cancelled the issuance and the identification.
     */
    private fun retryFromAttestation() {
        setEvent(Event.DismissError)
        setEvent(Event.Init)
    }

    private fun startIssuanceAndSubscribeToEvents(walletInstanceAttestationSpec: WalletInstanceAttestationSpec) {
        // An issuance started again reports on the same flow, which is listened to once.
        issuanceSubscription?.cancel()
        issuanceSubscription = viewModelScope.launch {
            addDocumentsInteractor.issuanceEvents.collect { event ->
                when (event) {
                    IssuanceEvent.Completed -> {
                        setState {
                            setInitialState().copy(isLoading = false)
                        }
                    }

                    is IssuanceEvent.Failed -> {
                        // Only failures before the eID step reach this screen: once the eID step is
                        // done it has been left for WalletPinSet, which also handles an expired
                        // authorization session.
                        setState {
                            copy(
                                isLoading = false,
                                errorDialog = event.networkFailure?.toPidIssuanceErrorDialogConfig(
                                    onRetry = ::retryFromAttestation,
                                    onCancel = ::exitToDashboard,
                                    onDismiss = ::leaveFailedStep,
                                ) ?: GenericErrorDialogConfig(
                                    titleRes = R.string.global_error_title,
                                    bodyTextRes = R.string.global_error_paragraph,
                                    errorCode = "ISSUANCE_FAILED",
                                    traceId = telemetry.currentTraceId(),
                                    primaryButtonTextRes = R.string.global_error_prim_button,
                                    onDismiss = ::leaveFailedStep,
                                    onPrimaryButtonClick = ::leaveFailedStep,
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

    /**
     * Stops listening to the eID workflow. Leaving the flow is the end of this screen's interest in
     * it, whether or not the ViewModel itself is torn down with the screen.
     */
    private fun unsubscribeFromWorkflows() {
        workflowSubscription?.cancel()
        workflowSubscription = null
        authorizationSubscription?.cancel()
        authorizationSubscription = null
    }

    /** Follows each authentication the issuance asks for, replacing what was subscribed to before. */
    private fun subscribeToAuthentication(showLoader: Boolean = true) {
        unsubscribeFromWorkflows()
        workflow = null
        setState { copy(isLoading = showLoader) }

        authorizationSubscription = viewModelScope.launch {
            addDocumentsInteractor.authorizationHandler.authentication.filterNotNull()
                .collect { authentication ->
                    // Offered as the issuance was ended for the transport PIN flow, which follows
                    // its own workflow.
                    if (isIssuanceSuspended) {
                        logController.d(logTag) { "Authentication dropped: the issuance is suspended" }
                        return@collect
                    }
                    follow(authentication)
                }
        }
    }

    /**
     * Takes the user through [next]. The workflow followed so far is left: its outcome is no longer
     * this screen's to act on.
     */
    private fun follow(next: EidWorkflow<*>) {
        workflowSubscription?.cancel()
        resetCardExchange()
        workflow = next
        workflowSubscription = viewModelScope.launch {
            launch { next.request.filterNotNull().collect(::onRequest) }
            launch { next.reader.collect(::onReaderState) }
            onOutcome(next.outcome.await())
        }
    }

    /** Stops following the workflow, and ends it. */
    private fun leaveWorkflow() {
        workflowSubscription?.cancel()
        workflowSubscription = null
        workflow?.cancel()
        workflow = null
        resetCardExchange()
    }

    private fun onRequest(request: EidRequest) {
        logController.d(logTag) { "Request: ${request.javaClass.simpleName}" }
        when (request) {
            // The SDK asks for a card only while it holds none, and for a code only while it holds
            // one, so starting the scan answers it.
            is EidRequest.InsertCard -> {
                isCardPresent = false
                onCardRequested()
            }

            is EidRequest.AccessRights -> {
                acceptRightsIfReady()
                setState { copy(isLoading = false) }
            }

            is EidRequest.Pin -> {
                isCardPresent = true
                handlePinRequested(request)
            }

            is EidRequest.Can -> {
                isCardPresent = true
                handleCanRequested(request)
            }

            is EidRequest.NewPin -> {
                isCardPresent = true
                handleNewPinRequested(request)
            }

            // TODO WD-4778 a blocked PIN is not unblocked in this app. Until the flow for it is
            // in, it ends the flow rather than leaving it waiting on a code nobody can enter.
            is EidRequest.Puk -> {
                isCardPresent = true
                handleWorkflowFailure(PIN_BLOCKED)
            }
        }
    }

    private fun onCardRequested() {
        if (viewState.value.scanStatus != null) {
            presentVirtualCardIfRequested()
        }
        if (viewState.value.isLoading) {
            setState { copy(isLoading = false) }
        }

        // Re-arm reader mode so a card resting on the sensor (the CAN / Transport PIN follow-up
        // reads) is re-discovered when the SDK re-requests the card, without needing a re-tap.
        // Skip while a read is already in progress (readingProgress != null) so a repeated request
        // does not toggle reader mode mid-read.
        if (viewState.value.currentRoute.isNfcPrompt &&
            viewState.value.readingProgress == null
        ) {
            logController.d(logTag) { "Card requested while NFC prompt active -> restarting NFC reader" }
            setEffect { Effect.RestartNfcReader }
        }
    }

    private fun onReaderState(state: ReaderState) {
        val previous = lastReaderState
        lastReaderState = state
        when (state) {
            // Taken off the reader: a reader that never held a card reports none as well.
            ReaderState.NoCard -> if (previous != null && previous != ReaderState.NoCard) {
                handleCardRemoved()
            }

            ReaderState.CardPresent -> handleCardRecognized()
            is ReaderState.Reading -> handleReadingProgress(state.progress)
            is ReaderState.Paused -> handlePause()
        }
    }

    private fun handleCardRemoved() {
        isCardPresent = false
        if (viewState.value.currentRoute.isNfcPrompt) {
            setState {
                copy(
                    isLoading = false,
                    readingProgress = null,
                    // Lifted before its read was through: the sheet asks for it again.
                    scanStatus = if (scanStatus == CardScanStatus.IN_PROGRESS) {
                        CardScanStatus.READY_AGAIN
                    } else {
                        scanStatus
                    },
                    readyAgainCount = if (scanStatus == CardScanStatus.IN_PROGRESS) {
                        readyAgainCount + 1
                    } else {
                        readyAgainCount
                    },
                )
            }
        }
    }

    private suspend fun onOutcome(result: EidResult<*>) {
        logController.d(logTag) { "Outcome: $result" }
        when (result) {
            // Only this screen ends its workflows, and nothing waits on their end here.
            EidResult.Cancelled -> Unit
            is EidResult.Failure -> handleWorkflowFailure(result.failure.code)
            is EidResult.Success -> when (val value = result.value) {
                is AuthOutcome -> onAuthenticated(value.redirectUrl)
                PinChanged -> onPinChanged()
                else -> Unit
            }
        }
    }

    private suspend fun onAuthenticated(redirectUrl: String) {
        wipeEnteredCodes()

        if (viewState.value.scanStatus != null) {
            scanCompletion = ScanCompletion.Issuance(redirectUrl)
            setState {
                copy(
                    scanStatus = CardScanStatus.SUCCESS,
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
            navigateToWalletPinSet(redirectUrl)
        }
    }

    /** The SDK stays connected for a while: the issuance goes on with the new PIN. */
    private suspend fun onPinChanged() {
        if (viewState.value.scanStatus != null) {
            scanCompletion = ScanCompletion.PinChanged
            setState {
                copy(
                    scanStatus = CardScanStatus.SUCCESS,
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
            returnToIssuanceAfterPinChange()
        }
    }

    private fun handleCanRequested(request: EidRequest.Can) {
        // The CAN, not the eID PIN, is what the card is waiting for, so a PIN kept for a replay is
        // stale from here on.
        replayCode.wipe()

        // Asked again right after one was sent, or refused as malformed: the CAN was not taken.
        if (request.isRetry) {
            transitionTo(ReadCardScreenStep.EnterCan) {
                copy(
                    pinState = pinState.cleared(
                        capacity = CodeLength.CAN,
                        supportingText = resourceProvider.getString(R.string.pid_issuance_can_entry_error_wrong_can),
                    ),
                    isLoading = false,
                    readingProgress = null,
                    bottomSheetTitle = codeEntryTitle(ReadCardScreenStep.EnterCan),
                )
            }
            return
        }

        val routeWhenAsked = viewState.value.currentRoute
        if (routeWhenAsked == CardReaderRoute.NFC_SCAN_CAN && viewState.value.pinState.buffer.isComplete) {
            // Before the scan is started, starting it answers the request.
            if (isScanStarted()) {
                sendCode(request, Event.Can)
            }
            return
        }

        val nextStep = if (routeWhenAsked.isInPinEntryStage()) {
            ReadCardScreenStep.PinBlockedError
        } else {
            ReadCardScreenStep.EnterCan
        }
        transitionTo(nextStep) {
            copy(
                isLoading = false,
                readingProgress = null,
                bottomSheetTitle = codeEntryTitle(nextStep),
                pinState = pinState.cleared(capacity = CodeLength.CAN),
            )
        }
    }

    private fun handleNewPinRequested(request: EidRequest.NewPin) {
        val route = viewState.value.currentRoute
        when {
            // The SDK refused the new PIN sent last.
            request.isRetry -> transitionTo(ReadCardScreenStep.EnterNewPin) {
                copy(
                    pinState = pinState.cleared(CodeLength.EID_PIN),
                    isLoading = false,
                )
            }

            route == CardReaderRoute.NFC_SCAN_NEW_PIN && viewState.value.pinState.buffer.isComplete -> {
                // Before the scan is started, starting it answers the request.
                if (isScanStarted()) {
                    sendCode(request, Event.NewPin)
                }
            }

            route == CardReaderRoute.NFC_SCAN_TRANSPORT_PIN ->
                transitionTo(ReadCardScreenStep.EnterNewPinInstructions) {
                    copy(
                        isLoading = false,
                        bottomSheetTitle = codeEntryTitle(ReadCardScreenStep.EnterNewPinInstructions),
                        pinState = pinState.cleared(CodeLength.EID_PIN),
                    )
                }

            else -> transitionTo(ReadCardScreenStep.EnterNewPin) {
                copy(
                    isLoading = false,
                    bottomSheetTitle = codeEntryTitle(ReadCardScreenStep.EnterNewPin),
                    pinState = pinState.cleared(CodeLength.EID_PIN),
                )
            }
        }
    }

    fun onNfcTagDetected(tag: Tag) {
        logController.d(logTag) { "onNfcTagDetected: NFC tag detected, forwarding to reader" }
        eidEngine.onNfcTag(tag)
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

    /** Aborts the issuance on user exit or failure. */
    private fun cancelIssuanceAndIdentification() {
        cancelIdentification()
        addDocumentsInteractor.cancelIssuance()
        telemetry.endSpan(TelemetryConstants.ISSUANCE)
    }

    /**
     * Does not end the Issuance span: [onCleared] also runs on the normal forward navigation, while
     * wallet-core is still issuing.
     */
    private fun cancelIdentification() {
        resetCardExchange()
        workflow?.cancel()
    }

    private fun transitionTo(
        step: ReadCardScreenStep,
        flowType: CardReaderFlowType = viewState.value.activeFlowType,
        update: State.() -> State = { this },
    ) {
        val enteringNfcPrompt = step.toRoute().isNfcPrompt
        setState {
            val next = withStep(step = step, flowType = flowType)
                .copy(scanStatus = null)
                .update()
            // Reset any progress from a previous read so the next one starts clean.
            if (enteringNfcPrompt) next.copy(readingProgress = null) else next
        }
    }

    /** Whether the user has started the scan, which is what lets a code go to the card. */
    private fun isScanStarted(): Boolean = viewState.value.scanStatus != null

    private fun handleReadingProgress(progress: Int) {
        val scanStatus = viewState.value.scanStatus
        when {
            // A status that trails a finished read must not cover its outcome or its retry.
            scanStatus == CardScanStatus.SUCCESS || scanStatus == CardScanStatus.FAILED -> Unit

            else -> setState {
                copy(
                    readingProgress = progress,
                    bottomSheetTitle = null,
                    scanStatus = when (scanStatus) {
                        // The card has to be put back before the read goes on.
                        CardScanStatus.READY_AGAIN -> scanStatus
                        null -> null
                        else -> CardScanStatus.IN_PROGRESS
                    },
                )
            }
        }
    }

    private fun handleCardRecognized() {
        isCardPresent = true
        val currentState = viewState.value
        when (currentState.scanStatus) {
            CardScanStatus.READY,
            CardScanStatus.READY_AGAIN,
            -> setState { copy(scanStatus = CardScanStatus.IN_PROGRESS) }

            // A read that has not been asked for yet, or has ended, stays as it is: a card put down
            // before the scan is started is read once the user starts it.
            null,
            CardScanStatus.IN_PROGRESS,
            CardScanStatus.SUCCESS,
            CardScanStatus.FAILED,
            -> Unit
        }

        // A code goes to the card only once the scan is started and the SDK asks for it; otherwise
        // its request, or the start of the scan, is what sends it.
        if (isScanStarted()) {
            answerAwaitedCode()
        }
    }

    /** Each answer reads the entered code out of the buffer and erases it as it goes. */
    private fun answerAwaitedCode() {
        when (viewState.value.currentRoute) {
            CardReaderRoute.NFC_SCAN_CAN -> answerIfAwaited<EidRequest.Can>(Event.Can)
            CardReaderRoute.NFC_SCAN_EID_PIN,
            CardReaderRoute.NFC_SCAN_TRANSPORT_PIN,
            -> if (viewState.value.eidCardType.isVirtual) {
                discardEnteredPin()
            } else {
                answerIfAwaited<EidRequest.Pin>(Event.Pin)
            }

            CardReaderRoute.NFC_SCAN_NEW_PIN -> answerIfAwaited<EidRequest.NewPin>(Event.NewPin)
            else -> Unit
        }
    }

    private inline fun <reified T : EidRequest> answerIfAwaited(answer: Event) {
        awaited<T>()?.let { sendCode(it, answer) }
    }

    /** The request the SDK waits on, if it is a [T] no code has been queued for yet. */
    private inline fun <reified T : EidRequest> awaited(): T? =
        (workflow?.request?.value as? T)?.takeIf { it !== answeredRequest }

    /** Queues [answer], which hands [request] its code; see [answeredRequest]. */
    private fun sendCode(request: EidRequest, answer: Event) {
        answeredRequest = request
        setEvent(answer)
    }

    /** Answers the request the SDK waits on with [code] if it is a [T], and erases [code] if not. */
    private inline fun <reified T : EidRequest> answerAwaited(code: UserPin, answer: (T) -> Unit) {
        val request = workflow?.request?.value as? T
        if (request == null) {
            logController.d(logTag) { "Code dropped: the SDK does not wait for it" }
            code.discard()
            return
        }
        answer(request)
    }

    /** Forgets what the SDK was answered and what the reader reported, for a workflow that is gone or replaced. */
    private fun resetCardExchange() {
        answeredRequest = null
        lastReaderState = null
        // A new workflow has to discover the card for itself, even one still on the reader.
        isCardPresent = false
    }

    private fun startChangePinWorkflow() {
        follow(eidEngine.changePin(userRuntimeConfig.eidCardSource()))
    }

    /**
     * The SDK runs one workflow at a time, so the issuance's authentication is ended before the PIN
     * change starts. Going back to the issuance starts it again, see [handleBackNavigation].
     */
    private fun switchToChangePin() {
        // Already switched: a second tap would cancel the PIN change it just started.
        if (isIssuanceSuspended) {
            return
        }
        isIssuanceSuspended = true
        addDocumentsInteractor.cancelIssuance()
        wipeEnteredCodes()
        // The engine ends the issuance's authentication before it starts the PIN change.
        startChangePinWorkflow()
    }

    /**
     * The card now has the PIN the user just set, so the issuance the PIN change interrupted is
     * started again from its steps screen, the way it goes for a card whose PIN is already set.
     */
    private fun returnToIssuanceAfterPinChange() {
        scanCompletion = null
        transitionTo(step = ReadCardScreenStep.ProgressSteps, flowType = CardReaderFlowType.ISSUANCE) {
            copy(
                returnTarget = null,
                isBottomSheetOpen = false,
                isLoading = false,
                bottomSheetTitle = null,
                pinState = pinState.cleared(CodeLength.EID_PIN),
            )
        }
        resumeIssuance()
    }

    private fun resumeIssuance() {
        isIssuanceSuspended = false
        wipeEnteredCodes()
        restartIssuance()
    }

    /**
     * Ends whatever workflow runs and starts the issuance over, which asks for a fresh
     * authentication: the one that failed cannot be taken up again. The attestation is fresh too,
     * since it is signed over a server challenge that may not outlast the detour.
     */
    private fun restartIssuance() {
        // The new issuance brings its own authentication, which the engine starts once this ends.
        leaveWorkflow()
        addDocumentsInteractor.cancelIssuance()
        telemetry.endSpan(TelemetryConstants.ISSUANCE)
        performAttestation()
    }

    private fun retryWorkflow() {
        val hasScanFailed = viewState.value.scanStatus == CardScanStatus.FAILED
        // Off the scan, the restart is network work with nothing else on screen to show for it;
        // the restarted authentication's access rights take the loader away.
        setState { copy(errorDialog = null, isLoading = !hasScanFailed) }
        when (viewState.value.activeFlowType) {
            CardReaderFlowType.ISSUANCE -> {
                // The consent stays given and the PIN is kept for one more send, so a failed read
                // is run again from the scan it failed on.
                restartIssuance()
                if (hasScanFailed) {
                    startScan()
                }
            }

            CardReaderFlowType.CHANGE_PIN -> {
                startChangePinWorkflow()
                // A new change of PIN starts from the transport PIN.
                wipeEnteredCodes()
                transitionTo(ReadCardScreenStep.EnterTransportPin) {
                    copy(
                        bottomSheetTitle = resourceProvider.getString(R.string.pid_issuance_no_letter_forgot_info_title),
                        pinState = pinState.cleared(CodeLength.TRANSPORT_PIN),
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** The title of the sheet that asks for the code on [step]. */
    private fun codeEntryTitle(step: ReadCardScreenStep): String? = when (step) {
        ReadCardScreenStep.EnterCan,
        ReadCardScreenStep.PinBlockedError,
        -> R.string.pid_issuance_sheet_can_title

        ReadCardScreenStep.EnterNewPin,
        ReadCardScreenStep.EnterNewPinInstructions,
        ReadCardScreenStep.ConfirmNewPin,
        -> R.string.new_pin_set_bottom_sheet_title

        ReadCardScreenStep.EnterPin,
        ReadCardScreenStep.EnterTransportPin,
        ReadCardScreenStep.EnterCanSuccess,
        -> R.string.pid_issuance_sheet_eID_PIN_not_set_title

        // Not a code entry, so no code-entry sheet.
        ReadCardScreenStep.Consent,
        ReadCardScreenStep.NfcActivation,
        ReadCardScreenStep.EnterPuk,
        ReadCardScreenStep.IssuanceConsent,
        ReadCardScreenStep.Completed,
        ReadCardScreenStep.OnboardingCard,
        ReadCardScreenStep.OnboardingPin,
        ReadCardScreenStep.ProgressSteps,
        ReadCardScreenStep.NoPinLetterInfo,
        ReadCardScreenStep.TransportPinLetter,
        is ReadCardScreenStep.NfcScanPrompt,
        -> null
    }?.let(resourceProvider::getString)

    private fun handleWorkflowFailure(errorCode: String) {
        when (EidFailureRecovery.forErrorCode(errorCode)) {
            EidFailureRecovery.RETRY -> offerRetry(errorCode)
            EidFailureRecovery.ACTIVATE_AT_CITIZEN_OFFICE ->
                referToCitizenOffice(errorCode, R.string.eid_card_deactivated_paragraph)

            EidFailureRecovery.NEW_PIN_AT_CITIZEN_OFFICE ->
                referToCitizenOffice(errorCode, R.string.pid_issuance_no_letter_forgot_info_paragraph)

            EidFailureRecovery.NONE -> failWorkflow(errorCode)
        }
    }

    private fun leaveFailedFlow() {
        setEvent(Event.DismissError)
        exitToDashboard()
    }

    /**
     * On the scan, the failed sheet offers the retry; anywhere else, a dialog does. The codes stay
     * for the retry, and go when the flow is left.
     */
    private fun offerRetry(errorCode: String) {
        if (viewState.value.scanStatus != null) {
            setState {
                copy(scanStatus = CardScanStatus.FAILED, readingProgress = null, isLoading = false)
            }
            return
        }
        setState {
            copy(
                isLoading = false,
                errorDialog = GenericErrorDialogConfig(
                    titleRes = R.string.global_error_title,
                    bodyTextRes = R.string.global_error_paragraph,
                    errorCode = errorCode,
                    traceId = telemetry.currentTraceId(),
                    dismissable = false,
                    primaryButtonTextRes = R.string.nfc_scanning_system_sheet_nfc_paragraph_error_button,
                    onPrimaryButtonClick = { setEvent(Event.OnScanRetryClick) },
                    secondaryButtonTextRes = R.string.global_cancel_button,
                    onSecondaryButtonClick = ::leaveFailedFlow,
                ),
            )
        }
    }

    /** Nothing in the app can switch the card's online function on or replace a used-up PUK. */
    private fun referToCitizenOffice(errorCode: String, bodyTextRes: Int) {
        // Nothing in the app recovers from here, so the codes and the workflow waiting on them go.
        wipeEnteredCodes()
        workflow?.cancel()
        setState {
            copy(
                isLoading = false,
                scanStatus = null,
                readingProgress = null,
                pinState = pinState.cleared(),
                errorDialog = GenericErrorDialogConfig(
                    titleRes = R.string.global_error_title,
                    bodyTextRes = bodyTextRes,
                    errorCode = errorCode,
                    traceId = telemetry.currentTraceId(),
                    primaryButtonTextRes = R.string.global_office_button,
                    primaryButtonOpensExternalLink = true,
                    onPrimaryButtonClick = {
                        setEffect {
                            OpenLink(
                                citizenOfficeUrl.buildUpon()
                                    .fragment(resourceProvider.getLocale().language)
                                    .build()
                            )
                        }
                    },
                    secondaryButtonTextRes = R.string.global_cancel_button,
                    onSecondaryButtonClick = ::leaveFailedFlow,
                    onDismiss = ::leaveFailedFlow,
                ),
            )
        }
    }

    /**
     * The card lost contact mid-read: it is asked for again, and the engine has told the SDK to
     * carry on. After a failure the sheet's retry starts over, so the pause is left to it.
     */
    private fun handlePause() {
        val scanStatus = viewState.value.scanStatus
        if (scanStatus == CardScanStatus.FAILED || scanStatus == CardScanStatus.SUCCESS) {
            return
        }
        setState {
            copy(
                isLoading = false,
                readingProgress = null,
                scanStatus = scanStatus?.let { CardScanStatus.READY_AGAIN },
                readyAgainCount = if (scanStatus != null) readyAgainCount + 1 else readyAgainCount,
            )
        }
        if (viewState.value.currentRoute.isNfcPrompt) {
            setEffect { Effect.RestartNfcReader }
        }
    }

    /**
     * Ends a flow whose workflow is gone or cannot go on: anything sent to it from here on would be
     * refused, so the user is told and taken out instead of being left on a screen that waits.
     */
    private fun failWorkflow(errorCode: String) {
        unsubscribeFromWorkflows()
        wipeEnteredCodes()
        setState {
            copy(
                isLoading = false,
                isBottomSheetOpen = false,
                scanStatus = null,
                readingProgress = null,
                bottomSheetTitle = null,
                pinState = pinState.cleared(),
                errorDialog = GenericErrorDialogConfig(
                    titleRes = R.string.global_error_title,
                    bodyTextRes = R.string.global_error_paragraph,
                    errorCode = errorCode,
                    traceId = telemetry.currentTraceId(),
                    primaryButtonTextRes = R.string.global_error_prim_button,
                    onDismiss = ::leaveFailedFlow,
                    onPrimaryButtonClick = ::leaveFailedFlow,
                ),
            )
        }
        cancelIssuanceAndIdentification()
    }

    /** Erases the PIN typed for a simulated card, which is never sent anywhere. */
    private fun discardEnteredPin() {
        viewState.value.pinState.buffer.wipe()
        replayCode.wipe()
    }

    /** How many digits the code field on [this] step asks for. */
    private fun ReadCardScreenStep.codeLength(): Int = when (this) {
        ReadCardScreenStep.EnterTransportPin -> CodeLength.TRANSPORT_PIN
        ReadCardScreenStep.EnterCan -> CodeLength.CAN
        else -> CodeLength.EID_PIN
    }

    private fun handlePinRequested(request: EidRequest.Pin) {
        val currentState = viewState.value
        val isInputRejected = request.previousAttempt == PreviousAttempt.MALFORMED
        val pinRetryCounter = request.retryCounter

        if (!isInputRejected && settlePinRequestWithoutAsking(request)) {
            return
        }

        replayCode.wipe()

        when (currentState.currentRoute) {
            // A PIN change goes straight back to the transport PIN, below.
            CardReaderRoute.NFC_SCAN_CAN -> if (currentState.activeFlowType == CardReaderFlowType.ISSUANCE) {
                transitionTo(ReadCardScreenStep.EnterCanSuccess) {
                    copy(
                        bottomSheetTitle = codeEntryTitle(ReadCardScreenStep.EnterCanSuccess),
                        pinState = pinState.cleared(CodeLength.EID_PIN),
                        isLoading = false,
                        readingProgress = null,
                    )
                }
                return
            }

            // The same request, already acted on: the CAN success screen is waiting for the user to
            // move on to the PIN by hand, and a repeat must not move it or blame a code for it.
            CardReaderRoute.ENTER_CAN_SUCCESS -> return

            else -> Unit
        }

        // A wrong PIN always costs an attempt, so only a refused PIN or attempts already spent
        // earn a warning; an unknown counter arrives as 0 and says nothing.
        val pinStateWarning = when {
            !isInputRejected && pinRetryCounter !in 1 until FULL_PIN_RETRY_COUNTER -> null
            pinRetryCounter == 1 -> resourceProvider.getString(R.string.pid_issuance_puk_entry_warning_wrong_puk_1)
            else -> resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin)
        }
        val retryStep = pinRetryStep(currentState.activeFlowType)

        transitionTo(retryStep) {
            copy(
                bottomSheetTitle = codeEntryTitle(retryStep),
                pinState = pinState.cleared(
                    capacity = retryStep.codeLength(),
                    supportingText = pinStateWarning,
                ),
                isLoading = false,
                readingProgress = null,
            )
        }
    }

    /**
     * Answers a PIN request with a code already at hand, or leaves it for the entry in progress.
     * Returns false when the user has to be asked for the PIN.
     */
    private fun settlePinRequestWithoutAsking(request: EidRequest.Pin): Boolean {
        val currentState = viewState.value
        val route = currentState.currentRoute
        val followsPin = request.previousAttempt != PreviousAttempt.NONE
        val hasCodeAtHand = if (followsPin) {
            // Asked again with no attempt spent: the read dropped before the card judged the PIN.
            replayCode.length > 0 && request.previousAttempt == PreviousAttempt.NOT_JUDGED
        } else {
            // The PIN typed for this request, or the copy of one the SDK refused before it was ready.
            currentState.pinState.buffer.isComplete || replayCode.length > 0
        }
        if (route.canReplayCachedPin() && hasCodeAtHand) {
            // Held until the scan is started, which then answers it: a card put down early is not
            // read before the user asks for it.
            if (isScanStarted()) {
                sendCode(request, Event.Pin)
            }
            return true
        }
        // Still being typed: it goes once the card is presented.
        return !followsPin &&
            (route == CardReaderRoute.ENTER_PIN || route == CardReaderRoute.ENTER_TRANSPORT_PIN)
    }

    /** Until it is changed, the PIN a PIN change asks for is the transport PIN, CAN or not. */
    private fun pinRetryStep(flowType: CardReaderFlowType): ReadCardScreenStep = when (flowType) {
        CardReaderFlowType.CHANGE_PIN -> ReadCardScreenStep.EnterTransportPin
        CardReaderFlowType.ISSUANCE -> ReadCardScreenStep.EnterPin
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
                        if (isIssuanceSuspended && target.flowType == CardReaderFlowType.ISSUANCE) {
                            resumeIssuance()
                        }
                        transitionTo(step = target.route.toStep(), flowType = target.flowType) {
                            copy(returnTarget = target.previous)
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
        // An attestation still in flight would otherwise start the issuance the user just left.
        attestationJob?.cancel()
        unsubscribeFromWorkflows()
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
                )
            )
        }
    }
    private fun readCredentialAttributes(): List<CredentialAttribute> {
        val document = walletCoreDocumentsController.getMainPidDocument() ?: return emptyList()
        val details = DocumentDetailsTransformer.transformToDocumentDetailsDomain(
            document = document,
            resourceProvider = resourceProvider,
        ).getOrNull() ?: return emptyList()

        return details.detailsItems
            .toListItemData(
                resourceProvider = resourceProvider,
                documentFormat = document.format,
                documentIdentifier = document.toDocumentIdentifier(),
            )
            .mapNotNull { item ->
                val label = item.overlineText ?: return@mapNotNull null
                val value = (item.mainContentData as? ListItemMainContentData.Text)?.text
                    ?: return@mapNotNull null
                CredentialAttribute(label = label, value = value)
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
            )
        }
    }

}

private const val FULL_PIN_RETRY_COUNTER = 3

/** How long the attestation may take before the patience screen replaces the loader (WD-5013). */
private val HSM_SLOW_AFTER = 5.seconds

/** How long the patience screen waits before it offers a retry (WD-5013). */
private val HSM_DELAY_RETRY_AFTER = 20.seconds

private sealed interface ScanCompletion {

    /** The card was read for the issuance, which continues at [redirectUrl]. */
    data class Issuance(val redirectUrl: String) : ScanCompletion

    /** The card's PIN was changed, after which the issuance it interrupted goes on. */
    data object PinChanged : ScanCompletion
}