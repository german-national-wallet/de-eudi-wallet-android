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

import android.net.Uri
import org.sprind.wallet.analyticslogic.controller.Telemetry
import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import eu.europa.ec.businesslogic.config.ConfigLogic
import org.sprind.wallet.businesslogic.config.EidCardType
import eu.europa.ec.businesslogic.config.EnvironmentConfig
import org.sprind.wallet.businesslogic.config.UserRuntimeConfig
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.commonfeature.interactor.IssuanceEvent
import eu.europa.ec.corelogic.controller.IssuanceMethod
import eu.europa.ec.corelogic.handler.AusweisSdkAuthorizationHandler
import eu.europa.ec.corelogic.handler.reader.CardReaderInteractor
import eu.europa.ec.corelogic.handler.reader.WorkflowEvent
import eu.europa.ec.corelogic.interactor.walletattestation.WalletAttestationInteractor
import eu.europa.ec.corelogic.interactor.walletattestation.WalletAttestationResult
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.extension.toFlow
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.sprind.wallet.businesslogic.model.UserPinImpl
import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowType
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute
import org.sprind.wallet.cardreaderfeature.domain.CardScanStatus
import org.sprind.wallet.cardreaderfeature.ui.document.privacy.PrivacyPolicyRoute
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class)
class ReadCardViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var addDocumentInteractor: AddDocumentInteractor


    private lateinit var issuanceEventsFlow: MutableSharedFlow<IssuanceEvent>
    private lateinit var workflowEventsFlow: MutableStateFlow<WorkflowEvent>
    private lateinit var authorizationRequestFlow: MutableSharedFlow<String>
    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var cardReaderInteractor: CardReaderInteractor

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    @Mock
    private lateinit var authHandler: AusweisSdkAuthorizationHandler

    @Mock
    private lateinit var telemetry: Telemetry

    @Mock
    private lateinit var userRuntimeConfig: UserRuntimeConfig

    @Mock
    private lateinit var walletAttestationInteractor: WalletAttestationInteractor

    @Mock
    private lateinit var mockAttestationSpec: WalletInstanceAttestationSpec

    @Mock
    private lateinit var configLogic: ConfigLogic

    @Mock
    private lateinit var environmentConfig: EnvironmentConfig

    private lateinit var closeable: AutoCloseable

    private lateinit var viewModel: ReadCardViewModel

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_eid_pin_not_set_title))
            .thenReturn("info")
        whenever(resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin))
            .thenReturn("wrong pin")
        doNothing().`when`(logController).d(any(), any<() -> String>())
        doNothing().`when`(logController).e(any(), any<() -> String>())
        whenever(configLogic.environmentConfig).thenReturn(environmentConfig)
        whenever(configLogic.environmentConfig.pidIssuerURL).thenReturn("https://demo.pid-provider.bundesdruckerei.de")
        whenever(userRuntimeConfig.eidCardType).thenReturn(EidCardType.PHYSICAL)

        issuanceEventsFlow = MutableSharedFlow(extraBufferCapacity = 16)
        whenever(addDocumentInteractor.issuanceEvents).thenReturn(issuanceEventsFlow)

        workflowEventsFlow = MutableStateFlow(WorkflowEvent.Idle)
        whenever(cardReaderInteractor.eidFlow).thenReturn(workflowEventsFlow)

        authorizationRequestFlow = MutableSharedFlow(extraBufferCapacity = 16)
        whenever(authHandler.authorizationRequest).thenReturn(authorizationRequestFlow)

        whenever(configLogic.environmentConfig).thenReturn(environmentConfig)
        whenever(environmentConfig.pidIssuerURL).thenReturn("https://demo.pid-provider.bundesdruckerei.de")
        whenever(addDocumentInteractor.authorizationHandler).thenReturn(authHandler)
        whenever(addDocumentInteractor.authorizationHandler.authorizationRequest).thenReturn(authorizationRequestFlow)

        whenever(configLogic.environmentConfig).thenReturn(environmentConfig)
        whenever(environmentConfig.pidIssuerURL).thenReturn("https://demo.pid-provider.bundesdruckerei.de")
        runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                flowOf(WalletAttestationResult.Success(walletInstanceAttestationSpec = mockAttestationSpec))
            )
        }
        whenever(addDocumentInteractor.authorizationHandler).thenReturn(authHandler)
        whenever(addDocumentInteractor.authorizationHandler.authorizationRequest).thenReturn(authorizationRequestFlow)


        viewModel = createViewModel()
    }

    /**
     * Kept separate from [setUp] so a test can re-stub the mocks it depends on — the
     * eID card type, for instance, is read when the initial state is built.
     */
    private fun createViewModel() = ReadCardViewModel(
        configLogic = configLogic,
        resourceProvider = resourceProvider,
        cardReaderInteractor = cardReaderInteractor,
        walletCoreDocumentsController = walletCoreDocumentsController,
        walletAttestationInteractor = walletAttestationInteractor,
        logController = logController,
        telemetry = telemetry,
        userRuntimeConfig = userRuntimeConfig,
        flowType = IssuanceFlowUiConfig.NO_DOCUMENT,
        credentialTypes = setOf(CredentialConfigurationIdentifier("mdoc")),
        addDocumentsInteractor = addDocumentInteractor,
    )

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `Initially loading is true in the view state`() {
        assertTrue(viewModel.viewState.value.isLoading)
        assertEquals(CardReaderRoute.ONBOARDING_CARD, viewModel.viewState.value.currentRoute)
        assertEquals(CardReaderFlowType.ISSUANCE, viewModel.viewState.value.activeFlowType)
    }

    @Test
    fun `when state onBackAction invoked, then Navigation Pop is emitted`() = runTest {
        viewModel.viewState.value.onBackAction?.invoke()
        viewModel.effect.runFlowTest {
            val result = awaitItem()
            assertTrue(result is Effect.Navigation.Pop)
        }
    }

    @Test
    fun `when Init, starts card reader, performs attestation, and starts issuance`() = coroutineRule.runTest {
        viewModel.setEvent(Event.Init)

        verify(cardReaderInteractor).startCardReader()
        verify(walletAttestationInteractor).generateAttestation()

        verify(addDocumentInteractor).startIssueDocumentAttested(
            issuanceMethod = IssuanceMethod.OPENID4VCI,
            configIds = setOf(CredentialConfigurationIdentifier("mdoc")),
            issuerId = "https://demo.pid-provider.bundesdruckerei.de",
            walletInstanceAttestationSpec = mockAttestationSpec
        )
    }

    @Test
    fun `when authorization request arrives before ReadyToStart then authentication waits for SDK readiness`() = coroutineRule.runTest {
        val sdkFlow = MutableSharedFlow<WorkflowEvent>(extraBufferCapacity = 16)
        whenever(cardReaderInteractor.eidFlow).thenReturn(sdkFlow)

        viewModel.setEvent(Event.Init)
        authorizationRequestFlow.emit("https://example.com/auth")
        coroutineRule.testScope.testScheduler.runCurrent()

        verify(cardReaderInteractor, never()).startAuthentication(any())

        sdkFlow.emit(WorkflowEvent.ReadyToStart)
        coroutineRule.testScope.testScheduler.runCurrent()

        verify(cardReaderInteractor, times(1)).startAuthentication(any())
    }


    @Test
    fun `when Attestation Fails, then show Error Dialog`() = runTest {
        // GIVEN attestation fails
        whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
            flowOf(WalletAttestationResult.Failure("UNKNOWN", "trace"))
        )

        // WHEN
        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent() // process flow

        // THEN
        val state = viewModel.viewState.value
        assertTrue("Should have error dialog", state.errorDialog != null)
        assertFalse("Should stop loading", state.isLoading)
    }

    @Test
    fun `when InitEnterPin event, then go to EnterPin without starting reader`() {
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)

        verify(cardReaderInteractor, never()).startCardReader()

        with(viewModel.viewState.value) {
            assertEquals(ReadCardScreenStep.EnterPin, currentStep)
            assertEquals(CardReaderRoute.ENTER_PIN, currentRoute)
            assertEquals("info", bottomSheetTitle)
        }
    }

    @Test
    fun `when consent accepted while nfc is off, then the pin entry is still shown`() {
        viewModel.setEvent(Event.OnResume(isNfcEnabled = false))

        viewModel.setEvent(Event.AcceptRightsAndEnterPin)

        // NFC is not needed to type a PIN, so it no longer gates this step.
        verify(cardReaderInteractor, times(1)).acceptRights()
        assertEquals(CardReaderRoute.ENTER_PIN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when the scan is asked for while nfc is off, then nfc activation is shown`() {
        viewModel.setEvent(Event.OnResume(isNfcEnabled = false))
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnContinueClickCardPin)

        viewModel.setEvent(Event.OnStartScanningClick)

        with(viewModel.viewState.value) {
            assertEquals(CardReaderRoute.NFC_ACTIVATION, currentRoute)
            // Remembered so switching NFC on resumes the scan that asked for it.
            assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, returnTarget?.route)
        }
    }

    @Test
    fun `when consent accepted with a virtual card, then nfc state does not gate the flow`() {
        whenever(userRuntimeConfig.eidCardType).thenReturn(EidCardType.VIRTUAL)
        viewModel = createViewModel()
        viewModel.setEvent(Event.OnResume(isNfcEnabled = false))

        viewModel.setEvent(Event.AcceptRightsAndEnterPin)

        verify(cardReaderInteractor, times(1)).acceptRights()
        assertEquals(CardReaderRoute.ENTER_PIN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when enable nfc button clicked, then the nfc settings are opened`() = runTest {
        viewModel.setEvent(Event.OnEnableNfcButtonClick)

        viewModel.effect.runFlowTest {
            assertEquals(Effect.OpenNfcSettings, awaitItem())
        }
    }

    @Test
    fun `when returning with nfc enabled, then the scan that asked for it resumes`() {
        viewModel.setEvent(Event.OnResume(isNfcEnabled = false))
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnContinueClickCardPin)
        viewModel.setEvent(Event.OnStartScanningClick)
        assertEquals(CardReaderRoute.NFC_ACTIVATION, viewModel.viewState.value.currentRoute)

        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))

        with(viewModel.viewState.value) {
            assertEquals(ReadCardScreenStep.NfcScanPrompt.EidPin, currentStep)
            assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, currentRoute)
            assertTrue(isNfcEnabled)
            assertNull(returnTarget)
        }
    }

    @Test
    fun `when resumed with nfc enabled outside the activation step, then the step is kept`() {
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)

        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))

        verify(cardReaderInteractor, times(1)).acceptRights()
        assertEquals(CardReaderRoute.ENTER_PIN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when Pin event set, then providePin is called and loading becomes true`() {
        val pin = "123456"
        doNothing().`when`(cardReaderInteractor).providePin(UserPinImpl(pin))

        viewModel.setEvent(Event.Init)
        viewModel.typeIntoCodeField(pin)
        viewModel.setEvent(Event.Pin)
        assertTrue(viewModel.viewState.value.isLoading)
        verify(cardReaderInteractor, times(1)).providePin(UserPinImpl(pin))
    }

    @Test
    fun `when Can event set, then provideCan is called and loading becomes true`() {
        val can = "654321"
        doNothing().`when`(cardReaderInteractor).provideCan(UserPinImpl(can))

        viewModel.typeIntoCodeField(can)
        viewModel.setEvent(Event.Can)

        verify(cardReaderInteractor, times(1)).provideCan(UserPinImpl(can))
        assertTrue(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `when the pin is complete, then the flow waits for the continue action`() {
        viewModel.setEvent(Event.Init)
        // Accepting the consent is what opens the PIN entry, and it clears the field.
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)
        viewModel.typeIntoCodeField("123456")

        viewModel.setEvent(Event.OnPinUpdate)

        assertEquals(CardReaderRoute.ENTER_PIN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when the pin is complete and continue is pressed, then NFC prompt for eID PIN is shown`() {
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnContinueClickCardPin)
        assertEquals(ReadCardScreenStep.NfcScanPrompt.EidPin, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when transport pin flow starts, state switches active flow and progress`() {
        whenever(resourceProvider.getString(R.string.pid_issuance_no_letter_forgot_info_title))
            .thenReturn("transportPinInfo")

        viewModel.setEvent(Event.StartTransportPin)

        with(viewModel.viewState.value) {
            assertEquals(CardReaderFlowType.CHANGE_PIN, activeFlowType)
            assertEquals(CardReaderRoute.ENTER_TRANSPORT_PIN, currentRoute)
            assertEquals(2, progress.currentStep)
            assertEquals(flowDefinition.routes.size, progress.totalSteps)
        }
    }

    @Test
    fun `when back pressed on onboarding pin, then returns to onboarding card`() {
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.OnContinueClickOnboardingPin)

        viewModel.setEvent(Event.Pop)

        assertEquals(ReadCardScreenStep.OnboardingCard, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderRoute.ONBOARDING_CARD, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when back pressed on onboarding card, then exits to dashboard`() = runTest {
        viewModel.setEvent(Event.Init)

        viewModel.setEvent(Event.Pop)

        viewModel.effect.runFlowTest {
            val result = awaitItem()
            assertTrue(result is Effect.Navigation.Pop)
        }
        verify(cardReaderInteractor, times(1)).cancelIdentification()
    }

    @Test
    fun `when change pin subflow opened from onboarding pin and back pressed, then returns to origin screen`() {
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.OnContinueClickOnboardingPin)
        viewModel.setEvent(Event.TransportPinLetter)

        viewModel.setEvent(Event.Pop)

        assertEquals(ReadCardScreenStep.OnboardingPin, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderFlowType.ISSUANCE, viewModel.viewState.value.activeFlowType)
        assertEquals(CardReaderRoute.ONBOARDING_PIN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when NFC prompt visible and CardRecognized, then current PIN is provided`() = runTest {
        val pin = "123456"
        doNothing().`when`(cardReaderInteractor).providePin(UserPinImpl(pin))

        val sdkFlow = MutableSharedFlow<WorkflowEvent>()
        whenever(cardReaderInteractor.eidFlow).thenReturn(sdkFlow)

        // Enter PIN and show NFC prompt
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.OnContinueClickOnboardingPin)
        viewModel.typeIntoCodeField(pin)
        viewModel.setEvent(Event.OnContinueClickCardPin)
        assertEquals(ReadCardScreenStep.NfcScanPrompt.EidPin, viewModel.viewState.value.currentStep)

        // Simulate card recognized -> then should call providePin with the cached value
        sdkFlow.emit(WorkflowEvent.CardRecognized)

        verify(cardReaderInteractor, times(1)).providePin(UserPinImpl(pin))
        assertTrue(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `when AuthenticationCompleted, then Completed state, effect to SwitchScreen, and cancel identification`() =
        runTest {
            val flow =
                MutableStateFlow<WorkflowEvent>(WorkflowEvent.AuthenticationCompleted("redirect"))
            whenever(cardReaderInteractor.eidFlow).thenReturn(flow)

            viewModel.setEvent(Event.Init)

            assertEquals(ReadCardScreenStep.Completed, viewModel.viewState.value.currentStep)
            assertFalse(viewModel.viewState.value.isLoading)
            assertNull(viewModel.viewState.value.bottomSheetTitle)
            with(viewModel.viewState.value.pinState) {
                assertFalse(isValid)
                assertNull(supportingText)
                assertEquals(0, buffer.length)
            }
            viewModel.effect.runFlowTest {
                val result = awaitItem()
                assertTrue(result is Effect.Navigation.SwitchScreen)
            }
            verify(cardReaderInteractor, times(1)).cancelIdentification()
        }

    @Test
    fun `when CardRemoved while loading, then loader is disabled`() = runTest {
        val flow = MutableSharedFlow<WorkflowEvent>()
        whenever(cardReaderInteractor.eidFlow).thenReturn(flow)
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)
        viewModel.setEvent(Event.OnContinueClickOnboardingPin)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnContinueClickCardPin) // show NFC prompt
        assertEquals(ReadCardScreenStep.NfcScanPrompt.EidPin, viewModel.viewState.value.currentStep)

        // Make it "loading" by simulating recognition
        flow.emit(WorkflowEvent.CardRecognized)
        assertTrue(viewModel.viewState.value.isLoading)

        flow.emit(WorkflowEvent.CardRemoved)
        assertFalse(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `when ReadingProgress, then readingProgress state reflects the reported value`() = runTest {
        val flow = MutableSharedFlow<WorkflowEvent>(extraBufferCapacity = 16)
        whenever(cardReaderInteractor.eidFlow).thenReturn(flow)
        viewModel.setEvent(Event.Init)

        flow.emit(WorkflowEvent.ReadingProgress(60))

        assertEquals(60, viewModel.viewState.value.readingProgress)
    }

    @Test
    fun `when the scan is asked for, then the NFC reader is restarted to re-discover a resting card`() =
        runTest {
            val flow = MutableSharedFlow<WorkflowEvent>(extraBufferCapacity = 16)
            whenever(cardReaderInteractor.eidFlow).thenReturn(flow)
            viewModel.setEvent(Event.Init)
            viewModel.setEvent(Event.AcceptRightsAndEnterPin)
            viewModel.setEvent(Event.OnContinueClickOnboardingPin)

            // Continuing with a complete PIN transitions to the NFC scan prompt.
            viewModel.typeIntoCodeField("123456")
            viewModel.setEvent(Event.OnContinueClickCardPin)
            assertEquals(
                ReadCardScreenStep.NfcScanPrompt.EidPin,
                viewModel.viewState.value.currentStep
            )

            viewModel.effect.runFlowTest {
                viewModel.setEvent(Event.OnStartScanningClick)
                assertEquals(Effect.RestartNfcReader, awaitItem())
            }
        }

    @Test
    fun `when InsertCardRequested while an NFC prompt is active, then the NFC reader is restarted`() =
        runTest {
            val flow = MutableSharedFlow<WorkflowEvent>(extraBufferCapacity = 16)
            whenever(cardReaderInteractor.eidFlow).thenReturn(flow)

            viewModel.setEvent(Event.Init)
            // Navigate to the CAN NFC prompt (a follow-up read after a first read).
            viewModel.setEvent(Event.OnEnterCanButtonPress)
            viewModel.typeIntoCodeField("123456")
            viewModel.setEvent(Event.OnCanUpdate)
            assertEquals(CardReaderRoute.NFC_SCAN_CAN, viewModel.viewState.value.currentRoute)

            viewModel.effect.runFlowTest {
                // The SDK re-requesting the card re-arms the reader, so a card resting on the
                // sensor from the previous read is re-discovered without a re-tap.
                flow.emit(WorkflowEvent.InsertCardRequested)
                assertEquals(Effect.RestartNfcReader, awaitItem())
            }
        }

    @Test
    fun `when InsertCardRequested and no NFC prompt is active, then the NFC reader is not restarted`() =
        runTest {
            val flow = MutableSharedFlow<WorkflowEvent>(extraBufferCapacity = 16)
            whenever(cardReaderInteractor.eidFlow).thenReturn(flow)

            viewModel.setEvent(Event.Init)
            assertEquals(CardReaderRoute.ONBOARDING_CARD, viewModel.viewState.value.currentRoute)

            viewModel.effect.runFlowTest {
                flow.emit(WorkflowEvent.InsertCardRequested)
                expectNoEvents()
            }
        }

    @Test
    fun `when EnterCan event first time, then show PinBlockedError with CAN bottom sheet title`() {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_can_title))
            .thenReturn("bottomSheetInfoText")

        val flow = MutableStateFlow<WorkflowEvent>(WorkflowEvent.EnterCan)
        whenever(cardReaderInteractor.eidFlow).thenReturn(flow)

        viewModel.setEvent(Event.Init)

        assertEquals(ReadCardScreenStep.PinBlockedError, viewModel.viewState.value.currentStep)
        assertEquals("bottomSheetInfoText", viewModel.viewState.value.bottomSheetTitle)
        assertFalse(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `when EnterCanError, then stay on EnterCan with supporting error text`() {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_can_title))
            .thenReturn("bottomSheetInfoText")
        whenever(resourceProvider.getString(R.string.pid_issuance_can_entry_error_wrong_can))
            .thenReturn("wrongCan")

        val flow = MutableStateFlow<WorkflowEvent>(WorkflowEvent.EnterCanError)
        whenever(cardReaderInteractor.eidFlow).thenReturn(flow)

        viewModel.setEvent(Event.Init)

        assertEquals(ReadCardScreenStep.EnterCan, viewModel.viewState.value.currentStep)
        assertEquals("bottomSheetInfoText", viewModel.viewState.value.bottomSheetTitle)
        assertEquals("wrongCan", viewModel.viewState.value.pinState.supportingText)
        assertFalse(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `when EnterCan event repeats on enter can route, then supporting wrong can text is shown`() {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_can_title))
            .thenReturn("bottomSheetInfoText")
        whenever(resourceProvider.getString(R.string.pid_issuance_can_entry_error_wrong_can))
            .thenReturn("wrongCan")

        val flow = MutableSharedFlow<WorkflowEvent>(extraBufferCapacity = 16)
        whenever(cardReaderInteractor.eidFlow).thenReturn(flow)

        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()
        viewModel.setEvent(Event.OnEnterCanButtonPress)

        flow.tryEmit(WorkflowEvent.EnterCan)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertEquals(ReadCardScreenStep.EnterCan, viewModel.viewState.value.currentStep)
        assertEquals("wrongCan", viewModel.viewState.value.pinState.supportingText)
        assertEquals("bottomSheetInfoText", viewModel.viewState.value.bottomSheetTitle)
        assertFalse(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `when OnEnterCanButtonPress, then go to EnterCan and clear pin state`() = runTest {
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        assertEquals(ReadCardScreenStep.EnterCan, viewModel.viewState.value.currentStep)
        with(viewModel.viewState.value.pinState) {
            assertFalse(isValid)
            assertNull(supportingText)
            assertEquals(0, buffer.length)
        }
    }

    @Test
    fun `when OnCanUpdate with 6 digits, set NFC prompt for CAN`() {
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)
        assertEquals(ReadCardScreenStep.NfcScanPrompt.Can, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderRoute.NFC_SCAN_CAN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when OnCanUpdate with less than 6 digits, just update pin state`() {
        viewModel.typeIntoCodeField("12345")
        viewModel.setEvent(Event.OnCanUpdate)
        assertEquals(5, viewModel.viewState.value.pinState.buffer.length)
    }

    @Test
    fun `when NFC prompt for CAN and CardRecognized, provide CAN`() = runTest {
        val can = "123456"
        doNothing().`when`(cardReaderInteractor).provideCan(UserPinImpl(can))

        val flow = MutableSharedFlow<WorkflowEvent>()
        whenever(cardReaderInteractor.eidFlow).thenReturn(flow)

        viewModel.setEvent(Event.Init)
        // go to CAN prompt
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField(can)
        viewModel.setEvent(Event.OnCanUpdate)
        assertEquals(ReadCardScreenStep.NfcScanPrompt.Can, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderRoute.NFC_SCAN_CAN, viewModel.viewState.value.currentRoute)

        // card recognized -> provide CAN internally this will trigger the call to provideCan
        flow.emit(WorkflowEvent.CardRecognized)

        verify(cardReaderInteractor, times(1)).provideCan(UserPinImpl(can))
        assertTrue(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `when pin requested after nfc can prompt, route falls back to can success`() = runTest {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_eid_pin_not_set_title))
            .thenReturn("info")
        whenever(resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin))
            .thenReturn("wrongPin")

        val flow = MutableSharedFlow<WorkflowEvent>()
        whenever(cardReaderInteractor.eidFlow).thenReturn(flow)

        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)

        flow.emit(WorkflowEvent.PinRequested(3))
        coroutineRule.testScope.testScheduler.runCurrent()

        assertEquals(CardReaderRoute.ENTER_CAN_SUCCESS, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when EnterCan arrives after PinRequested during CAN flow, then stay on EnterCan`() = runTest {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_can_title))
            .thenReturn("bottomSheetInfoText")
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_eid_pin_not_set_title))
            .thenReturn("info")
        whenever(resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin))
            .thenReturn("wrongPin")

        val flow = MutableSharedFlow<WorkflowEvent>(extraBufferCapacity = 16)
        whenever(cardReaderInteractor.eidFlow).thenReturn(flow)

        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)

        flow.emit(WorkflowEvent.PinRequested(1))
        coroutineRule.testScope.testScheduler.runCurrent()
        assertEquals(CardReaderRoute.ENTER_CAN_SUCCESS, viewModel.viewState.value.currentRoute)

        flow.emit(WorkflowEvent.EnterCan)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertEquals(ReadCardScreenStep.EnterCan, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderRoute.ENTER_CAN, viewModel.viewState.value.currentRoute)
        assertEquals("bottomSheetInfoText", viewModel.viewState.value.bottomSheetTitle)
    }

    @Test
    fun `when PIN wrong twice then SDK requests CAN, first EnterCan shows PinBlockedError and retry stays on EnterCan`() = runTest {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_can_title))
            .thenReturn("bottomSheetInfoText")
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_eid_pin_not_set_title))
            .thenReturn("info")
        whenever(resourceProvider.getString(R.string.pid_issuance_puk_entry_warning_wrong_puk_1))
            .thenReturn("wrongPuk")
        whenever(resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin))
            .thenReturn("wrongPin")

        val flow = MutableSharedFlow<WorkflowEvent>(extraBufferCapacity = 16)
        whenever(cardReaderInteractor.eidFlow).thenReturn(flow)

        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()

        // first wrong PIN: retry counter drops to 2
        flow.emit(WorkflowEvent.PinRequested(2))
        coroutineRule.testScope.testScheduler.runCurrent()
        assertEquals(ReadCardScreenStep.EnterPin, viewModel.viewState.value.currentStep)

        // second wrong PIN: retry counter drops to 1, "last try" warning shown
        flow.emit(WorkflowEvent.PinRequested(1))
        coroutineRule.testScope.testScheduler.runCurrent()
        assertEquals(ReadCardScreenStep.EnterPin, viewModel.viewState.value.currentStep)

        // SDK now requires CAN -> first EnterCan must show the PinBlockedError intro screen
        flow.emit(WorkflowEvent.EnterCan)
        coroutineRule.testScope.testScheduler.runCurrent()
        assertEquals(ReadCardScreenStep.PinBlockedError, viewModel.viewState.value.currentStep)
        assertEquals("bottomSheetInfoText", viewModel.viewState.value.bottomSheetTitle)

        // user taps "Enter CAN" -> route becomes ENTER_CAN
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        assertEquals(ReadCardScreenStep.EnterCan, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderRoute.ENTER_CAN, viewModel.viewState.value.currentRoute)

        // user types the 6-digit CAN -> route advances to NFC_SCAN_CAN
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)
        assertEquals(CardReaderRoute.NFC_SCAN_CAN, viewModel.viewState.value.currentRoute)

        // during the CAN handshake the SDK re-requests PIN, then re-requests CAN;
        // the user must NOT be bounced back to PinBlockedError here
        flow.emit(WorkflowEvent.PinRequested(1))
        coroutineRule.testScope.testScheduler.runCurrent()

        flow.emit(WorkflowEvent.EnterCan)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertEquals(ReadCardScreenStep.EnterCan, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderRoute.ENTER_CAN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when OnCloseButtonClick event, then asks before cancelling the flow`() = runTest {
        viewModel.setEvent(Event.Init)

        viewModel.setEvent(Event.OnCloseButtonClick)

        assertTrue(viewModel.viewState.value.isCancelFlowDialogVisible)
    }

    @Test
    fun `when the consent is rejected, then asks before cancelling the flow`() = runTest {
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.Consent)

        viewModel.setEvent(Event.OnCloseButtonClick)

        assertTrue(viewModel.viewState.value.isCancelFlowDialogVisible)
        assertEquals(CardReaderRoute.CONSENT, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when DismissCancelFlowDialog event, then leaves the flow where it is`() = runTest {
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.OnCloseButtonClick)

        viewModel.setEvent(Event.DismissCancelFlowDialog)

        assertFalse(viewModel.viewState.value.isCancelFlowDialogVisible)
        assertEquals(CardReaderRoute.ONBOARDING_CARD, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when the card pin is known, then shows the steps overview before the consent`() = runTest {
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.OnContinueClickOnboardingPin)

        viewModel.setEvent(Event.OnContinueClickProgressSteps)

        assertEquals(CardReaderRoute.PROGRESS_STEPS, viewModel.viewState.value.currentRoute)

        viewModel.setEvent(Event.Consent)

        assertEquals(CardReaderRoute.CONSENT, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when OnNoPinLetterButtonClick event, then shows the citizen office dead end`() = runTest {
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.OnContinueClickOnboardingPin)

        viewModel.setEvent(Event.OnNoPinLetterButtonClick)

        assertEquals(CardReaderRoute.NO_PIN_LETTER_INFO, viewModel.viewState.value.currentRoute)
        assertEquals(
            CardReaderRoute.ONBOARDING_PIN,
            viewModel.viewState.value.returnTarget?.route,
        )
    }

    @Test
    fun `when back from the citizen office dead end, then returns to the card pin question`() = runTest {
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.OnContinueClickOnboardingPin)
        viewModel.setEvent(Event.OnNoPinLetterButtonClick)

        viewModel.setEvent(Event.Pop)

        assertEquals(CardReaderRoute.ONBOARDING_PIN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when UpdateBottomSheetState event, then toggles bottom sheet`() = runTest {
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.BottomSheet.UpdateBottomSheetState(true))
        assertTrue(viewModel.viewState.value.isBottomSheetOpen)

        viewModel.setEvent(Event.BottomSheet.UpdateBottomSheetState(false))
        assertFalse(viewModel.viewState.value.isBottomSheetOpen)
    }

    @Test
    fun `when OnPrivacyPolicyButtonClick event, then opens the policy in the app`() = runTest {
        viewModel.setEvent(Event.OnPrivacyPolicyButtonClick)

        viewModel.effect.runFlowTest {
            val result = awaitItem()
            assertEquals(
                Effect.Navigation.NavigateToPrivacyPolicy(PrivacyPolicyRoute.BLANK_PAGE),
                result,
            )
        }
    }

    @Test
    fun `when OnSearchCitizenOfficeButtonClick german locale, then open german link`() = runTest {
        val expectedUri = Uri.Builder()
            .scheme("https")
            .authority("servicesuche.bund.de")
            .fragment("de")
            .build()

        viewModel.setEvent(Event.OnSearchCitizenOfficeButtonClick(Locale.GERMAN))

        viewModel.effect.runFlowTest {
            val result = awaitItem()
            assertEquals(Effect.Navigation.OpenLink(expectedUri), result)
        }
    }

    @Test
    fun `when OnSearchCitizenOfficeButtonClick non-german locale, then open english link`() =
        runTest {
            val expectedUri = Uri.Builder()
                .scheme("https")
                .authority("servicesuche.bund.de")
                .fragment("en")
                .build()

            viewModel.setEvent(Event.OnSearchCitizenOfficeButtonClick(Locale.ENGLISH))

            viewModel.effect.runFlowTest {
                val result = awaitItem()
                assertEquals(Effect.Navigation.OpenLink(expectedUri), result)
            }
        }

    @Test
    fun `when IssueDocument event is triggered and interactor returns unknown error, then error dialog should be set in the state`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                flowOf(WalletAttestationResult.Failure("UNKNOWN", "traceId"))
            )

            viewModel.setEvent(Event.Init)

            verify(walletAttestationInteractor).generateAttestation()

            assertEquals("UNKNOWN", viewModel.viewState.value.errorDialog?.errorCode)
            assertEquals("traceId", viewModel.viewState.value.errorDialog?.traceId)
            kotlin.test.assertFalse(viewModel.viewState.value.isLoading)
            assertEquals(R.string.global_error_title, viewModel.viewState.value.errorDialog?.titleRes)
            assertEquals(R.string.global_error_paragraph, viewModel.viewState.value.errorDialog?.bodyTextRes)
            assertEquals(R.string.global_error_prim_button, viewModel.viewState.value.errorDialog?.primaryButtonTextRes)
        }

    @Test
    fun `when error dialog is set in the state, onDismiss should reset the error dialog in the state`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                flowOf(WalletAttestationResult.Failure("UNKNOWN", null))
            )

            viewModel.setEvent(Event.Init)

            viewModel.viewState.value.errorDialog?.onDismiss?.invoke()
            assertNull(viewModel.viewState.value.errorDialog)
        }

    @Test
    fun `when error dialog is set in the state, onPrimaryButtonClick should reset the error dialog in the state`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                flowOf(WalletAttestationResult.Failure("UNKNOWN", null))
            )


            viewModel.setEvent(Event.Init)

            viewModel.viewState.value.errorDialog?.onPrimaryButtonClick?.invoke()
            assertNull(viewModel.viewState.value.errorDialog)
        }

    @Test
    fun `when IssueDocument event is triggered and interactor returns WB_ACCOUNT_UNKNOWN error, then error dialog with correct title, bodytext and primary button text should be set in the state`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                flowOf(WalletAttestationResult.Failure("WB_ACCOUNT_UNKNOWN", "traceId"))
            )

            viewModel.setEvent(Event.Init)

            verify(walletAttestationInteractor).generateAttestation()

            assertEquals("WB_ACCOUNT_UNKNOWN", viewModel.viewState.value.errorDialog?.errorCode)
            assertEquals("traceId", viewModel.viewState.value.errorDialog?.traceId)
            kotlin.test.assertFalse(viewModel.viewState.value.isLoading)
            assertEquals(R.string.pid_issuance_wb_account_unkown_title, viewModel.viewState.value.errorDialog?.titleRes)
            assertEquals(R.string.pid_issuance_wb_account_unkown_paragraph, viewModel.viewState.value.errorDialog?.bodyTextRes)
            assertEquals(R.string.pid_issuance_wb_account_unkown_prim_button, viewModel.viewState.value.errorDialog?.primaryButtonTextRes)
        }

    @Test
    fun `when IssueDocument event is triggered and interactor returns WB_AUTH_VERIFICATION_FAILED error, then error dialog with correct title, bodytext and primary button text should be set in the state`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                flowOf(WalletAttestationResult.Failure("WB_AUTH_VERIFICATION_FAILED", "traceId"))
            )

            viewModel.setEvent(Event.Init)

            verify(walletAttestationInteractor).generateAttestation()

            assertEquals("WB_AUTH_VERIFICATION_FAILED", viewModel.viewState.value.errorDialog?.errorCode)
            assertEquals("traceId", viewModel.viewState.value.errorDialog?.traceId)
            kotlin.test.assertFalse(viewModel.viewState.value.isLoading)
            assertEquals(R.string.pid_issuance_wb_auth_verification_failed_title, viewModel.viewState.value.errorDialog?.titleRes)
            assertEquals(R.string.pid_issuance_wb_auth_verification_failed_paragraph, viewModel.viewState.value.errorDialog?.bodyTextRes)
            assertEquals(R.string.pid_issuance_wb_auth_verification_failed_prim_button, viewModel.viewState.value.errorDialog?.primaryButtonTextRes)
        }
    @Test
    fun `when issuance Completed event emitted, then viewModel resets to initial state`() = runTest {
        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()

        issuanceEventsFlow.emit(IssuanceEvent.Completed)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertEquals(ReadCardScreenStep.OnboardingCard, viewModel.viewState.value.currentStep)
        assertFalse(viewModel.viewState.value.isLoading)
    }
    @Test
    fun `when issuance Failed event emitted, then error dialog is shown and loading stops`() = runTest {
        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()

        issuanceEventsFlow.emit(IssuanceEvent.Failed("ISSUANCE_FAILED"))
        coroutineRule.testScope.testScheduler.runCurrent()

        val state = viewModel.viewState.value
        assertFalse(state.isLoading)
        assertTrue(state.errorDialog != null)
        assertEquals("ISSUANCE_FAILED", state.errorDialog.errorCode)
    }
    @Test
    fun `when issuance Completed event, viewModel resets to initial state`() = coroutineRule.runTest {
        viewModel.setEvent(Event.Init)

        issuanceEventsFlow.emit(IssuanceEvent.Completed)

        assertEquals(ReadCardScreenStep.OnboardingCard, viewModel.viewState.value.currentStep)
        assertFalse(viewModel.viewState.value.isLoading)
    }
    @Test
    fun `when issuance Failed event, error dialog is shown and loading stops`() = coroutineRule.runTest {
        viewModel.setEvent(Event.Init)

        issuanceEventsFlow.emit(IssuanceEvent.Failed("ISSUANCE_FAILED"))

        val state = viewModel.viewState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorDialog)
        assertEquals("ISSUANCE_FAILED", state.errorDialog.errorCode)
    }

    @Test
    fun `when the scan is asked for, then the reader is armed and the read is announced as ready`() =
        coroutineRule.runTest {
            viewModel.goToNfcPromptForEidPin()

            viewModel.setEvent(Event.OnStartScanningClick)

            with(viewModel.viewState.value) {
                assertEquals(CardScanStatus.READY, scanStatus)
                assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, currentRoute)
                assertFalse(isLoading)
            }
        }

    @Test
    fun `when the scan is asked for with a simulated card, then the read starts without a tap`() {
        whenever(userRuntimeConfig.eidCardType).thenReturn(EidCardType.VIRTUAL)
        viewModel = createViewModel()
        viewModel.goToNfcPromptForEidPin()

        viewModel.setEvent(Event.OnStartScanningClick)

        verify(cardReaderInteractor, times(1)).setVirtualCard()
    }

    @Test
    fun `when the card is being read, then the scan reports the progress it makes`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            workflowEventsFlow.value = WorkflowEvent.ReadingProgress(progress = 42)

            with(viewModel.viewState.value) {
                assertEquals(CardScanStatus.IN_PROGRESS, scanStatus)
                assertEquals(42, readingProgress)
                // The read reports itself, so the screen-wide loader stays away.
                assertFalse(isLoading)
            }
        }

    @Test
    fun `when the code is refused during a read, then the scan reports it without leaving the screen`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            workflowEventsFlow.value = WorkflowEvent.PinRequested(pinRetryCounter = 2)

            with(viewModel.viewState.value) {
                assertEquals(CardScanStatus.FAILED, scanStatus)
                // The screen does not move under the user while the card is still on the sensor.
                assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, currentRoute)
                assertEquals(ReadCardScreenStep.EnterPin, codeRetry?.step)
            }
        }

    @Test
    fun `when a refused read is given up on, then the code entry comes back with the warning`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            workflowEventsFlow.value = WorkflowEvent.PinRequested(pinRetryCounter = 2)

            viewModel.setEvent(Event.OnCancelScanClick)

            with(viewModel.viewState.value) {
                assertEquals(CardReaderRoute.ENTER_PIN, currentRoute)
                assertEquals("wrong pin", pinState.supportingText)
                assertNull(scanStatus)
                assertNull(codeRetry)
            }
        }

    @Test
    fun `when a running read is given up on, then the scan instructions come back`() =
        coroutineRule.runTest {
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            viewModel.setEvent(Event.OnCancelScanClick)

            with(viewModel.viewState.value) {
                assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, currentRoute)
                assertNull(scanStatus)
            }
        }

    @Test
    fun `when a simulated card is asked for the code again mid-read, then the read is left to finish`() =
        coroutineRule.runTest {
            whenever(userRuntimeConfig.eidCardType).thenReturn(EidCardType.VIRTUAL)
            viewModel = createViewModel()
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            workflowEventsFlow.value = WorkflowEvent.PinRequested(pinRetryCounter = 3)

            with(viewModel.viewState.value) {
                // The simulator is never asked for a code, so the re-request says nothing about the
                // one that was entered and must not flash the entry screen over the read.
                assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, currentRoute)
                assertNull(codeRetry)
                assertEquals(CardScanStatus.READY, scanStatus)
            }
        }

    @Test
    fun `when a read completes, then the scan waits for the user before moving on`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            workflowEventsFlow.value = WorkflowEvent.AuthenticationCompleted(redirectURL = "https://redirect")

            with(viewModel.viewState.value) {
                assertEquals(CardScanStatus.SUCCESS, scanStatus)
                // Still on the scan: the success screens are not shown on the way past.
                assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, currentRoute)
            }
        }

    @Test
    fun `when the finished read is acknowledged, then the flow asks for the issuance consent`() =
        runTest {
            viewModel.setEvent(Event.Init)
            coroutineRule.testScope.testScheduler.runCurrent()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            workflowEventsFlow.value = WorkflowEvent.AuthenticationCompleted(redirectURL = "https://redirect")
            coroutineRule.testScope.testScheduler.runCurrent()

            viewModel.setEvent(Event.OnScanContinueClick)
            coroutineRule.testScope.testScheduler.runCurrent()

            // The read is finished but nothing has been added yet: the credential on offer is shown
            // first, and the scan view is gone so the consent's own actions can be reached.
            assertEquals(CardReaderRoute.ISSUANCE_CONSENT, viewModel.viewState.value.currentRoute)
            assertEquals(null, viewModel.viewState.value.scanStatus)
        }

    @Test
    fun `given no pid in the wallet, when the consent is reached, then it lists no attributes`() =
        runTest {
            whenever(walletCoreDocumentsController.getMainPidDocument()).thenReturn(null)

            viewModel.setEvent(Event.Init)
            coroutineRule.testScope.testScheduler.runCurrent()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            workflowEventsFlow.value = WorkflowEvent.AuthenticationCompleted(redirectURL = "https://redirect")
            coroutineRule.testScope.testScheduler.runCurrent()
            viewModel.setEvent(Event.OnScanContinueClick)
            coroutineRule.testScope.testScheduler.runCurrent()

            // Nothing to read yet is a quiet empty sheet, not a crash and not a half-filled row.
            assertEquals(CardReaderRoute.ISSUANCE_CONSENT, viewModel.viewState.value.currentRoute)
            assertTrue(viewModel.viewState.value.issuedCredentialAttributes.isEmpty())
        }

    @Test
    fun `when the issuance consent is accepted, then the flow continues to the wallet pin setup`() =
        runTest {
            viewModel.setEvent(Event.Init)
            coroutineRule.testScope.testScheduler.runCurrent()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            workflowEventsFlow.value = WorkflowEvent.AuthenticationCompleted(redirectURL = "https://redirect")
            coroutineRule.testScope.testScheduler.runCurrent()
            viewModel.setEvent(Event.OnScanContinueClick)
            coroutineRule.testScope.testScheduler.runCurrent()

            viewModel.effect.runFlowTest {
                viewModel.setEvent(Event.OnAcceptIssuanceClick)

                // Arming the reader queued an effect of its own, so the navigation is the next one
                // that is not it.
                var effect = awaitItem()
                while (effect !is Effect.Navigation) {
                    effect = awaitItem()
                }
                assertTrue(effect is Effect.Navigation.SwitchScreen)
                assertEquals("https://redirect", effect.redirectUrl)
            }
        }
}

/**
 * Walks the flow to the NFC prompt for the card PIN, which is where a scan is asked for.
 */
private fun ReadCardViewModel.goToNfcPromptForEidPin() {
    setEvent(Event.OnResume(isNfcEnabled = true))
    setEvent(Event.AcceptRightsAndEnterPin)
    typeIntoCodeField("123456")
    setEvent(Event.OnContinueClickCardPin)
}

/**
 * Types [code] into the screen's code buffer, standing in for the field the user would use. The
 * digits never travel as an event payload, so a test has to put them where the field would.
 */
private fun ReadCardViewModel.typeIntoCodeField(code: String) {
    viewState.value.pinState.buffer.apply {
        wipe()
        type(code)
    }
}