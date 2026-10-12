package org.sprind.wallet.presentationfeature.ui.request

import android.net.Uri
import eu.europa.ec.authenticationlogic.jwt.InstantProvider
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import eu.europa.ec.commonfeature.interactor.StartPinSessionResult
import eu.europa.ec.commonfeature.model.DocumentType
import eu.europa.ec.commonfeature.ui.request.Effect
import eu.europa.ec.commonfeature.ui.request.Event
import eu.europa.ec.commonfeature.ui.request.RequestScreenStep
import eu.europa.ec.commonfeature.ui.request.model.CollapsedUiItem
import eu.europa.ec.commonfeature.ui.request.model.DocumentPayloadDomain
import eu.europa.ec.commonfeature.ui.request.model.ExpandedUiItem
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentClaim
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.corelogic.di.AppCoroutineScope
import eu.europa.ec.presentationfeature.interactor.PresentationDocumentSubmissionPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRejectionPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestDeleteDocumentPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractorPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestProcessPartialState
import eu.europa.ec.presentationfeature.interactor.RequestRejectionReason
import eu.europa.ec.presentationfeature.ui.request.PresentationRequestViewModel
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.awaitFirstOf
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.serializer.UiSerializer
import eu.europa.ec.uilogic.serializer.UiSerializerImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.JsonArray
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.multipaz.request.MdocRequestedClaim
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import org.sprind.wallet.businesslogic.model.UserPinImpl
import org.sprind.wallet.corelogic.presentationerror.PresentationError
import org.sprind.wallet.presentationfeature.error.BlockingErrorReason
import org.sprind.wallet.presentationfeature.error.presentationErrorRoute
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class PresentationRequestViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()
    private lateinit var closeable: AutoCloseable

    @Mock
    private lateinit var presentationRequestInteractor: PresentationRequestInteractor
    @Mock
    private lateinit var resourceProvider: ResourceProvider
    @Mock
    private lateinit var uiSerializer: UiSerializer
    @Mock
    private lateinit var rwscaPinHandler: RwscaPinHandler
    @Mock
    private lateinit var instantProvider: InstantProvider
    @Mock
    private lateinit var telemetry: Telemetry
    @Mock
    private lateinit var configLogic: ConfigLogic

    val subject: PresentationRequestViewModel by lazy {
        PresentationRequestViewModel(
            presentationRequestInteractor = presentationRequestInteractor,
            resourceProvider = resourceProvider,
            uiSerializer = uiSerializer,
            rwscaPinHandler = rwscaPinHandler,
            instantProvider = instantProvider,
            appCoroutineScope = AppCoroutineScope(coroutineRule.testScope),
            requestUriConfigRaw = "",
            telemetry = telemetry,
            configLogic = configLogic,
        )
    }

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.getString(any())).thenReturn("")
        whenever(presentationRequestInteractor.getWalletPinBlockTime()).thenReturn("")
        whenever(telemetry.currentTraceId()).thenReturn("")
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `when request contains PID, credential preview continue opens wallet PIN step`() =
        coroutineRule.runTest {
            subject.updateData(
                listOf(
                    requestDocumentItemUi(documentType = DocumentType.PID)
                )
            )

            subject.setEvent(Event.CredentialDetailsView)
            subject.viewState.value.onContinueAction()

            assertEquals(RequestScreenStep.EnterPin, subject.viewState.value.currentStep)
        }

    @Test
    fun `when PID deletion confirmation is confirmed, then PID documents are deleted`() =
        coroutineRule.runTest {
            whenever(presentationRequestInteractor.deletePidDocuments()).thenReturn(
                flowOf(PresentationRequestDeleteDocumentPartialState.Success)
            )

            subject.effect.runFlowTest {
                subject.setEvent(Event.PidDeletionRequested)

                assertEquals(true, subject.viewState.value.shouldDisplayPidDeletionConfirmDialog)

                subject.setEvent(Event.DeletePidDocumentsRequested)
                coroutineRule.testScope.advanceUntilIdle()

                verify(presentationRequestInteractor).deletePidDocuments()
                assertEquals(
                    RequestScreenStep.DocumentDeletedConfirmation,
                    subject.viewState.value.currentStep
                )
                assertEquals(false, subject.viewState.value.shouldDisplayPidDeletionConfirmDialog)

                val effect = assertIs<Effect.Navigation.SwitchScreen>(awaitItem())
                assertEquals(DashboardScreens.Dashboard.screenRoute, effect.screenRoute)
                verify(presentationRequestInteractor).stopPresentation()
            }
        }

    @Test
    fun `when cancel confirmation is confirmed, then presentation is stopped and dashboard is opened`() =
        coroutineRule.runTest {
            whenever(presentationRequestInteractor.rejectPresentation())
                .thenReturn(PresentationRejectionPartialState.Delivered)

            subject.effect.runFlowTest {
                subject.setEvent(Event.CancelButtonPressed)

                assertEquals(true, subject.viewState.value.shouldDisplayConfirmCancelDialog)

                subject.setEvent(Event.ConfirmClickOnCancelConfirmationDialog)
                coroutineRule.testScope.advanceUntilIdle()

                verify(presentationRequestInteractor).stopPresentation()
                assertEquals(false, subject.viewState.value.shouldDisplayConfirmCancelDialog)

                val effect = assertIs<Effect.Navigation.SwitchScreen>(awaitItem())
                assertEquals(DashboardScreens.Dashboard.screenRoute, effect.screenRoute)
            }
        }

    @Test
    fun `declining tells the relying party before the presentation is torn down`() =
        // The refusal is dispatched over the session stopPresentation() closes, so the reverse
        // order sends nothing and the verifier is left unable to tell a denial from an abandoned
        // flow - which is the whole point of answering at all (WD-4265).
        coroutineRule.runTest {
            whenever(presentationRequestInteractor.rejectPresentation())
                .thenReturn(PresentationRejectionPartialState.Delivered)

            subject.setEvent(Event.ConfirmClickOnCancelConfirmationDialog)
            coroutineRule.testScope.advanceUntilIdle()

            inOrder(presentationRequestInteractor) {
                verify(presentationRequestInteractor).rejectPresentation()
                verify(presentationRequestInteractor).stopPresentation()
            }
        }

    @Test
    fun `a redirect the relying party answers with is followed, then the flow is left`() =
        // Leaving first cancels the collector that carries the deep link, so the link would be
        // dropped and the user would simply land on the dashboard.
        coroutineRule.runTest {
            whenever(presentationRequestInteractor.rejectPresentation()).thenReturn(
                PresentationRejectionPartialState.Redirect(
                    uri = URI.create("https://verifier.example.com/denied")
                )
            )

            subject.effect.runFlowTest {
                subject.setEvent(Event.ConfirmClickOnCancelConfirmationDialog)
                coroutineRule.testScope.advanceUntilIdle()

                val deepLink = assertIs<Effect.Navigation.DeepLink>(awaitItem())
                assertEquals("https://verifier.example.com/denied", deepLink.link.toString())

                val exit = assertIs<Effect.Navigation.SwitchScreen>(awaitItem())
                assertEquals(DashboardScreens.Dashboard.screenRoute, exit.screenRoute)
                verify(presentationRequestInteractor).stopPresentation()
            }
        }

    @Test
    fun `a refusal the relying party never receives still lets the user out`() =
        // The user declined and nothing was disclosed; an unreachable verifier is the verifier's
        // problem, not something to strand the user on. Nothing is opened either - a failure
        // carries no link.
        coroutineRule.runTest {
            whenever(presentationRequestInteractor.rejectPresentation())
                .thenReturn(PresentationRejectionPartialState.Failure("response_uri unreachable"))

            subject.effect.runFlowTest {
                subject.setEvent(Event.ConfirmClickOnCancelConfirmationDialog)
                coroutineRule.testScope.advanceUntilIdle()

                val exit = assertIs<Effect.Navigation.SwitchScreen>(awaitItem())
                assertEquals(DashboardScreens.Dashboard.screenRoute, exit.screenRoute)
                verify(presentationRequestInteractor).stopPresentation()
                assertFalse(subject.viewState.value.isLoading)
            }
        }

    @Test
    fun `a refusal the verifier turns down does not put the screen into its error state`() =
        // Tearing a session down makes it talk: a verifier that refuses the `access_denied` post
        // emits an error on the very flow this screen subscribes to for the request. Left
        // subscribed, the screen would show "something went wrong" for the moment before it
        // navigates - which says nothing true, since the user declined and declining worked.
        coroutineRule.runTest {
            val typedFailureError = PresentationError.Unclassified(
                traceId = "test-trace-id",
                cause = null,
                displayMessage = "response_uri said 400",
            )
            val sessionEvents = MutableSharedFlow<PresentationRequestInteractorPartialState>()
            whenever(presentationRequestInteractor.getRequestDocuments()).thenReturn(sessionEvents)
            whenever(
                uiSerializer.fromBase64(
                    anyOrNull(),
                    eq(RequestUriConfig::class.java),
                    any(),
                )
            ).thenReturn(RequestUriConfig(PresentationMode.Ble(initiatorRoute = "")))
            whenever(presentationRequestInteractor.rejectPresentation())
                .thenReturn(PresentationRejectionPartialState.Failure("response_uri said 400"))

            subject.setEvent(Event.DoWork)
            coroutineRule.testScope.advanceUntilIdle()

            subject.setEvent(Event.ConfirmClickOnCancelConfirmationDialog)
            coroutineRule.testScope.advanceUntilIdle()

            // What wallet-core emits as the refused dispatch unwinds.
            sessionEvents.emit(
                PresentationRequestInteractorPartialState.Failure(error = typedFailureError)
            )
            coroutineRule.testScope.advanceUntilIdle()

            assertNull(subject.viewState.value.error)
        }

    @Test
    fun `declining closes the presentation telemetry span`() =
        // A declined request reaches neither the success nor the failure path that ends the span,
        // so without this the span is left open for the rest of the process.
        coroutineRule.runTest {
            whenever(presentationRequestInteractor.rejectPresentation())
                .thenReturn(PresentationRejectionPartialState.Delivered)

            subject.setEvent(Event.ConfirmClickOnCancelConfirmationDialog)
            coroutineRule.testScope.advanceUntilIdle()

            verify(telemetry).endSpan(TelemetryConstants.PRESENTATION)
        }

    @Test
    fun `a request nothing in the wallet can satisfy is refused before the no-document screen opens`() =
        // Leaving the flow stops the presentation, which cancels a refusal still in flight, so the
        // screen may only open once the verifier has its answer (WD-4430).
        coroutineRule.runTest {
            val verifierAnswer = CompletableDeferred<PresentationRejectionPartialState>()
            whenever(presentationRequestInteractor.rejectPresentation())
                .doSuspendableAnswer { verifierAnswer.await() }
            val sessionEvents = startRequest()

            subject.effect.runFlowTest {
                sessionEvents.emit(noData())
                coroutineRule.testScope.advanceUntilIdle()

                verify(presentationRequestInteractor).rejectPresentation()
                expectNoEvents()

                verifierAnswer.complete(PresentationRejectionPartialState.Delivered)
                coroutineRule.testScope.advanceUntilIdle()

                val effect = assertIs<Effect.Navigation.SwitchScreen>(awaitItem())
                assertEquals(
                    PresentationScreens.PresentationNoDocument.screenRoute,
                    effect.screenRoute
                )
            }
        }

    @Test
    fun `a refusal of an unmatched request that fails still shows the no-document screen`() =
        coroutineRule.runTest {
            whenever(presentationRequestInteractor.rejectPresentation())
                .thenReturn(PresentationRejectionPartialState.Failure("response_uri unreachable"))
            val sessionEvents = startRequest()

            subject.effect.runFlowTest {
                sessionEvents.emit(noData())
                coroutineRule.testScope.advanceUntilIdle()

                val effect = assertIs<Effect.Navigation.SwitchScreen>(awaitItem())
                assertEquals(
                    PresentationScreens.PresentationNoDocument.screenRoute,
                    effect.screenRoute
                )
                assertNull(subject.viewState.value.error)
            }
        }

    @Test
    fun `a request the held credential cannot satisfy is refused, then explained in a dialog`() =
        // Same outcome as the iOS wallet's "malformed presentation" popup, plus the refusal the
        // verifier needs to close its session (WD-4430).
        coroutineRule.runTest {
            whenever(presentationRequestInteractor.rejectPresentation())
                .thenReturn(PresentationRejectionPartialState.Delivered)
            whenever(telemetry.currentTraceId()).thenReturn("trace-4430")
            val sessionEvents = startRequest()

            subject.effect.runFlowTest {
                sessionEvents.emit(
                    PresentationRequestInteractorPartialState.Unsatisfiable(
                        verifierName = "Verifier",
                        verifierIsTrusted = true,
                    )
                )
                coroutineRule.testScope.advanceUntilIdle()

                verify(presentationRequestInteractor).rejectPresentation()
                val dialog = requireNotNull(subject.viewState.value.errorDialog)
                assertEquals(R.string.pid_presentation_malformed_presentation_title, dialog.titleRes)
                assertEquals(
                    R.string.pid_presentation_malformed_presentation_paragraph,
                    dialog.bodyTextRes
                )
                assertEquals(
                    R.string.pid_presentation_malformed_presentation_prim_button,
                    dialog.primaryButtonTextRes
                )
                assertEquals("trace-4430", dialog.traceId)

                dialog.onPrimaryButtonClick()
                assertIs<Effect.Navigation.Pop>(awaitItem())
            }
        }

    @Test
    fun `a redirect answering the refusal is followed once the dialog is closed`() =
        coroutineRule.runTest {
            whenever(presentationRequestInteractor.rejectPresentation()).thenReturn(
                PresentationRejectionPartialState.Redirect(
                    uri = URI.create("https://verifier.example.com/denied")
                )
            )
            val sessionEvents = startRequest()

            subject.effect.runFlowTest {
                sessionEvents.emit(
                    PresentationRequestInteractorPartialState.Unsatisfiable(
                        verifierName = "Verifier",
                        verifierIsTrusted = true,
                    )
                )
                coroutineRule.testScope.advanceUntilIdle()
                expectNoEvents()

                requireNotNull(subject.viewState.value.errorDialog).onDismiss()

                val deepLink = assertIs<Effect.Navigation.DeepLink>(awaitItem())
                assertEquals("https://verifier.example.com/denied", deepLink.link.toString())
                assertIs<Effect.Navigation.Pop>(awaitItem())
            }
        }

    @Test
    fun `refusing an unmatched request closes the presentation telemetry span`() =
        coroutineRule.runTest {
            whenever(presentationRequestInteractor.rejectPresentation())
                .thenReturn(PresentationRejectionPartialState.Delivered)
            val sessionEvents = startRequest()

            sessionEvents.emit(noData())
            coroutineRule.testScope.advanceUntilIdle()

            verify(telemetry).endSpan(TelemetryConstants.PRESENTATION)
        }

    @Test
    fun `a request the wallet refuses itself is answered with access_denied before the teardown`() =
        // stopPresentation() cancels the access_denied dispatch, so it must come second.
        coroutineRule.runTest {
            givenRequest(rejected(RequestRejectionReason.OVER_ASKING))
            whenever(presentationRequestInteractor.rejectPresentation())
                .thenReturn(PresentationRejectionPartialState.Delivered)

            subject.setEvent(Event.DoWork)
            coroutineRule.testScope.advanceUntilIdle()

            inOrder(presentationRequestInteractor) {
                verify(presentationRequestInteractor).rejectPresentation()
                verify(presentationRequestInteractor).stopPresentation()
            }
            verify(telemetry).endSpan(TelemetryConstants.PRESENTATION)
        }

    @Test
    fun `a refused request hands the verifier's redirect to the rejection screen`() =
        coroutineRule.runTest {
            givenRequest(rejected(RequestRejectionReason.NOT_REGISTERED))
            whenever(presentationRequestInteractor.rejectPresentation()).thenReturn(
                PresentationRejectionPartialState.Redirect(
                    uri = URI.create("https://verifier.example.com/denied?state=1")
                )
            )

            subject.effect.runFlowTest {
                subject.setEvent(Event.DoWork)
                coroutineRule.testScope.advanceUntilIdle()

                val route = assertIs<Effect.Navigation.SwitchScreen>(awaitItem()).screenRoute
                assertTrue(route.startsWith("PRESENTATION_REQUEST_REJECTED"), route)
                assertTrue("rejectionReason=NOT_REGISTERED" in route, route)
                assertTrue(
                    "redirectUri=${Uri.encode("https://verifier.example.com/denied?state=1")}" in route,
                    route,
                )
            }
        }

    @Test
    fun `a request the verifier was already answered for is not answered again`() =
        coroutineRule.runTest {
            givenRequest(
                rejected(RequestRejectionReason.NOT_REGISTERED).copy(verifierAnswered = true)
            )

            subject.effect.runFlowTest {
                subject.setEvent(Event.DoWork)
                coroutineRule.testScope.advanceUntilIdle()

                val route = assertIs<Effect.Navigation.SwitchScreen>(awaitItem()).screenRoute
                assertTrue("rejectionReason=NOT_REGISTERED" in route, route)
                assertTrue(route.endsWith("redirectUri="), route)
            }
            verify(presentationRequestInteractor, never()).rejectPresentation()
            verify(presentationRequestInteractor).stopPresentation()
        }

    @Test
    fun `a request within the registered scope shows the registered purpose and report target`() =
        coroutineRule.runTest {
            givenRequest(
                PresentationRequestInteractorPartialState.Success(
                    verifierName = "Verifier",
                    verifierIsTrusted = true,
                    requestDocuments = listOf(requestDocumentItemUi(DocumentType.EAA)),
                    purpose = "Age verification",
                    reportUri = "https://authority.example.invalid/complaint",
                )
            )

            subject.setEvent(Event.DoWork)
            coroutineRule.testScope.advanceUntilIdle()

            assertEquals("Age verification", subject.viewState.value.purpose)
            assertEquals(
                "https://authority.example.invalid/complaint",
                subject.viewState.value.reportUrl,
            )
        }

    @Test
    fun `when request hands off to loading, cleanup keeps presentation alive`() =
        coroutineRule.runTest {
            subject.updateData(
                listOf(
                    requestDocumentItemUi(documentType = DocumentType.EAA)
                )
            )

            subject.setEvent(Event.CredentialDetailsView)
            subject.viewState.value.onContinueAction()
            subject.cleanUp()

            verify(presentationRequestInteractor, never()).stopPresentation()
            verify(rwscaPinHandler, never()).clearPinSession()
        }

    @Test
    fun `when the presentation completes, the credential batch refresh is triggered`() =
        // PID presentations complete on this screen (RWSCA/PIN, no device auth) and never reach the
        // loading screen, so this is where the post-presentation batch refresh (spec step 043) must
        // be triggered. See the redirect test below for the ordering that path has to keep.
        coroutineRule.runTest {
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.processRequest()).thenReturn(
                flowOf(PresentationRequestProcessPartialState.Success)
            )

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.ConfirmPin)

            verify(presentationRequestInteractor).reissueLowBatchDocumentsIfNeeded()
        }

    @Test
    fun `when the verifier asks for a redirect, the batch refresh finishes before the teardown`() =
        // Either ordering reversed breaks the refresh silently while the presentation still looks
        // fine: redirecting first loses network to the background restriction, tearing down first
        // clears the PIN session the refresh signs with.
        coroutineRule.runTest {
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.initiatorRoute).thenReturn("")
            whenever(presentationRequestInteractor.processRequest()).thenReturn(
                flowOf(
                    PresentationRequestProcessPartialState.Redirect(
                        uri = URI.create("https://verifier.example.com/done")
                    )
                )
            )

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.ConfirmPin)

            inOrder(presentationRequestInteractor) {
                verify(presentationRequestInteractor).reissueLowBatchDocumentsIfNeeded()
                verify(presentationRequestInteractor).stopPresentation()
            }
        }

    @Test
    fun `when requested documents are sent and interactor returns error, then error dialog with correct title, bodytext and primary button text should be set in the state`() =
        coroutineRule.runTest {

            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.processRequest()).thenReturn(
                flowOf(PresentationRequestProcessPartialState.RequestReadyToBeSent)
            )

            whenever(presentationRequestInteractor.sendRequestedDocuments()).thenReturn(
                PresentationDocumentSubmissionPartialState.Failure.ServerError(PresentationDocumentSubmissionPartialState.Failure.ServerErrorCode.UNKNOWN, "traceId")
            )

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.ConfirmPin)

            verify(rwscaPinHandler).startPinSession(UserPinImpl("123456"))
            verify(presentationRequestInteractor).processRequest()

            assertEquals("UNKNOWN", subject.viewState.value.errorDialog?.errorCode)
            assertEquals("traceId", subject.viewState.value.errorDialog?.traceId)
            assertFalse(subject.viewState.value.isLoading)
            assertEquals(R.string.global_error_title, subject.viewState.value.errorDialog?.titleRes)
            assertEquals(R.string.global_error_paragraph, subject.viewState.value.errorDialog?.bodyTextRes)
            assertEquals(R.string.global_error_prim_button, subject.viewState.value.errorDialog?.primaryButtonTextRes)
        }

    @Test
    fun `when requested documents are sent and interactor returns RWSCD_ACCOUNT_UNKNOWN error, then error dialog with correct title, bodytext and primary button text should be set in the state`() =
        coroutineRule.runTest {

            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.processRequest()).thenReturn(
                flowOf(PresentationRequestProcessPartialState.RequestReadyToBeSent)
            )

            whenever(presentationRequestInteractor.sendRequestedDocuments()).thenReturn(
                PresentationDocumentSubmissionPartialState.Failure.ServerError(PresentationDocumentSubmissionPartialState.Failure.ServerErrorCode.RWSCD_ACCOUNT_UNKNOWN, "traceId")
            )

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.ConfirmPin)

            verify(rwscaPinHandler).startPinSession(UserPinImpl("123456"))
            verify(presentationRequestInteractor).processRequest()

            assertEquals("RWSCD_ACCOUNT_UNKNOWN", subject.viewState.value.errorDialog?.errorCode)
            assertEquals("traceId", subject.viewState.value.errorDialog?.traceId)
            assertFalse(subject.viewState.value.isLoading)
            assertEquals(R.string.pid_presentation_rwscd_account_unknown_title, subject.viewState.value.errorDialog?.titleRes)
            assertEquals(R.string.pid_presentation_rwscd_account_unknown_paragraph, subject.viewState.value.errorDialog?.bodyTextRes)
            assertEquals(R.string.pid_presentation_rwscd_account_unknown_prim_button, subject.viewState.value.errorDialog?.primaryButtonTextRes)
        }

    @Test
    fun `when requested documents are sent and interactor returns RWSCD_AUTH_VERIFICATION_FAILED error, then error dialog with correct title, bodytext and primary button text should be set in the state`() =
        coroutineRule.runTest {

            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.processRequest()).thenReturn(
                flowOf(PresentationRequestProcessPartialState.RequestReadyToBeSent)
            )

            whenever(presentationRequestInteractor.sendRequestedDocuments()).thenReturn(
                PresentationDocumentSubmissionPartialState.Failure.ServerError(PresentationDocumentSubmissionPartialState.Failure.ServerErrorCode.RWSCD_AUTH_VERIFICATION_FAILED, "traceId")
            )

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.ConfirmPin)

            verify(rwscaPinHandler).startPinSession(UserPinImpl("123456"))
            verify(presentationRequestInteractor).processRequest()

            assertEquals("RWSCD_AUTH_VERIFICATION_FAILED", subject.viewState.value.errorDialog?.errorCode)
            assertEquals("traceId", subject.viewState.value.errorDialog?.traceId)
            assertFalse(subject.viewState.value.isLoading)
            assertEquals(R.string.pid_presentation_rwscd_auth_verification_failed_title, subject.viewState.value.errorDialog?.titleRes)
            assertEquals(R.string.pid_presentation_rwscd_auth_verification_failed_paragraph, subject.viewState.value.errorDialog?.bodyTextRes)
            assertEquals(R.string.pid_presentation_rwscd_auth_verification_failed_prim_button, subject.viewState.value.errorDialog?.primaryButtonTextRes)
        }

    @Test
    fun `when sendRequestedDocuments returns Unknown with PresentationError, then error dialog uses the typed error code and traceId`() =
        coroutineRule.runTest {
            val typedError = PresentationError.RelyingPartySide(
                traceId = "typed-trace-id",
                cause = null,
                displayMessage = "invalid request",
                subType = PresentationError.RelyingPartySideSubType.INVALID_REQUEST,
            )

            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.processRequest()).thenReturn(
                flowOf(PresentationRequestProcessPartialState.RequestReadyToBeSent)
            )
            whenever(presentationRequestInteractor.sendRequestedDocuments()).thenReturn(
                PresentationDocumentSubmissionPartialState.Failure.Unknown(error = typedError)
            )

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.ConfirmPin)

            assertEquals("RELYING_PARTY_INVALID_REQUEST", subject.viewState.value.errorDialog?.errorCode)
            assertEquals("typed-trace-id", subject.viewState.value.errorDialog?.traceId)
            assertFalse(subject.viewState.value.isLoading)
        }

    private fun startRequest(): MutableSharedFlow<PresentationRequestInteractorPartialState> {
        val sessionEvents = MutableSharedFlow<PresentationRequestInteractorPartialState>()
        givenRequestDocuments(sessionEvents)
        subject.setEvent(Event.DoWork)
        coroutineRule.testScope.advanceUntilIdle()
        return sessionEvents
    }

    private fun givenRequest(state: PresentationRequestInteractorPartialState) =
        givenRequestDocuments(flowOf(state))

    private fun givenRequestDocuments(documents: Flow<PresentationRequestInteractorPartialState>) {
        whenever(presentationRequestInteractor.getRequestDocuments()).thenReturn(documents)
        whenever(
            uiSerializer.fromBase64(
                anyOrNull(),
                eq(RequestUriConfig::class.java),
                any(),
            )
        ).thenReturn(RequestUriConfig(PresentationMode.Ble(initiatorRoute = "")))
    }

    private fun noData() = PresentationRequestInteractorPartialState.NoData(
        verifierName = "Verifier",
        verifierIsTrusted = true,
    )

    private fun rejected(reason: RequestRejectionReason) =
        PresentationRequestInteractorPartialState.Rejected(verifierName = "Verifier", reason = reason)

    @Test
    fun `when a blocking error arrives, no dialog is set and navigation to the error screen is emitted`() =
        coroutineRule.runTest {
            // Trust failure resolves to the BLOCKING layout; it must not render as a
            // dialog on the request screen but route to the dedicated error screen.
            val typedError = PresentationError.RelyingPartySide(
                traceId = "blocking-trace-id",
                cause = null,
                displayMessage = "unknown relying party",
                subType = PresentationError.RelyingPartySideSubType.TRUST_FAILURE,
            )

            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.processRequest()).thenReturn(
                flowOf(PresentationRequestProcessPartialState.RequestReadyToBeSent)
            )
            whenever(presentationRequestInteractor.sendRequestedDocuments()).thenReturn(
                PresentationDocumentSubmissionPartialState.Failure.Unknown(error = typedError)
            )

            subject.effect.runFlowTest {
                subject.typeIntoPinField("123456")
                subject.setEvent(Event.ConfirmPin)
                coroutineRule.testScope.advanceUntilIdle()

                // BLOCKING renders full-screen: no dialog config, no isLoading reset.
                assertEquals(null, subject.viewState.value.errorDialog)

                // ConfirmPin first emits HideKeyboard; skip to the navigation effect.
                skipItems(1)
                val effect = assertIs<Effect.Navigation.SwitchScreen>(awaitItem())
                // Only the blocking reason and the trace ID cross the navigation boundary.
                assertEquals(
                    presentationErrorRoute(BlockingErrorReason.TRUST_FAILURE, "blocking-trace-id"),
                    effect.screenRoute,
                )
            }
        }

    @Test
    fun `when an overasking error occurs, the route carries only the reason and the trace id`() =
        coroutineRule.runTest {
            val typedError = PresentationError.RelyingPartySide(
                traceId = "blocking-trace-id",
                cause = null,
                displayMessage = "too much data requested",
                subType = PresentationError.RelyingPartySideSubType.UNKNOWN_SCOPE,
            )

            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.processRequest()).thenReturn(
                flowOf(PresentationRequestProcessPartialState.RequestReadyToBeSent)
            )
            whenever(presentationRequestInteractor.sendRequestedDocuments()).thenReturn(
                PresentationDocumentSubmissionPartialState.Failure.Unknown(error = typedError)
            )

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.ConfirmPin)
            coroutineRule.testScope.advanceUntilIdle()

            val effect = subject.effect.awaitFirstOf<Effect.Navigation.SwitchScreen>()
            val paramKey = PresentationScreens.PresentationError.ParamKey
            assertTrue(
                effect.screenRoute.startsWith(
                    PresentationScreens.PresentationError.screenRoute.substringBefore("?")
                ),
                effect.screenRoute,
            )
            assertTrue("${paramKey.REASON}=${BlockingErrorReason.OVERASKING.name}" in effect.screenRoute)
            assertTrue("${paramKey.TRACE_ID}=blocking-trace-id" in effect.screenRoute)
        }

    @Test
    fun `when a relying-party-side dialog error occurs, the dialog offers no retry and dismisses on the primary button`() =
        coroutineRule.runTest {
            // RELYING_PARTY_REJECTED maps to a DISMISS primary action: the dialog must
            // expose no retry and its primary click must clear the dialog.
            val typedError = PresentationError.RelyingPartySide(
                traceId = "rejected-trace-id",
                cause = null,
                displayMessage = "rejected",
                subType = PresentationError.RelyingPartySideSubType.RELYING_PARTY_REJECTED,
            )

            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.processRequest()).thenReturn(
                flowOf(PresentationRequestProcessPartialState.RequestReadyToBeSent)
            )
            whenever(presentationRequestInteractor.sendRequestedDocuments()).thenReturn(
                PresentationDocumentSubmissionPartialState.Failure.Unknown(error = typedError)
            )

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.ConfirmPin)
            coroutineRule.testScope.advanceUntilIdle()

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            assertEquals(
                R.string.eaa_presentation_dialog_rp_rejection_sec_button_2,
                dialog.primaryButtonTextRes,
            )
            dialog.onPrimaryButtonClick()
            assertEquals(null, subject.viewState.value.errorDialog)
        }

    //region WD-4273: retry gated by the taxonomy on the request screen

    @Test
    fun `when the request fetch fails on the relying-party side, the error dialog offers no retry`() =
        coroutineRule.runTest {
            // INVALID_REQUEST's taxonomy action is CONTACT_RELYING_PARTY: retrying
            // cannot change the outcome, so no retry affordance may be offered.
            val fetchError = PresentationError.RelyingPartySide(
                traceId = "fetch-trace",
                cause = null,
                displayMessage = "invalid request",
                subType = PresentationError.RelyingPartySideSubType.INVALID_REQUEST,
            )
            givenRequestFetchEmitsFailure(fetchError)

            subject.effect.runFlowTest {
                subject.setEvent(Event.DoWork)
                coroutineRule.testScope.advanceUntilIdle()

                // WD-4274: shown as the designed dialog, not inline.
                assertEquals(null, subject.viewState.value.error)
                val dialog = assertNotNull(subject.viewState.value.errorDialog)
                assertEquals(R.string.eaa_presentation_malformed_presentation_title, dialog.titleRes)
                assertEquals("fetch-trace", dialog.traceId)

                // No retry: the only action leaves the screen, there is nothing to return to.
                dialog.onPrimaryButtonClick()
                coroutineRule.testScope.advanceUntilIdle()
                assertEquals(null, subject.viewState.value.errorDialog)
                assertIs<Effect.Navigation.Pop>(awaitItem())
            }
        }

    @Test
    fun `when the request fetch fails intermittently, the error dialog wires retry to a re-fetch`() =
        coroutineRule.runTest {
            val fetchError = PresentationError.Intermittent(
                traceId = "fetch-trace",
                cause = null,
                displayMessage = "timeout",
                subType = PresentationError.IntermittentSubType.NETWORK,
            )
            givenRequestFetchEmitsFailure(fetchError)

            subject.setEvent(Event.DoWork)
            coroutineRule.testScope.advanceUntilIdle()

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            assertEquals(R.string.global_timeout_slow_internet_error_title, dialog.titleRes)
            val retry = dialog.onPrimaryButtonClick

            // AC2: the retry handler re-runs the fetch (Event.DoWork). When the
            // re-fetch succeeds, the error state is cleared and the flow continues
            // normally (the request screen renders the claims; navigation happens
            // later, on user consent).
            whenever(presentationRequestInteractor.getRequestDocuments()).thenReturn(
                flowOf(
                    PresentationRequestInteractorPartialState.Success(
                        verifierName = "Verifier",
                        verifierIsTrusted = true,
                        requestDocuments = listOf(
                            requestDocumentItemUi(documentType = DocumentType.EAA)
                        ),
                    )
                )
            )
            retry()
            coroutineRule.testScope.advanceUntilIdle()

            assertEquals(null, subject.viewState.value.errorDialog)
            assertEquals(true, subject.viewState.value.claimItems.isNotEmpty())
            assertEquals(false, subject.viewState.value.isLoading)
        }

    @Test
    fun `when the request fetch fails as relying party unreachable, the error dialog offers no retry`() =
        coroutineRule.runTest {
            // RETRY_LATER: the body asks the user to come back later; no immediate retry.
            givenRequestFetchEmitsFailure(
                PresentationError.RelyingPartySide(
                    traceId = "fetch-trace",
                    cause = null,
                    displayMessage = "503",
                    subType = PresentationError.RelyingPartySideSubType.RELYING_PARTY_UNREACHABLE,
                )
            )

            subject.setEvent(Event.DoWork)
            coroutineRule.testScope.advanceUntilIdle()

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            assertEquals(R.string.global_try_again_later_button, dialog.primaryButtonTextRes)
        }

    @Test
    fun `when submitting fails intermittently, retry re-sends the documents and continues to success`() =
        coroutineRule.runTest {
            val processStates = MutableSharedFlow<PresentationRequestProcessPartialState>(replay = 1)
            processStates.emit(PresentationRequestProcessPartialState.RequestReadyToBeSent)
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.processRequest()).thenReturn(processStates)
            whenever(presentationRequestInteractor.sendRequestedDocuments()).thenReturn(
                PresentationDocumentSubmissionPartialState.Failure.Unknown(
                    error = PresentationError.Intermittent(
                        traceId = "submit-trace",
                        cause = null,
                        displayMessage = "timeout",
                        subType = PresentationError.IntermittentSubType.NETWORK,
                    )
                ),
                PresentationDocumentSubmissionPartialState.Success,
            )

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.ConfirmPin)
            coroutineRule.testScope.advanceUntilIdle()

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            assertEquals(
                R.string.global_timeout_slow_internet_error_prim_button,
                dialog.primaryButtonTextRes,
            )

            // AC2: the retry re-sends the documents (it does not re-fetch the request);
            // the verifier's response then reaches the still-running processRequest()
            // collector, which navigates to the success screen.
            dialog.onPrimaryButtonClick()
            coroutineRule.testScope.advanceUntilIdle()

            verify(presentationRequestInteractor, times(2)).sendRequestedDocuments()
            verify(presentationRequestInteractor, never()).getRequestDocuments()
            verify(rwscaPinHandler, never()).clearPinSession()
            assertEquals(null, subject.viewState.value.errorDialog)

            processStates.emit(PresentationRequestProcessPartialState.Success)
            coroutineRule.testScope.advanceUntilIdle()

            val effect = subject.effect.awaitFirstOf<Effect.Navigation.SwitchScreen>()
            assertEquals(subject.getNextScreen(), effect.screenRoute)
        }

    @Test
    fun `when dispatching to the verifier fails intermittently, the dialog offers no retry`() =
        coroutineRule.runTest {
            // The PIN session is cleared on this path and a re-collection would replay the
            // failure, so the retry action is downgraded to a "Retry later" dismiss.
            whenever(rwscaPinHandler.startPinSession(any())).thenReturn(StartPinSessionResult.Success)
            whenever(presentationRequestInteractor.processRequest()).thenReturn(
                flowOf(
                    PresentationRequestProcessPartialState.Failure(
                        error = PresentationError.Intermittent(
                            traceId = "dispatch-trace",
                            cause = null,
                            displayMessage = "timeout",
                            subType = PresentationError.IntermittentSubType.NETWORK,
                        )
                    )
                )
            )

            subject.typeIntoPinField("123456")
            subject.setEvent(Event.ConfirmPin)
            coroutineRule.testScope.advanceUntilIdle()

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            assertEquals(R.string.global_try_again_later_button, dialog.primaryButtonTextRes)
            assertEquals(null, dialog.secondaryButtonTextRes)

            dialog.onPrimaryButtonClick()
            coroutineRule.testScope.advanceUntilIdle()

            assertEquals(null, subject.viewState.value.errorDialog)
            verify(presentationRequestInteractor, never()).sendRequestedDocuments()
            verify(presentationRequestInteractor, never()).getRequestDocuments()
        }

    /**
     * Stubs the request-fetch path (`doWork()`): a real [UiSerializerImpl] round-trips
     * a default `RequestUriConfig` through the mocked [uiSerializer], and the
     * interactor's `getRequestDocuments()` flow emits the given failure.
     */
    private fun givenRequestFetchEmitsFailure(fetchError: PresentationError) {
        val requestUriConfig = RequestUriConfig(
            PresentationMode.OpenId4Vp(uri = "https://verifier.example.com/request", initiatorRoute = "")
        )
        val configRaw = UiSerializerImpl().toBase64(
            model = requestUriConfig,
            parser = RequestUriConfig.Parser,
        )
        whenever(uiSerializer.fromBase64(anyOrNull(), any<Class<RequestUriConfig>>(), any()))
            .thenAnswer { invocation ->
                UiSerializerImpl().fromBase64(
                    configRaw,
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                )
            }
        whenever(presentationRequestInteractor.getRequestDocuments()).thenReturn(
            flowOf(PresentationRequestInteractorPartialState.Failure(error = fetchError))
        )
    }

    //endregion

    private fun requestDocumentItemUi(documentType: DocumentType): RequestDocumentItemUi {
        val domainPayload = DocumentPayloadDomain(
            docName = "Document",
            docId = "document-id",
            documentType = documentType,
            docNamespace = "namespace",
            requestedClaimsCount = 1,
            totalClaimsCount = 1,
            docClaimsDomain = listOf(
                RequestDocumentClaim(
                    requestedClaim = MdocRequestedClaim(
                        id = null,
                        docType = "",
                        namespaceName = "",
                        dataElementName = "claim",
                        intentToRetain = false,
                        values = JsonArray(emptyList()),
                    ),
                    elementIdentifier = "claim",
                    value = "value",
                    readableName = "Claim",
                    isRequired = true,
                    isAvailable = true,
                    path = listOf("claim"),
                    withoutDetailLabel = "Claim",
                    labelValue = "value",
                )
            ),
        )

        return RequestDocumentItemUi(
            domainPayload = domainPayload,
            collapsedUiItem = CollapsedUiItem(
                isExpanded = false,
                uiItem = listItemData("document-id"),
            ),
            expandedUiItems = listOf(
                ExpandedUiItem(
                    domainPayload = domainPayload,
                    uiItem = listItemData("claim-id"),
                )
            ),
            requestedClaimsCount = 1,
            totalClaimsCount = 1,
        )
    }

    private fun listItemData(itemId: String): ListItemData =
        ListItemData(
            itemId = itemId,
            mainContentData = ListItemMainContentData.Text("value"),
        )
}

/**
 * Types [code] into the screen's code buffer, standing in for the field the user would use. The
 * digits never travel as an event payload, so a test has to put them where the field would.
 */
private fun PresentationRequestViewModel.typeIntoPinField(code: String) {
    viewState.value.pinState.buffer.type(code)
}
