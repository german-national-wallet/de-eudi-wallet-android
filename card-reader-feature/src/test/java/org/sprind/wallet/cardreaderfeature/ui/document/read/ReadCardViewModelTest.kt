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
import android.nfc.Tag
import com.governikus.ausweisapp.sdkwrapper.card.core.AccessRight
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import eu.europa.ec.businesslogic.config.ConfigLogic
import org.sprind.wallet.businesslogic.config.EidCardType
import eu.europa.ec.businesslogic.config.EnvironmentConfig
import org.sprind.wallet.businesslogic.config.UserRuntimeConfig
import org.sprind.wallet.businesslogic.config.LocalizedUrl
import org.sprind.wallet.businesslogic.config.SettingsLinks
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.commonfeature.interactor.IssuanceEvent
import eu.europa.ec.corelogic.controller.IssuanceMethod
import eu.europa.ec.corelogic.handler.AusweisSdkAuthorizationHandler
import eu.europa.ec.corelogic.handler.pid.RefreshUrlInteractor
import eu.europa.ec.corelogic.interactor.walletattestation.WalletAttestationInteractor
import eu.europa.ec.corelogic.interactor.walletattestation.WalletAttestationResult
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.sprind.wallet.cardreaderfeature.domain.CardReaderFlowType
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import org.sprind.wallet.businesslogic.model.UserPin
import org.sprind.wallet.cardreaderfeature.domain.CardReaderRoute
import org.sprind.wallet.cardreaderfeature.domain.CardScanStatus
import org.sprind.wallet.corelogic.eid.AuthOutcome
import org.sprind.wallet.corelogic.eid.CardInfo
import org.sprind.wallet.corelogic.eid.EidEngine
import org.sprind.wallet.corelogic.eid.EidEngineImpl
import org.sprind.wallet.corelogic.eid.EidResult
import org.sprind.wallet.corelogic.eid.EidSdkPort
import org.sprind.wallet.corelogic.eid.SdkSignal
import org.sprind.wallet.uilogic.component.CodeLength
import junit.framework.TestCase.assertFalse
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.sprind.wallet.networklogic.common.NetworkFailure
import kotlin.time.Instant

/**
 * Runs the screen on the real [EidEngineImpl], over a [FakeEidSdkPort] the tests play the SDK's
 * signals through, and on the real [AusweisSdkAuthorizationHandler], whose authorization the
 * stubbed issuance asks for as the real one does.
 */
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

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    @Mock
    private lateinit var refreshUrlInteractor: RefreshUrlInteractor

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

    private lateinit var sdk: FakeEidSdkPort
    private lateinit var eidScope: CoroutineScope
    private lateinit var eidEngine: EidEngine
    private lateinit var authHandler: AusweisSdkAuthorizationHandler

    /** The issuance's wait on its authorization, as the stubbed interactor runs it. */
    private var issuanceJob: Job? = null
    private var isSdkStarted = false

    private lateinit var viewModel: ReadCardViewModel

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.getMarkdownString(any())).thenReturn(AnnotatedString(""))
        whenever(resourceProvider.getString(R.string.pid_issuance_onboarding_eid_tert_button_2))
            .thenReturn("help")
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_eID_PIN_not_set_title))
            .thenReturn("info")
        whenever(resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin))
            .thenReturn("wrong pin")
        doNothing().`when`(logController).d(any(), any<() -> String>())
        doNothing().`when`(logController).e(any(), any<() -> String>())
        whenever(configLogic.environmentConfig).thenReturn(environmentConfig)
        whenever(environmentConfig.pidIssuerURL).thenReturn("https://demo.pid-provider.bundesdruckerei.de")
        whenever(configLogic.settingsLinks).thenReturn(SettingsLinks(pidPrivacyPolicy = PID_PRIVACY_POLICY))
        whenever(resourceProvider.getLocale()).thenReturn(Locale.ENGLISH)
        whenever(userRuntimeConfig.eidCardType).thenReturn(EidCardType.PHYSICAL)

        issuanceEventsFlow = MutableSharedFlow(extraBufferCapacity = 16)
        whenever(addDocumentInteractor.issuanceEvents).thenReturn(issuanceEventsFlow)

        eidScope = CoroutineScope(coroutineRule.testDispatcher + SupervisorJob())
        sdk = FakeEidSdkPort()
        isSdkStarted = false
        eidEngine = EidEngineImpl(sdk, eidScope, logController)
        authHandler = AusweisSdkAuthorizationHandler(eidEngine, userRuntimeConfig, refreshUrlInteractor)
        whenever(addDocumentInteractor.authorizationHandler).thenReturn(authHandler)

        // As the real issuance does, starting one asks for its authorization, and cancelling it
        // gives that up.
        doAnswer {
            issuanceJob?.cancel()
            issuanceJob = eidScope.launch { authHandler.authorize(TC_TOKEN_URL) }
            Unit
        }.whenever(addDocumentInteractor).startIssueDocumentAttested(
            issuanceMethod = any<IssuanceMethod>(),
            configIds = any<Set<CredentialConfigurationIdentifier>>(),
            issuerId = any(),
            walletInstanceAttestationSpec = any(),
        )
        doAnswer {
            issuanceJob?.cancel()
            issuanceJob = null
            authHandler.forgetPendingAuthorization()
            Unit
        }.whenever(addDocumentInteractor).cancelIssuance()

        runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                flowOf(WalletAttestationResult.Success(walletInstanceAttestationSpec = mockAttestationSpec))
            )
        }

        viewModel = createViewModel()
    }

    /**
     * Kept separate from [setUp] so a test can re-stub the mocks it depends on — the
     * eID card type, for instance, is read when the initial state is built.
     */
    private fun createViewModel() = ReadCardViewModel(
        configLogic = configLogic,
        resourceProvider = resourceProvider,
        eidEngine = eidEngine,
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
        eidScope.cancel()
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

        // The authentication the issuance asks for connects the SDK.
        assertEquals(listOf("CONNECT"), sdk.commands)
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
        viewModel.setEvent(Event.Init)
        runCurrent()

        assertEquals(0, sent("RUN_AUTH"))

        startSdk()

        assertEquals(1, sent("RUN_AUTH"))
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
        assertNull(state.hsmDelay, "Only an overloaded HSM is a wait")
    }

    @Test
    fun `when InitEnterPin event, then go to EnterPin without starting reader`() {
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)

        assertTrue(sdk.commands.isEmpty())

        with(viewModel.viewState.value) {
            assertEquals(ReadCardScreenStep.EnterPin, currentStep)
            assertEquals(CardReaderRoute.ENTER_PIN, currentRoute)
            assertEquals("info", bottomSheetTitle)
        }
    }

    @Test
    fun `when consent accepted while nfc is off, then the pin entry is still shown`() = coroutineRule.runTest {
        viewModel.requestRights()
        viewModel.setEvent(Event.OnResume(isNfcEnabled = false))

        viewModel.setEvent(Event.AcceptRightsAndEnterPin)

        // NFC is not needed to type a PIN, so it no longer gates this step.
        assertEquals(1, sent("ACCEPT"))
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
    fun `when consent accepted with a virtual card, then nfc state does not gate the flow`() = coroutineRule.runTest {
        useSimulatedCard()
        viewModel.requestRights()
        viewModel.setEvent(Event.OnResume(isNfcEnabled = false))

        viewModel.setEvent(Event.AcceptRightsAndEnterPin)

        assertEquals(1, sent("ACCEPT"))
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
    fun `when resumed with nfc enabled outside the activation step, then the step is kept`() = coroutineRule.runTest {
        viewModel.requestRights()
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)

        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))

        assertEquals(1, sent("ACCEPT"))
        assertEquals(CardReaderRoute.ENTER_PIN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when Pin event set, then providePin is called and loading becomes true`() {
        val pin = "123456"

        viewModel.setEvent(Event.Init)
        sdkSends(pin(retryCounter = 3))
        viewModel.typeIntoCodeField(pin)
        viewModel.setEvent(Event.Pin)
        assertTrue(viewModel.viewState.value.isLoading)
        assertEquals(1, sent("SET_PIN $pin"))
    }

    @Test
    fun `when Can event set, then provideCan is called and loading becomes true`() {
        val can = "654321"

        viewModel.setEvent(Event.Init)
        sdkSends(can())
        viewModel.typeIntoCodeField(can)
        viewModel.setEvent(Event.Can)

        assertEquals(1, sent("SET_CAN $can"))
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
        val authentication = requireNotNull(authHandler.authentication.value)

        viewModel.setEvent(Event.Pop)

        viewModel.effect.runFlowTest {
            val result = awaitItem()
            assertTrue(result is Effect.Navigation.Pop)
        }
        assertEquals(EidResult.Cancelled, authentication.outcome.await())
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
    fun `when the scan is started with the card recognised while the SDK waits for the PIN, then the PIN is provided`() = runTest {
        val pin = "123456"

        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)
        // Asked for while the PIN is still being typed, the request waits for the card.
        sdkSends(pin(retryCounter = 3))
        viewModel.typeIntoCodeField(pin)
        assertEquals(0, sentStartingWith("SET_PIN"))
        viewModel.setEvent(Event.OnContinueClickCardPin)
        assertEquals(ReadCardScreenStep.NfcScanPrompt.EidPin, viewModel.viewState.value.currentStep)
        sdkSends(CARD_RECOGNISED)
        // Nothing is read before the user starts the scan.
        assertEquals(0, sentStartingWith("SET_PIN"))

        viewModel.setEvent(Event.OnStartScanningClick)

        assertEquals(1, sent("SET_PIN $pin"))
        // The read reports itself on the scan sheet rather than the screen-wide loader.
        assertEquals(CardScanStatus.IN_PROGRESS, viewModel.viewState.value.scanStatus)
    }

    @Test
    fun `when the screen asks to be set up again, then one card is still answered once`() =
        runTest {
            val pin = "123456"

            viewModel.setEvent(Event.Init)
            // The screen is set up a second time while the first listener is still running.
            viewModel.setEvent(Event.Init)
            viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
            viewModel.setEvent(Event.OnContinueClickOnboardingPin)
            viewModel.typeIntoCodeField(pin)
            viewModel.setEvent(Event.OnContinueClickCardPin)
            viewModel.setEvent(Event.OnStartScanningClick)

            sdkSends(pin(retryCounter = 3))

            // Two listeners would send the card two PINs for the one the user typed.
            assertEquals(1, sentStartingWith("SET_PIN"))
            assertEquals(1, sent("SET_PIN $pin"))
        }

    @Test
    fun `when the flow is left, then the workflow no longer moves the screen`() = runTest {
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.Close)
        val routeOnLeaving = viewModel.viewState.value.currentRoute

        sdkSends(pin(retryCounter = 2))

        // A reader left behind must not go on answering a workflow the user has walked away from.
        assertEquals(routeOnLeaving, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when AuthenticationCompleted, then Completed state, effect to SwitchScreen, and cancel identification`() =
        runTest {
            viewModel.setEvent(Event.Init)
            val authentication = requireNotNull(authHandler.authentication.value)

            sdkSends(SdkSignal.AuthSucceeded("redirect"))

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
            // The identification is over: the workflow ended with the redirect.
            assertEquals(EidResult.Success(AuthOutcome("redirect")), authentication.outcome.await())
        }

    @Test
    fun `when CardRemoved during a read, then no loader is left and the card is asked for again`() = runTest {
        viewModel.setEvent(Event.Init)
        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)
        viewModel.setEvent(Event.OnContinueClickOnboardingPin)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnContinueClickCardPin) // show NFC prompt
        assertEquals(ReadCardScreenStep.NfcScanPrompt.EidPin, viewModel.viewState.value.currentStep)
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(pin(retryCounter = 3))
        sdkSends(CARD_RECOGNISED)

        sdkSends(CARD_REMOVED)

        assertFalse(viewModel.viewState.value.isLoading)
        assertEquals(CardScanStatus.READY_AGAIN, viewModel.viewState.value.scanStatus)
    }

    @Test
    fun `when ReadingProgress, then readingProgress state reflects the reported value`() = runTest {
        viewModel.setEvent(Event.Init)

        sdkSends(progress(60))

        assertEquals(60, viewModel.viewState.value.readingProgress)
    }

    @Test
    fun `when the scan is asked for, then the NFC reader is restarted to re-discover a resting card`() =
        runTest {
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
            viewModel.setEvent(Event.Init)
            // Navigate to the CAN NFC prompt (a follow-up read after a first read).
            viewModel.setEvent(Event.OnEnterCanButtonPress)
            viewModel.typeIntoCodeField("123456")
            viewModel.setEvent(Event.OnCanUpdate)
            assertEquals(CardReaderRoute.NFC_SCAN_CAN, viewModel.viewState.value.currentRoute)

            viewModel.effect.runFlowTest {
                // The SDK re-requesting the card re-arms the reader, so a card resting on the
                // sensor from the previous read is re-discovered without a re-tap.
                sdkSends(SdkSignal.InsertCard)
                assertEquals(Effect.RestartNfcReader, awaitItem())
            }
        }

    @Test
    fun `when InsertCardRequested and no NFC prompt is active, then the NFC reader is not restarted`() =
        runTest {
            viewModel.setEvent(Event.Init)
            assertEquals(CardReaderRoute.ONBOARDING_CARD, viewModel.viewState.value.currentRoute)

            viewModel.effect.runFlowTest {
                sdkSends(SdkSignal.InsertCard)
                expectNoEvents()
            }
        }

    @Test
    fun `when EnterCan event first time, then show PinBlockedError with CAN bottom sheet title`() {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_can_title))
            .thenReturn("bottomSheetInfoText")

        viewModel.setEvent(Event.Init)
        sdkSends(can())

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

        viewModel.setEvent(Event.Init)
        sdkSends(can(isInputRejected = true))

        assertEquals(ReadCardScreenStep.EnterCan, viewModel.viewState.value.currentStep)
        assertEquals("bottomSheetInfoText", viewModel.viewState.value.bottomSheetTitle)
        assertEquals("wrongCan", viewModel.viewState.value.pinState.supportingText)
        assertFalse(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `when EnterCan event repeats before any CAN was sent, then no CAN is blamed`() {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_can_title))
            .thenReturn("bottomSheetInfoText")
        whenever(resourceProvider.getString(R.string.pid_issuance_can_entry_error_wrong_can))
            .thenReturn("wrongCan")

        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()
        viewModel.setEvent(Event.OnEnterCanButtonPress)

        sdkSends(can(), can())

        assertEquals(ReadCardScreenStep.EnterCan, viewModel.viewState.value.currentStep)
        assertNull(viewModel.viewState.value.pinState.supportingText)
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

        viewModel.setEvent(Event.Init)
        sdkSends(can())
        // go to CAN prompt
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField(can)
        viewModel.setEvent(Event.OnCanUpdate)
        assertEquals(ReadCardScreenStep.NfcScanPrompt.Can, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderRoute.NFC_SCAN_CAN, viewModel.viewState.value.currentRoute)

        // The card is recognised, but nothing is read before the user starts the scan.
        sdkSends(CARD_RECOGNISED)
        assertEquals(0, sentStartingWith("SET_CAN"))
        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
        viewModel.setEvent(Event.OnStartScanningClick)

        assertEquals(1, sent("SET_CAN $can"))
        // The read reports itself on the scan sheet rather than the screen-wide loader.
        assertEquals(CardScanStatus.IN_PROGRESS, viewModel.viewState.value.scanStatus)
    }

    @Test
    fun `when pin requested after nfc can prompt, route falls back to can success`() = runTest {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_eID_PIN_not_set_title))
            .thenReturn("info")
        whenever(resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin))
            .thenReturn("wrongPin")

        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)

        sdkSends(pin(retryCounter = 3))

        assertEquals(CardReaderRoute.ENTER_CAN_SUCCESS, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when the card asks for the pin during the can read, then the can is reported as correct`() =
        runTest {
            whenever(resourceProvider.getString(R.string.pid_issuance_sheet_eID_PIN_not_set_title))
                .thenReturn("info")

            viewModel.setEvent(Event.Init)
            coroutineRule.testScope.testScheduler.runCurrent()
            viewModel.setEvent(Event.OnEnterCanButtonPress)
            viewModel.typeIntoCodeField("123456")
            viewModel.setEvent(Event.OnCanUpdate)
            viewModel.setEvent(Event.OnStartScanningClick)

            // The card only asks for the PIN once it has taken the CAN, so this is the CAN read
            // succeeding: nothing here may say it failed.
            sdkSends(pin(retryCounter = 1))

            with(viewModel.viewState.value) {
                assertEquals(CardReaderRoute.ENTER_CAN_SUCCESS, currentRoute)
                assertNull(scanStatus)
                assertNull(pinState.supportingText)
                assertFalse(isLoading)
            }
        }

    @Test
    fun `when the can is refused during the read, then the entry comes back naming it`() = runTest {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_can_title))
            .thenReturn("bottomSheetInfoText")
        whenever(resourceProvider.getString(R.string.pid_issuance_can_entry_error_wrong_can))
            .thenReturn("wrongCan")

        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()
        sdkSends(can())
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(CARD_RECOGNISED)
        assertEquals(1, sentStartingWith("SET_CAN"))

        // Asking for the CAN again, with one already sent, is the card refusing it.
        sdkSends(can())

        with(viewModel.viewState.value) {
            assertEquals(CardReaderRoute.ENTER_CAN, currentRoute)
            assertEquals("wrongCan", pinState.supportingText)
            assertNull(scanStatus)
            assertFalse(isLoading)
        }
    }

    @Test
    fun `when EnterCan arrives after PinRequested during CAN flow, then stay on EnterCan`() = runTest {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_can_title))
            .thenReturn("bottomSheetInfoText")
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_eID_PIN_not_set_title))
            .thenReturn("info")
        whenever(resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin))
            .thenReturn("wrongPin")

        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)

        sdkSends(pin(retryCounter = 1))
        assertEquals(CardReaderRoute.ENTER_CAN_SUCCESS, viewModel.viewState.value.currentRoute)

        sdkSends(can())

        assertEquals(ReadCardScreenStep.EnterCan, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderRoute.ENTER_CAN, viewModel.viewState.value.currentRoute)
        assertEquals("bottomSheetInfoText", viewModel.viewState.value.bottomSheetTitle)
    }

    @Test
    fun `when PIN wrong twice then SDK requests CAN, first EnterCan shows PinBlockedError and retry stays on EnterCan`() = runTest {
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_can_title))
            .thenReturn("bottomSheetInfoText")
        whenever(resourceProvider.getString(R.string.pid_issuance_sheet_eID_PIN_not_set_title))
            .thenReturn("info")
        whenever(resourceProvider.getString(R.string.pid_issuance_puk_entry_warning_wrong_puk_1))
            .thenReturn("wrongPuk")
        whenever(resourceProvider.getString(R.string.pid_presentation_wallet_pin_entry_error_wrong_pin))
            .thenReturn("wrongPin")

        viewModel.setEvent(Event.Init)
        coroutineRule.testScope.testScheduler.runCurrent()

        // first wrong PIN: retry counter drops to 2
        sdkSends(pin(retryCounter = 2))
        assertEquals(ReadCardScreenStep.EnterPin, viewModel.viewState.value.currentStep)

        // second wrong PIN: retry counter drops to 1, "last try" warning shown
        sdkSends(pin(retryCounter = 1))
        assertEquals(ReadCardScreenStep.EnterPin, viewModel.viewState.value.currentStep)

        // SDK now requires CAN -> first EnterCan must show the PinBlockedError intro screen
        sdkSends(can())
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
        sdkSends(pin(retryCounter = 1))

        sdkSends(can())

        assertEquals(ReadCardScreenStep.EnterCan, viewModel.viewState.value.currentStep)
        assertEquals(CardReaderRoute.ENTER_CAN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when the transport PIN is wrong twice and the CAN is taken, then the transport PIN is asked for again`() =
        runTest {
            whenever(resourceProvider.getString(R.string.pid_issuance_puk_entry_warning_wrong_puk_1))
                .thenReturn("lastAttempt")
            viewModel.setEvent(Event.StartTransportPin)

            listOf(2, 1).forEach { retryCounter ->
                viewModel.typeIntoCodeField("12345")
                viewModel.setEvent(Event.OnTransportPinUpdate)
                assertEquals(CardReaderRoute.NFC_SCAN_TRANSPORT_PIN, viewModel.viewState.value.currentRoute)
                sdkSends(pin(retryCounter = retryCounter, isInputRejected = true))
                assertEquals(CardReaderRoute.ENTER_TRANSPORT_PIN, viewModel.viewState.value.currentRoute)
            }

            viewModel.typeIntoCodeField("12345")
            viewModel.setEvent(Event.OnTransportPinUpdate)
            sdkSends(can())
            assertEquals(CardReaderRoute.PIN_BLOCKED_ERROR, viewModel.viewState.value.currentRoute)

            viewModel.setEvent(Event.OnEnterCanButtonPress)
            viewModel.typeIntoCodeField("123456")
            viewModel.setEvent(Event.OnCanUpdate)
            viewModel.setEvent(Event.OnStartScanningClick)
            // The card only asks for the PIN again once it has taken the CAN.
            sdkSends(pin(retryCounter = 1))

            with(viewModel.viewState.value) {
                assertEquals(CardReaderFlowType.CHANGE_PIN, activeFlowType)
                assertEquals(CardReaderRoute.ENTER_TRANSPORT_PIN, currentRoute)
                assertEquals(CodeLength.TRANSPORT_PIN, pinState.buffer.capacity)
                assertEquals("lastAttempt", pinState.supportingText)
            }
            assertEquals(0, sent("ACCEPT"))
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
            assertEquals(Effect.Navigation.NavigateToPrivacyPolicy(PID_PRIVACY_POLICY.en), awaitItem())
        }
    }

    @Test
    fun `when OnPrivacyPolicyButtonClick event in german, then opens the german policy`() = runTest {
        // local has to be place before initializing the VM
        whenever(resourceProvider.getLocale()).thenReturn(Locale.GERMANY)
        viewModel = createViewModel()

        viewModel.setEvent(Event.OnPrivacyPolicyButtonClick)

        viewModel.effect.runFlowTest {
            assertEquals(Effect.Navigation.NavigateToPrivacyPolicy(PID_PRIVACY_POLICY.de), awaitItem())
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
    fun `when issuance Failed event, the Issuance span is ended`() = coroutineRule.runTest {
        viewModel.setEvent(Event.Init)

        issuanceEventsFlow.emit(IssuanceEvent.Failed("ISSUANCE_FAILED"))

        verify(telemetry).endSpan(eq(TelemetryConstants.ISSUANCE), any())
    }

    @Test
    fun `when issuance times out before the eID step, retry starts it again from the attestation`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)

            issuanceEventsFlow.emit(IssuanceEvent.Failed("ISSUANCE_FAILED", NetworkFailure.Timeout))

            val dialog = assertNotNull(viewModel.viewState.value.errorDialog)
            assertEquals(R.string.global_timeout_slow_internet_error_title, dialog.titleRes)
            assertEquals(R.string.pid_issuance_timeout_par_request_expired_sec_button, dialog.secondaryButtonTextRes)

            dialog.onPrimaryButtonClick()

            verify(walletAttestationInteractor, times(2)).generateAttestation()
            verify(addDocumentInteractor, times(2)).startIssueDocumentAttested(
                issuanceMethod = any(),
                configIds = any<Set<CredentialConfigurationIdentifier>>(),
                issuerId = any(),
                walletInstanceAttestationSpec = any(),
            )

            // The retry replaced the first attempt's collector, so a failure is handled once.
            issuanceEventsFlow.emit(IssuanceEvent.Failed("ISSUANCE_FAILED"))
            verify(telemetry, times(2)).endSpan(eq(TelemetryConstants.ISSUANCE), any())
        }

    @Test
    fun `when issuance is rate limited before the eID step, the dialog only leaves the step`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)

            issuanceEventsFlow.emit(
                IssuanceEvent.Failed("ISSUANCE_FAILED", NetworkFailure.RateLimited(retryAt = Instant.parse("2026-10-24T10:05:00Z")))
            )

            val dialog = assertNotNull(viewModel.viewState.value.errorDialog)
            assertEquals(R.string.global_timeout_too_many_requests_title, dialog.titleRes)
            dialog.onPrimaryButtonClick()

            assertNull(viewModel.viewState.value.errorDialog)
            verify(walletAttestationInteractor, times(1)).generateAttestation()
        }

    @Test
    fun `when the attestation times out, the dialog offers to retry or cancel`() = coroutineRule.runTest {
        whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
            flowOf(WalletAttestationResult.Failure("NO_INTERNET", "trace", NetworkFailure.Timeout))
        )

        viewModel.setEvent(Event.Init)

        val dialog = assertNotNull(viewModel.viewState.value.errorDialog)
        assertEquals(R.string.global_timeout_slow_internet_error_title, dialog.titleRes)
        assertNull(dialog.errorCode)

        dialog.onPrimaryButtonClick()

        verify(walletAttestationInteractor, times(2)).generateAttestation()
    }

    //region WD-5013: overloaded HSM

    @Test
    fun `when the HSM is overloaded, the patience screen offers a retry only after 20 seconds`() =
        coroutineRule.runTest {
            givenTheHsmIsOverloaded()

            viewModel.setEvent(Event.Init)

            val state = viewModel.viewState.value
            assertEquals(HsmDelay(canRetry = false), state.hsmDelay)
            // A wait, not an error.
            assertNull(state.errorDialog)
            assertFalse(state.isLoading)

            coroutineRule.testScope.advanceTimeBy(19.seconds)
            assertEquals(HsmDelay(canRetry = false), viewModel.viewState.value.hsmDelay)

            coroutineRule.testScope.advanceTimeBy(1.seconds + 1.milliseconds)
            assertEquals(HsmDelay(canRetry = true), viewModel.viewState.value.hsmDelay)
        }

    @Test
    fun `when retrying after an overloaded HSM, the issuance starts again from the attestation`() =
        coroutineRule.runTest {
            givenTheHsmIsOverloaded()
            viewModel.setEvent(Event.Init)
            coroutineRule.testScope.advanceTimeBy(20.seconds + 1.milliseconds)

            viewModel.setEvent(Event.RetryAfterHsmDelay)

            verify(walletAttestationInteractor, times(2)).generateAttestation()
            // Overloaded again, so the patience screen is back, waiting from the start.
            assertEquals(HsmDelay(canRetry = false), viewModel.viewState.value.hsmDelay)
        }

    @Test
    fun `when the patience screen is closed, the issuance is left for the dashboard`() =
        coroutineRule.runTest {
            givenTheHsmIsOverloaded()
            viewModel.setEvent(Event.Init)

            viewModel.effect.runFlowTest {
                viewModel.setEvent(Event.CloseHsmDelay)

                assertTrue(awaitItem() is Effect.Navigation.Pop)
            }
            assertNull(viewModel.viewState.value.hsmDelay)

            // The timer went with the screen.
            coroutineRule.testScope.advanceTimeBy(20.seconds + 1.milliseconds)
            assertNull(viewModel.viewState.value.hsmDelay)
        }

    @Test
    fun `when the HSM recovers, retrying after it was overloaded starts the issuance`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                overloadedHsm(),
                flowOf(WalletAttestationResult.Success(walletInstanceAttestationSpec = mockAttestationSpec)),
            )
            viewModel.setEvent(Event.Init)
            coroutineRule.testScope.advanceTimeBy(20.seconds + 1.milliseconds)

            viewModel.setEvent(Event.RetryAfterHsmDelay)

            verifyIssuanceStarted()
            assertNull(viewModel.viewState.value.hsmDelay)
        }

    @Test
    fun `when retry is tapped before it is offered, or twice, the attestation runs once more only`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                overloadedHsm(),
                flowOf(WalletAttestationResult.Success(walletInstanceAttestationSpec = mockAttestationSpec)),
            )
            viewModel.setEvent(Event.Init)

            coroutineRule.testScope.advanceTimeBy(10.seconds)
            viewModel.setEvent(Event.RetryAfterHsmDelay)
            verify(walletAttestationInteractor, times(1)).generateAttestation()

            coroutineRule.testScope.advanceTimeBy(10.seconds + 1.milliseconds)
            viewModel.setEvent(Event.RetryAfterHsmDelay)
            viewModel.setEvent(Event.RetryAfterHsmDelay)

            verify(walletAttestationInteractor, times(2)).generateAttestation()
        }

    @Test
    fun `when the attestation is slow, the patience screen replaces the loader after 5 seconds`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(slowHsm())

            viewModel.setEvent(Event.Init)

            coroutineRule.testScope.advanceTimeBy(5.seconds)
            assertNull(viewModel.viewState.value.hsmDelay)

            coroutineRule.testScope.advanceTimeBy(1.milliseconds)
            val state = viewModel.viewState.value
            assertEquals(HsmDelay(canRetry = false), state.hsmDelay)
            assertNull(state.errorDialog)

            // The retry is offered 20 seconds after the screen appeared.
            coroutineRule.testScope.advanceTimeBy(20.seconds)
            assertEquals(HsmDelay(canRetry = true), viewModel.viewState.value.hsmDelay)
        }

    @Test
    fun `when a slow attestation succeeds, the patience screen gives way to the issuance`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                flow {
                    delay(8.seconds)
                    emit(WalletAttestationResult.Success(walletInstanceAttestationSpec = mockAttestationSpec))
                }
            )
            viewModel.setEvent(Event.Init)
            coroutineRule.testScope.advanceTimeBy(6.seconds)
            assertNotNull(viewModel.viewState.value.hsmDelay)

            coroutineRule.testScope.advanceTimeBy(2.seconds + 1.milliseconds)

            verifyIssuanceStarted()
            assertNull(viewModel.viewState.value.hsmDelay)
            // The retry timer went with the screen.
            coroutineRule.testScope.advanceTimeBy(20.seconds)
            assertNull(viewModel.viewState.value.hsmDelay)
        }

    @Test
    fun `when a slow attestation fails, the patience screen gives way to the error dialog`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                flow {
                    delay(8.seconds)
                    emit(WalletAttestationResult.Failure("UNKNOWN", "trace"))
                }
            )
            viewModel.setEvent(Event.Init)

            coroutineRule.testScope.advanceTimeBy(8.seconds + 1.milliseconds)

            val state = viewModel.viewState.value
            assertNull(state.hsmDelay)
            assertNotNull(state.errorDialog)
        }

    @Test
    fun `when retrying a slow attestation, the one in flight is given up and the issuance starts over`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                slowHsm(),
                flowOf(WalletAttestationResult.Success(walletInstanceAttestationSpec = mockAttestationSpec)),
            )
            viewModel.setEvent(Event.Init)
            coroutineRule.testScope.advanceTimeBy(25.seconds + 1.milliseconds)

            viewModel.setEvent(Event.RetryAfterHsmDelay)

            verify(walletAttestationInteractor, times(2)).generateAttestation()
            verify(addDocumentInteractor).cancelIssuance()
            verifyIssuanceStarted()
            assertNull(viewModel.viewState.value.hsmDelay)
        }

    @Test
    fun `when the patience screen of a slow attestation is closed, its late result is ignored`() =
        coroutineRule.runTest {
            whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
                flow {
                    delay(8.seconds)
                    emit(WalletAttestationResult.Success(walletInstanceAttestationSpec = mockAttestationSpec))
                }
            )
            viewModel.setEvent(Event.Init)
            coroutineRule.testScope.advanceTimeBy(6.seconds)

            viewModel.setEvent(Event.CloseHsmDelay)
            coroutineRule.testScope.advanceTimeBy(10.seconds)

            verify(addDocumentInteractor, never()).startIssueDocumentAttested(
                issuanceMethod = IssuanceMethod.OPENID4VCI,
                configIds = setOf(CredentialConfigurationIdentifier("mdoc")),
                issuerId = "https://demo.pid-provider.bundesdruckerei.de",
                walletInstanceAttestationSpec = mockAttestationSpec
            )
            assertNull(viewModel.viewState.value.hsmDelay)
        }

    private suspend fun givenTheHsmIsOverloaded() {
        whenever(walletAttestationInteractor.generateAttestation()).thenReturn(overloadedHsm())
    }

    private fun overloadedHsm(): Flow<WalletAttestationResult> =
        flowOf(WalletAttestationResult.Failure("HSM_UNAVAILABLE", "trace"))

    /** An attestation the HSM never answers. */
    private fun slowHsm(): Flow<WalletAttestationResult> = flow { awaitCancellation() }

    private suspend fun verifyIssuanceStarted() {
        verify(addDocumentInteractor).startIssueDocumentAttested(
            issuanceMethod = IssuanceMethod.OPENID4VCI,
            configIds = setOf(CredentialConfigurationIdentifier("mdoc")),
            issuerId = "https://demo.pid-provider.bundesdruckerei.de",
            walletInstanceAttestationSpec = mockAttestationSpec
        )
    }

    //endregion

    @Test
    fun `when the attestation is rate limited, retry later leaves the step`() = coroutineRule.runTest {
        whenever(walletAttestationInteractor.generateAttestation()).thenReturn(
            flowOf(
                WalletAttestationResult.Failure(
                    "TOO_MANY_REQUESTS",
                    "trace",
                    NetworkFailure.RateLimited(retryAt = Instant.parse("2026-10-24T10:05:00Z")),
                )
            )
        )

        viewModel.setEvent(Event.Init)

        val dialog = assertNotNull(viewModel.viewState.value.errorDialog)
        assertEquals(R.string.global_timeout_too_many_requests_title, dialog.titleRes)

        viewModel.effect.runFlowTest {
            dialog.onPrimaryButtonClick()
            assertTrue(awaitItem() is Effect.Navigation.Pop)
        }
        assertNull(viewModel.viewState.value.errorDialog)
        verify(walletAttestationInteractor, times(1)).generateAttestation()
    }

    @Test
    fun `when the screen is torn down mid-issuance, the Issuance span is left open`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)

            viewModel.onCleared()

            verify(telemetry, never()).endSpan(eq(TelemetryConstants.ISSUANCE), any())
        }

    @Test
    fun `when issuance Completed event, the Issuance span is left open for the later requests`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)

            issuanceEventsFlow.emit(IssuanceEvent.Completed)

            verify(telemetry, never()).endSpan(eq(TelemetryConstants.ISSUANCE), any())
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
    fun `when the scan is asked for with a simulated card, then the read starts without a tap`() = coroutineRule.runTest {
        useSimulatedCard()
        viewModel.setEvent(Event.Init)
        sdkSends(SdkSignal.InsertCard)
        viewModel.goToNfcPromptForEidPin()

        viewModel.setEvent(Event.OnStartScanningClick)

        assertEquals(1, sent("SET_CARD $PSEUDONYM_KEY"))
    }

    @Test
    fun `when the card is being read, then the scan reports the progress it makes`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            sdkSends(progress(42))

            with(viewModel.viewState.value) {
                assertEquals(CardScanStatus.IN_PROGRESS, scanStatus)
                assertEquals(42, readingProgress)
                // The read reports itself, so the screen-wide loader stays away.
                assertFalse(isLoading)
            }
        }

    @Test
    fun `when the code is refused during a read, then the code entry comes back with the warning`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            // The PIN goes in answer to the first request; one attempt fewer is the card refusing it.
            sdkSends(pin(retryCounter = 3))

            sdkSends(pin(retryCounter = 2))

            with(viewModel.viewState.value) {
                // Only the entry screen can do anything about a refused code, so the read ends
                // there by itself rather than on a sheet the user has to cancel.
                assertEquals(CardReaderRoute.ENTER_PIN, currentRoute)
                assertEquals("wrong pin", pinState.supportingText)
                assertNull(scanStatus)
                assertFalse(viewModel.viewState.value.isLoading)
            }
        }

    @Test
    fun `when the entry screen reports an untouched field, then the warning stays`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            sdkSends(pin(retryCounter = 3))
            sdkSends(pin(retryCounter = 2))

            // The field reports its contents as it is created, with nothing typed into it yet.
            viewModel.setEvent(Event.OnPinUpdate)

            assertEquals("wrong pin", viewModel.viewState.value.pinState.supportingText)
        }

    @Test
    fun `when a digit is typed after a refusal, then the warning goes`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            sdkSends(pin(retryCounter = 3))
            sdkSends(pin(retryCounter = 2))

            viewModel.typeIntoCodeField("1")
            viewModel.setEvent(Event.OnPinUpdate)

            assertNull(viewModel.viewState.value.pinState.supportingText)
        }

    @Test
    fun `when a read is given up on, then the scan instructions come back`() =
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
            useSimulatedCard()
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            sdkSends(pin(retryCounter = 3))

            with(viewModel.viewState.value) {
                // The simulator is never asked for a code, so the re-request says nothing about the
                // one that was entered and must not flash the entry screen over the read.
                assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, currentRoute)
                assertEquals(CardScanStatus.READY, scanStatus)
            }
            // The request is still answered, or the SDK waits on it until the workflow times out.
            assertEquals(1, sent("SET_PIN none"))
        }

    @Test
    fun `when a simulated card is recognised before the SDK asks for the PIN, then the PIN is sent once on the request`() =
        coroutineRule.runTest {
            useSimulatedCard()
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            sdkSends(CARD_RECOGNISED)

            // Sent now, before the SDK has reached its PIN entry, the answer would be refused.
            assertEquals(0, sentStartingWith("SET_PIN"))
            assertEquals(0, viewModel.viewState.value.pinState.buffer.length)

            sdkSends(pin(retryCounter = 3))

            assertEquals(1, sent("SET_PIN none"))
            with(viewModel.viewState.value) {
                assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, currentRoute)
                assertEquals(CardScanStatus.IN_PROGRESS, scanStatus)
            }
        }

    @Test
    fun `when a simulated card is asked for the PIN after the scan sheet was closed, then the request is still answered`() =
        coroutineRule.runTest {
            useSimulatedCard()
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            // Closing the sheet clears the scan status but leaves the simulator running.
            viewModel.setEvent(Event.OnCancelScanClick)

            sdkSends(pin(retryCounter = 3))

            assertEquals(1, sent("SET_PIN none"))
            with(viewModel.viewState.value) {
                // The simulator refused nothing, so there is no wrong-PIN warning to show.
                assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, currentRoute)
                assertNull(pinState.supportingText)
            }
        }

    @Test
    fun `when a read completes, then the scan waits for the user before moving on`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            sdkSends(SdkSignal.AuthSucceeded(redirectUrl = "https://redirect"))

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
            sdkSends(SdkSignal.AuthSucceeded(redirectUrl = "https://redirect"))

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
            sdkSends(SdkSignal.AuthSucceeded(redirectUrl = "https://redirect"))
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
            sdkSends(SdkSignal.AuthSucceeded(redirectUrl = "https://redirect"))
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

    @Test
    fun `when the card is recognised before the SDK asks for the PIN, then the PIN waits for the request`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            // Sent now, the PIN would be refused with BAD_STATE.
            sdkSends(CARD_RECOGNISED)
            assertEquals(0, sentStartingWith("SET_PIN"))

            sdkSends(pin(retryCounter = 3))
            assertEquals(1, sent("SET_PIN 123456"))
        }

    @Test
    fun `when the PIN costs an attempt, then it is not sent again`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForEidPin()
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(pin(retryCounter = 3))

        sdkSends(pin(retryCounter = 2))

        assertEquals(1, sentStartingWith("SET_PIN"))
        with(viewModel.viewState.value) {
            assertEquals(CardReaderRoute.ENTER_PIN, currentRoute)
            assertEquals("wrong pin", pinState.supportingText)
        }
    }

    @Test
    fun `when the PIN is asked for again with no attempt spent, then the same PIN is sent again`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            sdkSends(pin(retryCounter = 3))

            // The read dropped before the card judged the PIN.
            sdkSends(pin(retryCounter = 3))

            assertEquals(2, sent("SET_PIN 123456"))
            assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, viewModel.viewState.value.currentRoute)
        }

    @Test
    fun `when a PIN refused as malformed is asked for again, then it is not sent again`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForEidPin()
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(pin(retryCounter = 3))

        sdkSends(pin(retryCounter = 3, isInputRejected = true))

        assertEquals(1, sentStartingWith("SET_PIN"))
        assertEquals(CardReaderRoute.ENTER_PIN, viewModel.viewState.value.currentRoute)
    }

    @Test
    fun `when the SDK refuses the PIN with a workflow running, then the request that follows gets it`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            sdkSends(pin(retryCounter = 3))

            sdkSends(SdkSignal.BadState("SET_PIN"))

            // The refused PIN never reached the card, so the request still stands: the engine asks
            // for it again, and it is answered with the PIN kept for one more send.
            assertEquals(2, sent("SET_PIN 123456"))
            assertEquals(1, sent("GET_STATUS"))
            sdkSends(progress(10))
            assertNull(viewModel.viewState.value.errorDialog)
        }

    @Test
    fun `when the SDK refuses a command and no workflow is running, then the flow ends with an error`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()

            sdkSends(SdkSignal.BadState("SET_PIN"))
            sdkSends(NO_WORKFLOW_RUNNING)

            val dialog = requireNotNull(viewModel.viewState.value.errorDialog)
            assertEquals("EID_BAD_STATE_SET_PIN", dialog.errorCode)
            assertEquals(R.string.nfc_scanning_system_sheet_nfc_paragraph_error_button, dialog.primaryButtonTextRes)
            assertEquals(0, sent("CANCEL"))

            // Trying again starts the issuance over, which asks for a fresh authentication.
            dialog.onPrimaryButtonClick()

            assertNull(viewModel.viewState.value.errorDialog)
            verify(addDocumentInteractor, times(1)).cancelIssuance()
            verify(addDocumentInteractor, times(2)).startIssueDocumentAttested(
                issuanceMethod = IssuanceMethod.OPENID4VCI,
                configIds = setOf(CredentialConfigurationIdentifier("mdoc")),
                issuerId = "https://demo.pid-provider.bundesdruckerei.de",
                walletInstanceAttestationSpec = mockAttestationSpec,
            )
            assertEquals(2, sent("RUN_AUTH"))
        }

    @Test
    fun `when no workflow is reported without a refused command, then nothing happens`() = runTest {
        startIssuance()

        sdkSends(NO_WORKFLOW_RUNNING)

        assertNull(viewModel.viewState.value.errorDialog)
    }

    @Test
    fun `when the SDK refuses a cancel, then the workflow is not checked`() = runTest {
        startIssuance()

        sdkSends(SdkSignal.BadState("CANCEL"))

        assertEquals(0, sent("GET_STATUS"))
        assertNull(viewModel.viewState.value.errorDialog)
    }

    @Test
    fun `when the SDK refuses to start the authentication, then the flow ends with an error`() =
        runTest {
            startIssuance()

            sdkSends(SdkSignal.BadState("RUN_AUTH"))

            assertEquals("EID_BAD_STATE_RUN_AUTH", viewModel.viewState.value.errorDialog?.errorCode)
            assertEquals(0, sent("GET_STATUS"))
        }

    @Test
    fun `when the SDK refuses to start the PIN change, then the flow ends with an error`() =
        runTest {
            startIssuance()
            startSdk()
            viewModel.setEvent(Event.StartTransportPin)
            sdkSends(SdkSignal.AuthFailed("User_Cancelled"))
            assertEquals(1, sent("RUN_CHANGE_PIN"))

            sdkSends(SdkSignal.BadState("RUN_CHANGE_PIN"))

            assertEquals("EID_BAD_STATE_RUN_CHANGE_PIN", viewModel.viewState.value.errorDialog?.errorCode)
            assertEquals(0, sent("GET_STATUS"))
        }

    @Test
    fun `when a read fails in a way a retry can mend, then the failed sheet offers it`() = runTest {
        startIssuance()
        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)
        sdkSends(pin(retryCounter = 3))
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnContinueClickCardPin)
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(CARD_RECOGNISED)

        sdkSends(SdkSignal.AuthFailed("Card_Removed"))

        with(viewModel.viewState.value) {
            assertEquals(CardScanStatus.FAILED, scanStatus)
            assertNull(errorDialog)
        }
        assertEquals(0, sent("CANCEL"))

        viewModel.setEvent(Event.OnScanRetryClick)

        // The read runs again from the scan it failed on.
        with(viewModel.viewState.value) {
            assertEquals(CardReaderRoute.NFC_SCAN_EID_PIN, currentRoute)
            assertEquals(CardScanStatus.READY, scanStatus)
        }
        verify(addDocumentInteractor, times(1)).cancelIssuance()
        // The consent the user gave stays given for the fresh workflow.
        sdkSends(ACCESS_RIGHTS)
        assertEquals(1, sent("ACCEPT"))
        // And its PIN request is answered with the PIN kept for one more send.
        sdkSends(pin(retryCounter = 3))
        assertEquals(2, sent("SET_PIN 123456"))
    }

    @Test
    fun `when the workflow fails in a way no retry mends, then the error names the reason and leaving goes to the dashboard`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            sdkSends(SdkSignal.AuthFailed("Certificate_Check_Failed_Hash_Mismatch"))

            val dialog = requireNotNull(viewModel.viewState.value.errorDialog)
            with(viewModel.viewState.value) {
                assertEquals("Certificate_Check_Failed_Hash_Mismatch", dialog.errorCode)
                assertNull(scanStatus)
                assertEquals(0, pinState.buffer.length)
            }
            // Anything the dead workflow still sends is no longer this screen's business.
            sdkSends(pin(retryCounter = 3))
            assertEquals(0, sentStartingWith("SET_PIN"))

            dialog.onPrimaryButtonClick()

            assertNull(viewModel.viewState.value.errorDialog)
            viewModel.effect.runFlowTest {
                var effect = awaitItem()
                while (effect !is Effect.Navigation) {
                    effect = awaitItem()
                }
                assertTrue(effect is Effect.Navigation.Pop)
            }
        }

    @Test
    fun `when the user consents before the SDK asks for the rights, then they are accepted once asked for`() =
        runTest {
            startIssuance()

            viewModel.setEvent(Event.AcceptRightsAndEnterPin)
            assertEquals(0, sent("ACCEPT"))

            sdkSends(ACCESS_RIGHTS)
            assertEquals(1, sent("ACCEPT"))
        }

    @Test
    fun `when the transport PIN flow starts during the authentication, then that ends before the PIN change starts`() =
        runTest {
            startIssuance()
            startSdk()
            sdkSends(ACCESS_RIGHTS)

            viewModel.setEvent(Event.TransportPinLetter)
            viewModel.setEvent(Event.StartTransportPin)

            assertEquals(1, sent("CANCEL"))
            assertEquals(0, sent("ACCEPT"))
            assertEquals(0, sent("RUN_CHANGE_PIN"))
            verify(addDocumentInteractor, times(1)).cancelIssuance()

            // The cancelled authentication reports its end; that is no failure to show.
            sdkSends(SdkSignal.AuthFailed("User_Cancelled"))

            assertEquals(1, sent("RUN_CHANGE_PIN"))
            assertNull(viewModel.viewState.value.errorDialog)
            assertEquals(CardReaderRoute.ENTER_TRANSPORT_PIN, viewModel.viewState.value.currentRoute)
        }

    @Test
    fun `when the transport PIN flow is left for the issuance, then the authentication starts again`() =
        runTest {
            startIssuance()
            startSdk()
            viewModel.setEvent(Event.TransportPinLetter)
            viewModel.setEvent(Event.StartTransportPin)
            sdkSends(SdkSignal.AuthFailed("User_Cancelled"))

            viewModel.setEvent(Event.Pop)
            viewModel.setEvent(Event.Pop)

            assertEquals(CardReaderFlowType.ISSUANCE, viewModel.viewState.value.activeFlowType)
            // The PIN change is ended first, then the issuance asks for a fresh authentication.
            assertEquals(2, sent("CANCEL"))
            sdkSends(SdkSignal.ChangePinFailed("User_Cancelled"))
            assertNull(viewModel.viewState.value.errorDialog)
            verify(addDocumentInteractor, times(2)).startIssueDocumentAttested(
                issuanceMethod = IssuanceMethod.OPENID4VCI,
                configIds = setOf(CredentialConfigurationIdentifier("mdoc")),
                issuerId = "https://demo.pid-provider.bundesdruckerei.de",
                walletInstanceAttestationSpec = mockAttestationSpec,
            )
            verify(walletAttestationInteractor, times(2)).generateAttestation()
            assertEquals(2, sent("RUN_AUTH"))
        }

    @Test
    fun `when a detour is taken inside the transport PIN flow, then back still leads to the issuance`() =
        runTest {
            startTransportPinFlowDuringAuthentication()

            viewModel.setEvent(Event.OnNoPinLetterButtonClick)
            viewModel.setEvent(Event.Pop)
            assertEquals(CardReaderRoute.ENTER_TRANSPORT_PIN, viewModel.viewState.value.currentRoute)
            viewModel.setEvent(Event.Pop)
            viewModel.setEvent(Event.Pop)

            assertEquals(CardReaderFlowType.ISSUANCE, viewModel.viewState.value.activeFlowType)
            assertNull(viewModel.viewState.value.returnTarget)
            sdkSends(SdkSignal.ChangePinFailed("User_Cancelled"))
            verify(walletAttestationInteractor, times(2)).generateAttestation()
        }

    @Test
    fun `when the PIN change completes after its cancel, then the issuance it was left for goes on`() =
        runTest {
            startTransportPinFlowDuringAuthentication()
            viewModel.setEvent(Event.Pop)
            viewModel.setEvent(Event.Pop)

            sdkSends(SdkSignal.ChangePinSucceeded)

            // The screen left the PIN change for the issuance, which has started over already and
            // now gets its authentication: the late success no longer moves the screen.
            assertEquals(CardReaderFlowType.ISSUANCE, viewModel.viewState.value.activeFlowType)
            assertNull(viewModel.viewState.value.errorDialog)
            verify(walletAttestationInteractor, times(2)).generateAttestation()
            assertEquals(2, sent("RUN_AUTH"))
        }

    @Test
    fun `when the SDK does not report the end of a cancelled workflow, then a status report releases the next one`() =
        runTest {
            startIssuance()
            startSdk()
            viewModel.setEvent(Event.TransportPinLetter)
            viewModel.setEvent(Event.StartTransportPin)

            coroutineRule.testScope.testScheduler.advanceTimeBy(UNREPORTED_END_WAIT_MS)
            coroutineRule.testScope.testScheduler.runCurrent()
            assertEquals(1, sent("GET_STATUS"))
            sdkSends(NO_WORKFLOW_RUNNING)

            assertEquals(1, sent("RUN_CHANGE_PIN"))
            assertNull(viewModel.viewState.value.errorDialog)
        }

    @Test
    fun `when the card is deactivated, then the user is sent to the citizen's office`() = runTest {
        startIssuance()

        sdkSends(CARD_DEACTIVATED)

        val dialog = requireNotNull(viewModel.viewState.value.errorDialog)
        assertEquals("EID_CARD_DEACTIVATED", dialog.errorCode)
        assertEquals(R.string.eid_card_deactivated_paragraph, dialog.bodyTextRes)
        assertEquals(R.string.global_office_button, dialog.primaryButtonTextRes)
    }

    @Test
    fun `when the card is deactivated during the authentication, then that workflow is ended and the codes erased`() =
        runTest {
            startIssuance()
            startSdk()
            viewModel.goToNfcPromptForEidPin()

            sdkSends(CARD_DEACTIVATED)

            assertEquals(1, sent("CANCEL"))
            assertEquals(0, viewModel.viewState.value.pinState.buffer.length)
            // The cancelled workflow's end is no new failure.
            sdkSends(SdkSignal.AuthFailed("User_Cancelled"))
            assertEquals("EID_CARD_DEACTIVATED", viewModel.viewState.value.errorDialog?.errorCode)
        }

    @Test
    fun `when a failure off the scan is tried again, then the loader shows until the restart answers`() =
        runTest {
            startIssuance()
            sdkSends(SdkSignal.AuthFailed("Card_Removed"))
            val dialog = requireNotNull(viewModel.viewState.value.errorDialog)

            dialog.onPrimaryButtonClick()

            assertTrue(viewModel.viewState.value.isLoading)
            sdkSends(ACCESS_RIGHTS)
            assertFalse(viewModel.viewState.value.isLoading)
        }

    @Test
    fun `when a retried authentication asks for the rights again, then the consent already given accepts them`() =
        runTest {
            startIssuance()
            viewModel.setEvent(Event.AcceptRightsAndEnterPin)
            sdkSends(ACCESS_RIGHTS)
            sdkSends(SdkSignal.AuthFailed("Card_Removed"))

            requireNotNull(viewModel.viewState.value.errorDialog).onPrimaryButtonClick()
            sdkSends(ACCESS_RIGHTS)

            assertEquals(2, sent("ACCEPT"))
        }

    @Test
    fun `when the card asks for the PUK, then the flow ends with an error`() = runTest {
        startIssuance()

        sdkSends(SdkSignal.EnterPuk(card(retryCounter = 0)))

        assertEquals("EID_PIN_BLOCKED", viewModel.viewState.value.errorDialog?.errorCode)
    }

    @Test
    fun `when the SDK pauses the read, then the card is asked for again and the workflow continued`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            sdkSends(progress(40))

            sdkSends(SdkSignal.Paused("BadCardPosition"))

            assertEquals(1, sent("CONTINUE"))
            with(viewModel.viewState.value) {
                assertEquals(CardScanStatus.READY_AGAIN, scanStatus)
                assertNull(readingProgress)
            }
        }

    @Test
    fun `when a simulated card is asked for its new PIN, then none is sent rather than the typed one`() =
        runTest {
            useSimulatedCard()
            startIssuance()

            viewModel.goToNfcPromptForNewPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            // The simulator takes no value, so the request was answered with none when it came.
            assertEquals(1, sent("SET_NEW_PIN none"))
            assertEquals(0, sent("SET_NEW_PIN 123456"))
        }

    @Test
    fun `when the scan for the new PIN starts with a physical card left in place, then the awaited new PIN is sent once`() =
        runTest {
            startIssuance()

            viewModel.goToNfcPromptForNewPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            // A card resting on the phone is not recognised again, so the scan start sends it.
            assertEquals(1, sent("SET_NEW_PIN 123456"))

            // Recognised again once the read is under way, it is sent nothing more.
            sdkSends(progress(10), CARD_RECOGNISED)
            assertEquals(1, sentStartingWith("SET_NEW_PIN"))
        }

    @Test
    fun `when the scan starts before the SDK asks for the new PIN, then the card's request sends it`() =
        runTest {
            startIssuance()
            viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
            viewModel.setEvent(Event.StartTransportPin)
            viewModel.setEvent(Event.OnSetNewPinPrimaryButtonClick)
            viewModel.typeIntoCodeField("123456")
            viewModel.setEvent(Event.OnContinueClickOnSetNewPin)
            viewModel.typeIntoCodeField("123456")
            viewModel.setEvent(Event.OnNewPinConfirmUpdate)
            viewModel.setEvent(Event.OnContinueClickOnConfirmNewPin)

            viewModel.setEvent(Event.OnStartScanningClick)
            assertEquals(0, sentStartingWith("SET_NEW_PIN"))

            sdkSends(SdkSignal.EnterNewPin(isInputRejected = false))
            assertEquals(1, sent("SET_NEW_PIN 123456"))
        }

    @Test
    fun `when a status trails a failed read, then the failed sheet stays`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForEidPin()
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(SdkSignal.AuthFailed("Card_Removed"))

        sdkSends(progress(0))

        with(viewModel.viewState.value) {
            assertEquals(CardScanStatus.FAILED, scanStatus)
            assertNull(readingProgress)
        }
    }

    @Test
    fun `when an automatic status comes between a refusal and the status answer, then a lost workflow is still noticed`() =
        runTest {
            startIssuance()

            sdkSends(SdkSignal.BadState("SET_PIN"))
            sdkSends(progress(0))
            sdkSends(NO_WORKFLOW_RUNNING)

            assertEquals("EID_BAD_STATE_SET_PIN", viewModel.viewState.value.errorDialog?.errorCode)
        }

    @Test
    fun `when a refused command's workflow ends on its own, then a later status says nothing`() = runTest {
        startIssuance()

        sdkSends(SdkSignal.BadState("ACCEPT"))
        sdkSends(SdkSignal.AuthSucceeded("https://redirect"))
        sdkSends(NO_WORKFLOW_RUNNING)

        assertNull(viewModel.viewState.value.errorDialog)
    }

    @Test
    fun `when the PIN is asked for again with no attempt spent and no copy left, then no PIN is blamed`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            sdkSends(pin(retryCounter = 3))
            // The first drop spends the copy kept for a resend.
            sdkSends(pin(retryCounter = 3))

            sdkSends(pin(retryCounter = 3))

            with(viewModel.viewState.value) {
                assertEquals(CardReaderRoute.ENTER_PIN, currentRoute)
                assertNull(pinState.supportingText)
            }
        }

    @Test
    fun `when the online function is off, then the dialog says so and links to the citizen's office`() =
        runTest {
            startIssuance()
            whenever(resourceProvider.getLocale()).thenReturn(Locale.GERMAN)

            sdkSends(SdkSignal.AuthFailed("Connect_Card_Eid_Inactive"))

            val dialog = requireNotNull(viewModel.viewState.value.errorDialog)
            assertEquals(R.string.eid_card_deactivated_paragraph, dialog.bodyTextRes)
            dialog.onPrimaryButtonClick()
            viewModel.effect.runFlowTest {
                var effect = awaitItem()
                while (effect !is Effect.Navigation.OpenLink) {
                    effect = awaitItem()
                }
                assertEquals("de", effect.uri.fragment)
            }
        }

    @Test
    fun `when the PUK is used up, then the dialog asks for a new PIN letter`() = runTest {
        startIssuance()

        sdkSends(SdkSignal.EnterPuk(CardInfo(isDeactivated = false, isPukInoperative = true, pinRetryCounter = 0)))

        assertEquals(
            R.string.pid_issuance_no_letter_forgot_info_paragraph,
            viewModel.viewState.value.errorDialog?.bodyTextRes,
        )
    }

    @Test
    fun `when the transport PIN flow is started twice, then the PIN change starts once`() = runTest {
        startIssuance()
        startSdk()

        viewModel.setEvent(Event.StartTransportPin)
        viewModel.setEvent(Event.StartTransportPin)
        sdkSends(SdkSignal.AuthFailed("User_Cancelled"))

        assertEquals(1, sent("RUN_CHANGE_PIN"))
        // Only the authentication is cancelled: a second tap does not cancel the PIN change.
        assertEquals(1, sent("CANCEL"))
    }

    @Test
    fun `when a PIN change read fails and is tried again, then the PIN change starts over at the transport PIN`() =
        runTest {
            startIssuance()
            viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
            viewModel.setEvent(Event.StartTransportPin)
            startSdk()
            viewModel.typeIntoCodeField("12345")
            viewModel.setEvent(Event.OnTransportPinUpdate)
            viewModel.setEvent(Event.OnStartScanningClick)

            sdkSends(SdkSignal.ChangePinFailed("Change_Pin_Unrecoverable"))
            assertEquals(CardScanStatus.FAILED, viewModel.viewState.value.scanStatus)

            viewModel.setEvent(Event.OnScanRetryClick)

            assertEquals(2, sent("RUN_CHANGE_PIN"))
            with(viewModel.viewState.value) {
                assertEquals(CardReaderRoute.ENTER_TRANSPORT_PIN, currentRoute)
                assertNull(scanStatus)
                assertEquals(0, pinState.buffer.length)
            }
        }

    @Test
    fun `when a CAN read fails and is tried again, then the scan runs again and asks for the CAN once needed`() = runTest {
        startIssuance()
        sdkSends(can())
        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(CARD_RECOGNISED)
        sdkSends(SdkSignal.AuthFailed("Card_Removed"))

        viewModel.setEvent(Event.OnScanRetryClick)

        with(viewModel.viewState.value) {
            assertEquals(CardReaderRoute.NFC_SCAN_CAN, currentRoute)
            assertEquals(CardScanStatus.READY, scanStatus)
        }
        // The CAN already went to the card, so the fresh workflow asks the user for it again,
        // blaming no CAN.
        sdkSends(can())
        with(viewModel.viewState.value) {
            assertEquals(CardReaderRoute.ENTER_CAN, currentRoute)
            assertNull(pinState.supportingText)
        }
    }

    @Test
    fun `when the card was lifted while the CAN was typed, then the CAN waits for the card`() = runTest {
        startIssuance()
        sdkSends(CARD_RECOGNISED)
        sdkSends(can())
        sdkSends(CARD_REMOVED)
        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)

        viewModel.setEvent(Event.OnStartScanningClick)
        assertEquals(0, sentStartingWith("SET_CAN"))

        sdkSends(CARD_RECOGNISED)
        assertEquals(1, sent("SET_CAN 123456"))
    }

    @Test
    fun `when the card stayed in place while the CAN was typed, then the scan start sends it`() = runTest {
        startIssuance()
        sdkSends(CARD_RECOGNISED)
        sdkSends(can())
        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)

        viewModel.setEvent(Event.OnStartScanningClick)

        assertEquals(1, sent("SET_CAN 123456"))
    }

    @Test
    fun `when the SDK asks for something after a refusal, then a later status without a workflow says nothing`() =
        runTest {
            startIssuance()

            sdkSends(SdkSignal.BadState("ACCEPT"))
            sdkSends(SdkSignal.InsertCard)
            sdkSends(NO_WORKFLOW_RUNNING)

            assertNull(viewModel.viewState.value.errorDialog)
        }

    @Test
    fun `when the PIN change is asked for before the SDK is ready, then it starts once the SDK is`() = runTest {
        startIssuance()

        viewModel.setEvent(Event.StartTransportPin)
        assertEquals(0, sent("RUN_CHANGE_PIN"))

        startSdk()
        assertEquals(1, sent("RUN_CHANGE_PIN"))
    }

    @Test
    fun `when the SDK pauses after a failed read, then the failed sheet stays and the workflow is not continued`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            sdkSends(SdkSignal.Error("EID_CARD_UNREADABLE"))

            sdkSends(SdkSignal.Paused("BadCardPosition"))

            assertEquals(CardScanStatus.FAILED, viewModel.viewState.value.scanStatus)
            assertEquals(0, sent("CONTINUE"))
        }

    @Test
    fun `when the user is sent to the citizen's office, then the typed codes are erased`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForEidPin()

        sdkSends(CARD_DEACTIVATED)

        assertEquals(0, viewModel.viewState.value.pinState.buffer.length)
    }

    @Test
    fun `when the SDK asks for the new PIN, then the sheet is titled for it`() = runTest {
        whenever(resourceProvider.getString(R.string.new_pin_set_bottom_sheet_title)).thenReturn("new pin")
        startIssuance()

        sdkSends(SdkSignal.EnterNewPin(isInputRejected = false))

        assertEquals("new pin", viewModel.viewState.value.bottomSheetTitle)
    }

    @Test
    fun `when the PIN change succeeds, then the issuance goes on at its steps screen`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForNewPin()
        viewModel.setEvent(Event.OnStartScanningClick)

        sdkSends(SdkSignal.ChangePinSucceeded)
        assertEquals(CardScanStatus.SUCCESS, viewModel.viewState.value.scanStatus)
        viewModel.setEvent(Event.OnScanContinueClick)

        with(viewModel.viewState.value) {
            assertEquals(CardReaderFlowType.ISSUANCE, activeFlowType)
            assertEquals(CardReaderRoute.PROGRESS_STEPS, currentRoute)
            assertNull(scanStatus)
        }
        // The SDK stays connected, and the issuance starts again with a fresh attestation.
        assertEquals(0, sent("DISCONNECT"))
        verify(walletAttestationInteractor, times(2)).generateAttestation()
        assertEquals(1, sent("RUN_AUTH"))
    }

    @Test
    fun `when the PIN change succeeds after the consent was given, then the issuance still goes on at its steps screen`() =
        runTest {
            startIssuance()
            viewModel.setEvent(Event.AcceptRightsAndEnterPin)
            viewModel.goToNfcPromptForNewPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            sdkSends(SdkSignal.ChangePinSucceeded)
            viewModel.setEvent(Event.OnScanContinueClick)

            with(viewModel.viewState.value) {
                assertEquals(CardReaderFlowType.ISSUANCE, activeFlowType)
                assertEquals(CardReaderRoute.PROGRESS_STEPS, currentRoute)
                assertEquals(0, pinState.buffer.length)
            }
        }

    @Test
    fun `when progress is reported before the scan is started, then no scan sheet opens`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForEidPin()

        sdkSends(progress(40))

        assertNull(viewModel.viewState.value.scanStatus)
    }

    @Test
    fun `when a read reports progress while a refusal is checked, then the progress shows`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForEidPin()
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(SdkSignal.BadState("SET_PIN"))

        sdkSends(progress(0))
        assertNull(viewModel.viewState.value.readingProgress)
        sdkSends(progress(55))

        assertEquals(55, viewModel.viewState.value.readingProgress)
    }

    @Test
    fun `when progress is reported while the card has to be put back, then the sheet keeps asking for it`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)
            sdkSends(CARD_RECOGNISED)
            sdkSends(CARD_REMOVED)

            sdkSends(progress(10))

            assertEquals(CardScanStatus.READY_AGAIN, viewModel.viewState.value.scanStatus)
        }

    @Test
    fun `when a simulated card goes through the transport PIN flow, then its PINs are answered with none`() =
        runTest {
            useSimulatedCard()
            startIssuance()
            viewModel.setEvent(Event.StartTransportPin)
            viewModel.typeIntoCodeField("12345")
            viewModel.setEvent(Event.OnTransportPinUpdate)
            viewModel.setEvent(Event.OnStartScanningClick)

            sdkSends(pin(retryCounter = 3))
            assertEquals(1, sent("SET_PIN none"))

            // No new-PIN screens: the request is answered with none and the read goes on.
            sdkSends(SdkSignal.EnterNewPin(isInputRejected = false))
            assertEquals(1, sent("SET_NEW_PIN none"))
            assertEquals(CardReaderRoute.NFC_SCAN_TRANSPORT_PIN, viewModel.viewState.value.currentRoute)

            sdkSends(SdkSignal.ChangePinSucceeded)
            assertEquals(CardScanStatus.SUCCESS, viewModel.viewState.value.scanStatus)
        }

    /** Takes the PIN change past the transport PIN up to the scan prompt for the new PIN. */
    private fun ReadCardViewModel.goToNfcPromptForNewPin() {
        setEvent(Event.OnResume(isNfcEnabled = true))
        setEvent(Event.StartTransportPin)
        typeIntoCodeField("12345")
        setEvent(Event.OnTransportPinUpdate)
        assertEquals(CardReaderRoute.NFC_SCAN_TRANSPORT_PIN, viewState.value.currentRoute)
        // The SDK asks for the new PIN once the transport PIN is accepted, with the card in place.
        sdkSends(CARD_RECOGNISED)
        sdkSends(SdkSignal.EnterNewPin(isInputRejected = false))
        setEvent(Event.OnSetNewPinPrimaryButtonClick)
        typeIntoCodeField("123456")
        setEvent(Event.OnContinueClickOnSetNewPin)
        typeIntoCodeField("123456")
        setEvent(Event.OnNewPinConfirmUpdate)
        setEvent(Event.OnContinueClickOnConfirmNewPin)
        assertEquals(CardReaderRoute.NFC_SCAN_NEW_PIN, viewState.value.currentRoute)
    }

    @Test
    fun `when the workflow fails before a scan, then the loader goes`() =
        coroutineRule.runTest {
            viewModel.setEvent(Event.Init)

            sdkSends(SdkSignal.AuthStartFailed)

            assertFalse(viewModel.viewState.value.isLoading)
        }

    @Test
    fun `when the card is recognised twice for one CAN request, then the CAN is sent once`() = runTest {
        startIssuance()
        sdkSends(can())
        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
        viewModel.setEvent(Event.OnEnterCanButtonPress)
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnCanUpdate)
        viewModel.setEvent(Event.OnStartScanningClick)

        sdkSends(CARD_RECOGNISED)
        sdkSends(progress(10))
        sdkSends(CARD_RECOGNISED)

        assertEquals(1, sentStartingWith("SET_CAN"))
    }

    @Test
    fun `when the SDK cannot be asked for the status after a refusal, then the flow ends with an error`() =
        runTest {
            startIssuance()
            startSdk()
            sdk.isConnected = false

            sdkSends(SdkSignal.BadState("SET_PIN"))

            assertEquals("EID_BAD_STATE_SET_PIN", viewModel.viewState.value.errorDialog?.errorCode)
        }

    @Test
    fun `when the status asked after a refusal comes back, then it is not shown as read progress`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()
            viewModel.setEvent(Event.OnStartScanningClick)

            sdkSends(SdkSignal.BadState("SET_PIN"))
            sdkSends(progress(0))

            with(viewModel.viewState.value) {
                assertEquals(CardScanStatus.READY, scanStatus)
                assertNull(readingProgress)
            }
            // The SDK's next request settles the check, so a later report is progress again.
            sdkSends(SdkSignal.InsertCard)
            sdkSends(progress(30))
            assertEquals(30, viewModel.viewState.value.readingProgress)
        }

    @Test
    fun `when the PIN is asked for with an unknown retry counter, then no PIN is blamed`() = runTest {
        startIssuance()

        sdkSends(pin(retryCounter = 0))

        with(viewModel.viewState.value) {
            assertEquals(CardReaderRoute.ENTER_PIN, currentRoute)
            assertNull(pinState.supportingText)
        }
    }

    @Test
    fun `when the card is lifted before its read is through, then the scan asks for it again`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForEidPin()
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(CARD_RECOGNISED)
        sdkSends(progress(20))

        sdkSends(CARD_REMOVED)

        with(viewModel.viewState.value) {
            assertEquals(CardScanStatus.READY_AGAIN, scanStatus)
            assertNull(readingProgress)
            assertFalse(isLoading)
        }
        // Put back, the card is read again.
        sdkSends(CARD_RECOGNISED)
        assertEquals(CardScanStatus.IN_PROGRESS, viewModel.viewState.value.scanStatus)
    }

    @Test
    fun `when a card is put down on the scan instructions, then it is read only once the scan is started`() = runTest {
        startIssuance()
        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)
        sdkSends(pin(retryCounter = 3))
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnContinueClickCardPin)
        assertNull(viewModel.viewState.value.scanStatus)

        sdkSends(CARD_RECOGNISED)
        assertNull(viewModel.viewState.value.scanStatus)
        assertEquals(0, sentStartingWith("SET_PIN"))

        viewModel.setEvent(Event.OnStartScanningClick)

        // Already on the reader, the card is read at once.
        assertEquals(CardScanStatus.IN_PROGRESS, viewModel.viewState.value.scanStatus)
        assertEquals(1, sent("SET_PIN 123456"))
    }

    @Test
    fun `when the scan starts with the card already on the reader, then the read starts at once`() = runTest {
        startIssuance()
        viewModel.setEvent(Event.OnResume(isNfcEnabled = true))
        viewModel.setEvent(Event.AcceptRightsAndEnterPin)
        // The card is put down while the PIN is still being typed, and the SDK asks for it.
        sdkSends(CARD_RECOGNISED)
        sdkSends(pin(retryCounter = 3))
        viewModel.typeIntoCodeField("123456")
        viewModel.setEvent(Event.OnContinueClickCardPin)

        viewModel.setEvent(Event.OnStartScanningClick)

        assertEquals(CardScanStatus.IN_PROGRESS, viewModel.viewState.value.scanStatus)
        assertEquals(1, sent("SET_PIN 123456"))
    }

    @Test
    fun `when the card is recognised again after a successful read, then the success stays`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForEidPin()
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(SdkSignal.AuthSucceeded("https://redirect"))
        assertEquals(CardScanStatus.SUCCESS, viewModel.viewState.value.scanStatus)

        sdkSends(CARD_RECOGNISED)

        assertEquals(CardScanStatus.SUCCESS, viewModel.viewState.value.scanStatus)
    }

    @Test
    fun `when the SDK asks for a card after one was recognised, then the scan waits for it to be presented`() =
        runTest {
            startIssuance()
            viewModel.goToNfcPromptForEidPin()
            sdkSends(CARD_RECOGNISED)

            sdkSends(SdkSignal.InsertCard)
            viewModel.setEvent(Event.OnStartScanningClick)

            assertEquals(CardScanStatus.READY, viewModel.viewState.value.scanStatus)
        }

    @Test
    fun `when the SDK pauses after a successful read, then the success stays`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForEidPin()
        viewModel.setEvent(Event.OnStartScanningClick)
        sdkSends(SdkSignal.AuthSucceeded("https://redirect"))

        sdkSends(SdkSignal.Paused("BadCardPosition"))

        assertEquals(CardScanStatus.SUCCESS, viewModel.viewState.value.scanStatus)
        assertEquals(0, sent("CONTINUE"))
    }

    @Test
    fun `when the card has to be put back, then each time counts once for the animation`() = runTest {
        startIssuance()
        viewModel.goToNfcPromptForEidPin()
        viewModel.setEvent(Event.OnStartScanningClick)

        sdkSends(CARD_RECOGNISED)
        sdkSends(CARD_REMOVED)
        // Put back, the read goes on without counting.
        sdkSends(CARD_RECOGNISED)
        assertEquals(1, viewModel.viewState.value.readyAgainCount)
        sdkSends(CARD_REMOVED)

        assertEquals(2, viewModel.viewState.value.readyAgainCount)
    }

    private fun ReadCardViewModel.requestRights() {
        setEvent(Event.Init)
        sdkSends(ACCESS_RIGHTS)
    }

    /** Starts the transport PIN flow while the authentication runs, and reports the end of that. */
    private fun startTransportPinFlowDuringAuthentication() {
        startIssuance()
        startSdk()
        viewModel.setEvent(Event.TransportPinLetter)
        viewModel.setEvent(Event.StartTransportPin)
        sdkSends(SdkSignal.AuthFailed("User_Cancelled"))
    }

    /** Sets the screen up, which starts the issuance and with it the authentication it asks for. */
    private fun startIssuance() {
        viewModel.setEvent(Event.Init)
        runCurrent()
    }

    /** Makes the SDK the simulated card, before the screen and its workflows are set up. */
    private fun useSimulatedCard() {
        whenever(userRuntimeConfig.eidCardType).thenReturn(EidCardType.VIRTUAL)
        whenever(userRuntimeConfig.simulatedEidCardPseudonymKey).thenReturn(PSEUDONYM_KEY)
        viewModel = createViewModel()
    }

    /** The SDK reports that it takes workflows, which starts the one waiting. */
    private fun startSdk() {
        isSdkStarted = true
        sdk.emit(SdkSignal.Started)
        runCurrent()
    }

    /** Plays [signals] from the SDK, which is started first if it has not been yet. */
    private fun sdkSends(vararg signals: SdkSignal) {
        if (!isSdkStarted) startSdk()
        signals.forEach {
            sdk.emit(it)
            runCurrent()
        }
    }

    private fun runCurrent() = coroutineRule.testScope.testScheduler.runCurrent()

    private fun sent(command: String): Int = sdk.commands.count { it == command }

    private fun sentStartingWith(command: String): Int =
        sdk.commands.count { it == command || it.startsWith("$command ") }
}

private const val TC_TOKEN_URL = "https://pid.example/tcToken"
private val PID_PRIVACY_POLICY = LocalizedUrl(
    en = "https://example.org/pid-privacy",
    de = "https://example.org/de/pid-privacy",
)
private const val PSEUDONYM_KEY = "pseudonym-key"
private const val UNREPORTED_END_WAIT_MS = 3_000L

private fun card(retryCounter: Int = 3, isDeactivated: Boolean = false) =
    CardInfo(isDeactivated = isDeactivated, isPukInoperative = false, pinRetryCounter = retryCounter)

private fun pin(retryCounter: Int, isInputRejected: Boolean = false) =
    SdkSignal.EnterPin(isInputRejected = isInputRejected, card = card(retryCounter))

private fun can(isInputRejected: Boolean = false) =
    SdkSignal.EnterCan(isInputRejected = isInputRejected, card = card(retryCounter = 1))

private fun progress(percent: Int) = SdkSignal.Status(isWorkflowRunning = true, progress = percent)

private val CARD_RECOGNISED = SdkSignal.Reader(attached = true, card = card())
private val CARD_REMOVED = SdkSignal.Reader(attached = true, card = null)
private val CARD_DEACTIVATED = SdkSignal.Reader(attached = true, card = card(isDeactivated = true))
private val NO_WORKFLOW_RUNNING = SdkSignal.Status(isWorkflowRunning = false, progress = null)
private val ACCESS_RIGHTS = SdkSignal.AccessRights(
    required = listOf(AccessRight.FAMILY_NAME),
    effective = listOf(AccessRight.FAMILY_NAME),
    transactionInfo = null,
)

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

/** Records the commands sent to the SDK, codes in the clear, and plays the signals a test emits. */
private class FakeEidSdkPort : EidSdkPort {

    val commands = mutableListOf<String>()

    private val queue = Channel<SdkSignal>(Channel.UNLIMITED)

    override val signals: Flow<SdkSignal> = queue.receiveAsFlow()

    override var isConnected = false

    fun emit(signal: SdkSignal) {
        queue.trySend(signal)
    }

    override fun connect() {
        commands += "CONNECT"
        isConnected = true
    }

    override fun disconnect() {
        commands += "DISCONNECT"
        isConnected = false
    }

    override fun runAuth(tcTokenUrl: String) {
        commands += "RUN_AUTH"
    }

    override fun runChangePin() {
        commands += "RUN_CHANGE_PIN"
    }

    override fun accept() {
        commands += "ACCEPT"
    }

    override fun setPin(pin: UserPin?) {
        commands += "SET_PIN ${pin.reveal()}"
    }

    override fun setCan(can: UserPin) {
        commands += "SET_CAN ${can.reveal()}"
    }

    override fun setNewPin(newPin: UserPin?) {
        commands += "SET_NEW_PIN ${newPin.reveal()}"
    }

    override fun setPuk(puk: UserPin) {
        commands += "SET_PUK ${puk.reveal()}"
    }

    override fun insertSimulatedCard(pseudonymKey: String) {
        commands += "SET_CARD $pseudonymKey"
    }

    override fun cancel() {
        commands += "CANCEL"
    }

    override fun continueWorkflow() {
        commands += "CONTINUE"
    }

    override fun requestStatus() {
        commands += "GET_STATUS"
    }

    override fun onNfcTag(tag: Tag) {
        commands += "NFC_TAG"
    }

    private fun UserPin?.reveal(): String =
        this?.getAndClear()?.use { String(it.chars) } ?: "none"
}
