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
import eu.europa.ec.testlogic.extension.expectNoEvents
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import org.sprind.wallet.walletpinfeature.interactor.wscd.WscaRegistrationInteractor
import org.sprind.wallet.walletpinfeature.interactor.wscd.WscaRegistrationResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import org.sprind.wallet.corelogic.issuance.AuthorizationRedirectException
import org.sprind.wallet.networklogic.common.NetworkFailure
import kotlin.time.Instant

private const val LIVE_SPAN_TRACE_ID = "trace-live-span"
private const val LAST_PROPAGATED_TRACE_ID = "trace-last-propagated"

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
    fun `If wsca is not registered, onWalletPinSet registers and shows success step only once issued`() =
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

            coroutineRule.testScope.runCurrent()

            // issuance still running: the loader stays over the Confirm step
            assertEquals(WalletPinStep.Confirm, subject.viewState.value.currentStep)
            assertTrue(subject.viewState.value.isLoading)

            issuanceEvents.emit(IssuanceEvent.Completed)

            assertEquals(WalletPinStep.Success, subject.viewState.value.currentStep)
            assertFalse(subject.viewState.value.isLoading)

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

            coroutineRule.testScope.runCurrent()

            // Success step is not shown before the issuance completes
            assertEquals(WalletPinStep.Confirm, subject.viewState.value.currentStep)
            assertTrue(subject.viewState.value.isLoading)

            verify(rwscaPinHandler).startPinSession(UserPinImpl("123456"))
            // WD-2861: the PIN session must survive until issuance signing completes;
            // clearing it before IssuanceEvent.Completed would break RwscaSecureArea.sign.
            verify(rwscaPinHandler, never()).clearPinSession()

            // now VM is subscribed; emit issuance completed
            issuanceEvents.emit(IssuanceEvent.Completed)
            assertEquals(WalletPinStep.Success, subject.viewState.value.currentStep)

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

            coroutineRule.testScope.runCurrent()

            val state = subject.viewState.value
            assertFalse(state.isLoading)
            assertNotNull(state.errorDialog)
            // the error is shown over the Confirm step, never under the Success step
            assertEquals(WalletPinStep.Confirm, state.currentStep)

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
        coroutineRule.testScope.runCurrent()

        issuanceEvents.emit(IssuanceEvent.Failed("ISSUANCE_FAILED"))

        val state = subject.viewState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorDialog)
        assertEquals(R.string.global_error_title, state.errorDialog.titleRes)
        assertEquals(WalletPinStep.Confirm, state.currentStep)

        // WD-2861: session is cleared on the issuance terminal event.
        verify(rwscaPinHandler).clearPinSession()
    }

    @Test
    fun `When resumeWithRedirectUri fails, the dialog shows the live span's trace id and the span is ended`() =
        coroutineRule.runTest {
            stubTraceIdThatChangesOnceTheSpanEnds()
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)
            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
                Result.failure(IllegalStateException("bad redirect"))
            )

            goToConfirmWithPin("123456")
            subject.setEvent(Event.OnWalletPinSet)
            coroutineRule.testScope.runCurrent()

            assertEquals(LIVE_SPAN_TRACE_ID, subject.viewState.value.errorDialog?.traceId)
            verify(telemetry).endSpan(eq(TelemetryConstants.ISSUANCE), any())
        }

    @Test
    fun `When issuance Failed event, the dialog shows the live span's trace id and the span is ended`() =
        coroutineRule.runTest {
            stubTraceIdThatChangesOnceTheSpanEnds()
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)
            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
                Result.success(AuthorizationResponse("code", "state"))
            )

            goToConfirmWithPin("123456")
            subject.setEvent(Event.OnWalletPinSet)
            coroutineRule.testScope.runCurrent()

            issuanceEvents.emit(IssuanceEvent.Failed("ISSUANCE_FAILED"))

            assertEquals(LIVE_SPAN_TRACE_ID, subject.viewState.value.errorDialog?.traceId)
            verify(telemetry).endSpan(eq(TelemetryConstants.ISSUANCE), any())
        }

    @Test
    fun `When issuance Completed event, the Issuance span is left for the dashboard to end`() =
        coroutineRule.runTest {
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)
            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
                Result.success(AuthorizationResponse("code", "state"))
            )

            goToConfirmWithPin("123456")
            subject.setEvent(Event.OnWalletPinSet)
            coroutineRule.testScope.runCurrent()

            issuanceEvents.emit(IssuanceEvent.Completed)

            verify(telemetry, never()).endSpan(eq(TelemetryConstants.ISSUANCE), any())
        }

    @Test
    fun `When the authorization expired after the eID step, the dialog offers to start again or cancel`() =
        coroutineRule.runTest {
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)
            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
                Result.failure(
                    Exception("Error occurred", AuthorizationRedirectException("invalid_request", null))
                )
            )

            goToConfirmWithPin("123456")
            subject.setEvent(Event.OnWalletPinSet)
            coroutineRule.testScope.runCurrent()

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            assertEquals(R.string.pid_issuance_timeout_par_request_expired_title, dialog.titleRes)
            assertEquals(R.string.pid_issuance_timeout_par_request_expired_prim_button, dialog.primaryButtonTextRes)
            assertEquals(R.string.pid_issuance_timeout_par_request_expired_sec_button, dialog.secondaryButtonTextRes)

            subject.effect.runFlowTest {
                dialog.onSecondaryButtonClick()
                assertEquals(dashboardNavigation(), awaitItem())

                dialog.onPrimaryButtonClick()
                assertStartsAddDocumentAgain(awaitItem())
            }
        }

    @Test
    fun `When issuance times out after the redirect, the dialog's retry starts the PID issuance again`() =
        coroutineRule.runTest {
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)
            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
                Result.success(AuthorizationResponse("code", "state"))
            )

            goToConfirmWithPin("123456")
            subject.setEvent(Event.OnWalletPinSet)
            coroutineRule.testScope.runCurrent()

            issuanceEvents.emit(IssuanceEvent.Failed("ISSUANCE_FAILED", NetworkFailure.Timeout))

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            assertEquals(R.string.global_timeout_slow_internet_error_title, dialog.titleRes)
            assertEquals(R.string.pid_issuance_timeout_par_request_expired_sec_button, dialog.secondaryButtonTextRes)
            subject.effect.runFlowTest {
                dialog.onPrimaryButtonClick()
                assertStartsAddDocumentAgain(awaitItem())
            }
            verify(rwscaPinHandler).clearPinSession()
        }

    @Test
    fun `When issuance is rate limited after the redirect, the dialog says when to retry`() =
        coroutineRule.runTest {
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)
            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
                Result.success(AuthorizationResponse("code", "state"))
            )

            goToConfirmWithPin("123456")
            subject.setEvent(Event.OnWalletPinSet)
            coroutineRule.testScope.runCurrent()

            issuanceEvents.emit(IssuanceEvent.Failed("ISSUANCE_FAILED", NetworkFailure.RateLimited(retryAt = Instant.parse("2026-10-24T10:05:00Z"))))

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            assertEquals(R.string.global_timeout_too_many_requests_title, dialog.titleRes)
            assertEquals(null, dialog.secondaryButtonTextRes)
        }

    private fun dashboardNavigation() = Effect.Navigation.SwitchScreen(
        screenRoute = generateComposableNavigationLink(
            screen = DashboardScreens.Dashboard,
            arguments = "",
        ),
        inclusive = false,
    )

    private fun assertStartsAddDocumentAgain(effect: Effect) {
        val route = (effect as Effect.Navigation.SwitchScreen).screenRoute
        assertTrue(route, route.startsWith(IssuanceScreens.AddDocument.screenName))
    }

    /**
     * Mirrors the real [Telemetry]: the trace id is the live span's while it is active and falls
     * back to the last propagated one (a different value) once the span has ended.
     */
    private fun stubTraceIdThatChangesOnceTheSpanEnds() {
        var spanEnded = false
        whenever(telemetry.currentTraceId()).thenAnswer {
            if (spanEnded) LAST_PROPAGATED_TRACE_ID else LIVE_SPAN_TRACE_ID
        }
        doAnswer { spanEnded = true }.whenever(telemetry).endSpan(any(), any())
    }

    @Test
    fun `onDismissRequested is ignored while issuance is running`() = coroutineRule.runTest {
        whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
        whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)
        whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
        whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
            Result.success(AuthorizationResponse("code", "state"))
        )

        goToConfirmWithPin("123456")
        subject.setEvent(Event.OnWalletPinSet)
        coroutineRule.testScope.runCurrent()

        subject.setEvent(Event.OnDismissRequested)

        assertEquals(WalletPinStep.Confirm, subject.viewState.value.currentStep)
        assertTrue(subject.viewState.value.isLoading)
        // the else branch would leave the step alone too; ignoring back also means not navigating
        subject.effect.expectNoEvents()
    }

    @Test
    fun `When no issuance outcome is published, error dialog is shown after the timeout`() =
        coroutineRule.runTest {
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(wscaRegistrationInteractor.isAlreadyRegistered()).thenReturn(true)
            whenever(addDocumentInteractor.authorizationHandler).thenReturn(authorizationHandler)
            whenever(authorizationHandler.resumeWithRedirectUri(fakeRedirectUrl)).thenReturn(
                Result.success(AuthorizationResponse("code", "state"))
            )

            goToConfirmWithPin("123456")
            subject.setEvent(Event.OnWalletPinSet)
            coroutineRule.testScope.runCurrent()

            // e.g. a deferred issuance, for which the interactor publishes no IssuanceEvent
            coroutineRule.testScope.advanceTimeBy(59.seconds)
            coroutineRule.testScope.runCurrent()
            assertTrue(subject.viewState.value.isLoading)
            verify(rwscaPinHandler, never()).clearPinSession()
            verify(addDocumentInteractor, never()).cancelIssuance()

            coroutineRule.testScope.advanceTimeBy(1.seconds)
            coroutineRule.testScope.runCurrent()

            val state = subject.viewState.value
            assertFalse(state.isLoading)
            assertEquals("ISSUANCE_FAILED", state.errorDialog?.errorCode)
            assertEquals(WalletPinStep.Confirm, state.currentStep)
            // the still-running issuance is stopped, so it cannot store a document behind the error
            verify(addDocumentInteractor).cancelIssuance()
            verify(rwscaPinHandler).clearPinSession()

            // a late outcome no longer moves the screen to the Success step
            issuanceEvents.emit(IssuanceEvent.Completed)
            assertEquals(WalletPinStep.Confirm, subject.viewState.value.currentStep)
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
