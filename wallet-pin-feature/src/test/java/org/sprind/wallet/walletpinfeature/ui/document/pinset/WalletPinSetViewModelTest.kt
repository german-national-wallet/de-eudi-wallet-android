package org.sprind.wallet.walletpinfeature.ui.document.pinset

import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.commonfeature.interactor.IssuanceEvent
import eu.europa.ec.commonfeature.interactor.StartPinSessionResult
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import org.sprind.wallet.businesslogic.model.UserPinImpl
import eu.europa.ec.corelogic.handler.AusweisSdkAuthorizationHandler
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationResponse
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.walletpinfeature.interactor.wscd.WscaRegistrationInteractor
import org.sprind.wallet.walletpinfeature.interactor.wscd.WscaRegistrationResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertNotNull

class WalletPinSetViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var rwscaPinHandler: RwscaPinHandler

    @Mock
    private lateinit var wscaRegistrationInteractor: WscaRegistrationInteractor

    @Mock
    private lateinit var addDocumentInteractor: AddDocumentInteractor
    @Mock
    lateinit var authorizationHandler: AusweisSdkAuthorizationHandler

    @Mock
    private lateinit var telemetry: Telemetry


    private val subject: WalletPinSetViewModel by lazy {
        WalletPinSetViewModel(
            rwscaPinHandler = rwscaPinHandler,
            wscaRegistrationInteractor = wscaRegistrationInteractor,
            flowType = fakeFlowType,
            redirectUrl = fakeRedirectUrl,
            addDocumentInteractor = addDocumentInteractor,
            telemetry = telemetry
        )
    }
    private lateinit var closeable: AutoCloseable

    private val fakeFlowType = IssuanceFlowUiConfig.NO_DOCUMENT
    private val fakeRedirectUrl =
        URLEncoder.encode("http://redirect.com", StandardCharsets.UTF_8.toString())

    private lateinit var issuanceEvents: MutableSharedFlow<IssuanceEvent>

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        issuanceEvents = MutableSharedFlow(extraBufferCapacity = 16)
        whenever(addDocumentInteractor.issuanceEvents).thenReturn(issuanceEvents)
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `Initial step should be Info`() {
        val state = subject.viewState.value
        assertEquals(WalletPinStep.Info, state.currentStep)
    }

    @Test
    fun `onContinueAction advances from Info to Set`() = coroutineRule.runTest {
        subject.setEvent(Event.OnContinueAction)

        val state = subject.viewState.value
        assertEquals(WalletPinStep.Set, state.currentStep)
        assertEquals(0, state.pinState.buffer.length)
    }

    @Test
    fun `onContinueAction advances from Set to Confirm`() = coroutineRule.runTest {
        // go to Set
        subject.setEvent(Event.OnContinueAction)

        subject.typeIntoPinField("123456")
        subject.setEvent(Event.OnPinUpdate)
        subject.setEvent(Event.OnContinueAction)

        val state = subject.viewState.value
        assertEquals(WalletPinStep.Confirm, state.currentStep)
        assertEquals(0, state.pinState.buffer.length)
    }

    @Test
    fun `onDismissRequested goes from Confirm to Set`() = coroutineRule.runTest {
        // go to Set
        subject.setEvent(Event.OnContinueAction)
        subject.typeIntoPinField("123456")
        subject.setEvent(Event.OnPinUpdate)
        // go to Confirm
        subject.setEvent(Event.OnContinueAction)

        // Now go back
        subject.setEvent(Event.OnDismissRequested)

        val state = subject.viewState.value
        assertEquals(WalletPinStep.Set, state.currentStep)
    }

    @Test
    fun `onDismissRequested goes from Set to Info`() = coroutineRule.runTest {
        // go to Set
        subject.setEvent(Event.OnContinueAction)

        subject.setEvent(Event.OnDismissRequested)

        val state = subject.viewState.value
        assertEquals(WalletPinStep.Info, state.currentStep)
    }

    @Test
    fun `onCloseClicked navigates to Dashboard`() = coroutineRule.runTest {
        subject.setEvent(Event.OnCloseClicked)

        subject.effect.runFlowTest {
            assertEquals(
                Effect.Navigation.SwitchScreen(
                    screenRoute = generateComposableNavigationLink(
                        screen = DashboardScreens.Dashboard,
                        arguments = "",
                    ),
                    inclusive = false,
                ),
                awaitItem()
            )
        }
    }

    @Test
    fun `onPinUpdate with valid length sets isValid true`() = coroutineRule.runTest {
        subject.typeIntoPinField("123456")
        subject.setEvent(Event.OnPinUpdate)

        val state = subject.viewState.value
        assertTrue(state.pinState.isValid)
        assertEquals("123456".length, state.pinState.buffer.length)
    }

    @Test
    fun `onPinUpdate with invalid length sets isValid false`() = coroutineRule.runTest {
        subject.typeIntoPinField("12")
        subject.setEvent(Event.OnPinUpdate)

        val state = subject.viewState.value
        assertFalse(state.pinState.isValid)
        assertEquals("12".length, state.pinState.buffer.length)
    }

    @Test
    fun `onPinConfirmUpdate with matching pin provided in Set, sets isValid true`() =
        coroutineRule.runTest {
            // go to Set
            subject.setEvent(Event.OnContinueAction)
            subject.typeIntoPinField("123456")
            subject.setEvent(Event.OnPinUpdate)
            // go to Confirm
            subject.setEvent(Event.OnContinueAction)

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.OnPinConfirmUpdate)

            val state = subject.viewState.value
            assertTrue(state.pinState.isValid)
            assertEquals("123456".length, state.pinState.buffer.length)
        }

    @Test
    fun `onPinConfirmUpdate with non matching pin provided in Set, sets isValid false`() =
        coroutineRule.runTest {
            // go to Set
            subject.setEvent(Event.OnContinueAction)
            subject.typeIntoPinField("123456")
            subject.setEvent(Event.OnPinUpdate)
            // go to Confirm
            subject.setEvent(Event.OnContinueAction)

            subject.typeIntoPinField("123455")
            subject.setEvent(Event.OnPinConfirmUpdate)

            val state = subject.viewState.value
            assertFalse(state.pinState.isValid)
        }

    @Test
    fun `If wsca is not registered, onWalletPinSet registers and shows success screen step`() =
        coroutineRule.runTest {
            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(Result.success(
                AuthorizationResponse("authCode","state"))
            )
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(false)
            whenever(wscaRegistrationInteractor.register(UserPinImpl("123456")))
                .thenReturn(flowOf(WscaRegistrationResult.Success))

            // go to Set
            subject.setEvent(Event.OnContinueAction)
            subject.typeIntoPinField("123456")
            subject.setEvent(Event.OnPinUpdate)
            // go to Confirm
            subject.setEvent(Event.OnContinueAction)

            subject.setEvent(Event.OnWalletPinSet)

            coroutineRule.testScope.advanceUntilIdle()
            coroutineRule.testScope.runCurrent()

            val state = subject.viewState.value
            assertEquals(WalletPinStep.Success, state.currentStep)

            verify(wscaRegistrationInteractor).isAlreadyRegistered()
            verify(wscaRegistrationInteractor).register(UserPinImpl("123456"))
        }


    @Test
    fun `If wsca not registered, onWalletPinSet shows error on register failure with unknown error`() =
        coroutineRule.runTest {
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(false)
            whenever(wscaRegistrationInteractor.register(any()))
                .thenReturn(flowOf(WscaRegistrationResult.Failure("error", traceId = "traceId")))

            subject.setEvent(Event.OnWalletPinSet)

            val state = subject.viewState.value

            verify(wscaRegistrationInteractor).isAlreadyRegistered()
            verify(wscaRegistrationInteractor).register(any())

            assertEquals("error", state.errorDialog?.errorCode)
            assertEquals("traceId", state.errorDialog?.traceId)
            assertEquals(R.string.global_error_title, state.errorDialog?.titleRes)
            assertEquals(R.string.global_error_paragraph, state.errorDialog?.bodyTextRes)
            assertEquals(R.string.global_error_prim_button, state.errorDialog?.primaryButtonTextRes)
        }

    @Test
    fun `If already registered, onWalletPinSet handles pin, resumes redirect, and navigates to Dashboard on issuance completed`() =
        coroutineRule.runTest {

            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)

            // resumeWithRedirectUri success
            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
                Result.success(AuthorizationResponse("code", "state"))
            )

            goToConfirmWithPin("123456")

            subject.setEvent(Event.OnWalletPinSet)

            coroutineRule.testScope.advanceUntilIdle()
            coroutineRule.testScope.runCurrent()

            // Success screen shown immediately
            assertEquals(WalletPinStep.Success, subject.viewState.value.currentStep)

            verify(rwscaPinHandler).startPinSession(UserPinImpl("123456"))
            // WD-2861: the PIN session must survive until issuance signing completes;
            // clearing it before IssuanceEvent.Completed would break RwscaSecureArea.sign.
            verify(rwscaPinHandler, never()).clearPinSession()

            // now VM is subscribed; emit issuance completed
            issuanceEvents.emit(IssuanceEvent.Completed)

            subject.effect.runFlowTest {
                assertEquals(
                    Effect.Navigation.SwitchScreen(
                        screenRoute = generateComposableNavigationLink(
                            screen = DashboardScreens.Dashboard,
                            arguments = "",
                        ),
                        inclusive = false,
                    ),
                    awaitItem()
                )
            }

            // WD-2861: session is cleared on the issuance terminal event, not before.
            verify(rwscaPinHandler).clearPinSession()
        }

    @Test
    fun `If issuance completes while redirect resumes, onWalletPinSet still navigates to Dashboard`() =
        coroutineRule.runTest {
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)
            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            doAnswer {
                issuanceEvents.tryEmit(IssuanceEvent.Completed)
                Result.success(AuthorizationResponse("code", "state"))
            }.whenever(authorizationHandler).resumeWithRedirectUri(fakeRedirectUrl)

            goToConfirmWithPin("123456")
            subject.setEvent(Event.OnWalletPinSet)

            coroutineRule.testScope.advanceUntilIdle()
            coroutineRule.testScope.runCurrent()

            subject.effect.runFlowTest {
                assertEquals(
                    Effect.Navigation.SwitchScreen(
                        screenRoute = generateComposableNavigationLink(
                            screen = DashboardScreens.Dashboard,
                            arguments = "",
                        ),
                        inclusive = false,
                    ),
                    awaitItem()
                )
            }
            verify(rwscaPinHandler).clearPinSession()
        }

    @Test
    fun `If not registered, onWalletPinSet registers, resumes redirect, and navigates to Dashboard on issuance completed`() =
        coroutineRule.runTest {

            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(false)
            whenever(wscaRegistrationInteractor.register(UserPinImpl("123456")))
                .thenReturn(flowOf(WscaRegistrationResult.Success))

            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
                Result.success(AuthorizationResponse("code", "state"))
            )

            goToConfirmWithPin("123456")

            subject.setEvent(Event.OnWalletPinSet)

            coroutineRule.testScope.advanceUntilIdle()
            coroutineRule.testScope.runCurrent()

            verify(wscaRegistrationInteractor).register(UserPinImpl("123456"))

            issuanceEvents.emit(IssuanceEvent.Completed)

            subject.effect.runFlowTest {
                val effect = awaitItem() as Effect.Navigation.SwitchScreen
                // validate it's dashboard route (or exact string if stable)
                assertTrue(effect.screenRoute.contains(DashboardScreens.Dashboard.screenRoute))
            }

            // WD-2861: session is cleared on the issuance terminal event.
            verify(rwscaPinHandler).clearPinSession()
        }
    @Test
    fun `When resumeWithRedirectUri fails, error dialog is shown and no issuance subscription is required`() =
        coroutineRule.runTest {

            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)

            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
                Result.failure(IllegalStateException("bad redirect"))
            )

            goToConfirmWithPin("123456")
            subject.setEvent(Event.OnWalletPinSet)

            coroutineRule.testScope.advanceUntilIdle()
            coroutineRule.testScope.runCurrent()

            val state = subject.viewState.value
            assertFalse(state.isLoading)
            assertNotNull(state.errorDialog)

            // WD-2861: session is cleared when authorization resume fails (issuance never starts).
            verify(rwscaPinHandler).clearPinSession()

            // If you emit issuance completed, it SHOULD NOT navigate because it never subscribed
            issuanceEvents.emit(IssuanceEvent.Completed)
            // no effect expected
        }
    @Test
    fun `When issuance Failed event, error dialog is shown`() = coroutineRule.runTest {
        whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
        whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)

        whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
        whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
            Result.success(AuthorizationResponse("code", "state"))
        )

        goToConfirmWithPin("123456")
        subject.setEvent(Event.OnWalletPinSet)
        // we advance time because of the forced delay
        coroutineRule.testScope.advanceUntilIdle()
        coroutineRule.testScope.runCurrent()

        issuanceEvents.emit(IssuanceEvent.Failed("ISSUANCE_FAILED"))

        val state = subject.viewState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorDialog)
        assertEquals(R.string.global_error_title, state.errorDialog.titleRes)

        // WD-2861: session is cleared on the issuance terminal event.
        verify(rwscaPinHandler).clearPinSession()
    }

    private fun goToConfirmWithPin(pin: String = "123456") {
        subject.setEvent(Event.OnContinueAction)   // Info -> Set
        subject.typeIntoPinField(pin)
        subject.setEvent(Event.OnPinUpdate)
        subject.setEvent(Event.OnContinueAction)   // Set -> Confirm (keeps the first entry)
        subject.typeIntoPinField(pin)
        subject.setEvent(Event.OnPinConfirmUpdate)
    }
}

/**
 * Types [code] into the screen's code buffer, standing in for the field the user would use. The
 * digits never travel as an event payload, so a test has to put them where the field would.
 */
private fun WalletPinSetViewModel.typeIntoPinField(code: String) {
    viewState.value.pinState.buffer.type(code)
}
