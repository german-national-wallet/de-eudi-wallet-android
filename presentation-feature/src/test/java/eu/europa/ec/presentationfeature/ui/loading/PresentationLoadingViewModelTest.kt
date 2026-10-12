package eu.europa.ec.presentationfeature.ui.loading

import android.content.Context
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.commonfeature.ui.loading.Effect
import eu.europa.ec.commonfeature.ui.loading.Event
import eu.europa.ec.corelogic.controller.IssueDocumentsPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingObserveResponsePartialState
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingReissuePartialState
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingSendRequestedDocumentPartialState
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.navigation.PresentationScreens
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.corelogic.presentationerror.PresentationError
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class PresentationLoadingViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var interactor: PresentationLoadingInteractor

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var telemetry: Telemetry


    private val mockContext: Context = mock()

    private lateinit var closeable: AutoCloseable

    val subject by lazy {
        PresentationLoadingViewModel(
            resourceProvider = resourceProvider,
            interactor = interactor,
            logController = logController,
            telemetry = telemetry,
        )
    }

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.getString(R.string.issuance_interrupted_error))
            .thenReturn("issuance was interrupted")
        whenever(resourceProvider.getString(R.string.loading_header_description))
            .thenReturn("Loading...")
        whenever(resourceProvider.getString(R.string.global_error_title))
            .thenReturn("Something went wrong")
        whenever(resourceProvider.getString(R.string.global_error_paragraph))
            .thenReturn("Please try again later.")
        whenever(resourceProvider.getString(R.string.global_error_code_title))
            .thenReturn("Error code")
        whenever(resourceProvider.getString(R.string.global_trace_id_title))
            .thenReturn("Trace ID")
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    //region resumeOpenId4VciWithAuthorization

    @Test
    fun `when resumeOpenId4VciWithAuthorization with Failure, then error state is set`() = coroutineRule.runTest {
        whenever(interactor.issuanceState).thenReturn(
            flowOf(IssueDocumentsPartialState.Failure("error message"))
        )

        subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")
        coroutineRule.testScope.testScheduler.runCurrent()

        assertNotNull(subject.viewState.value.error)
        assertEquals("error message", subject.viewState.value.error?.errorSubTitle)
    }

    @Test
    fun `when resumeOpenId4VciWithAuthorization with Success, then error state is not set`() = coroutineRule.runTest {
        whenever(interactor.issuanceState).thenReturn(
            flowOf(IssueDocumentsPartialState.Success(emptyList()))
        )

        subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")
        coroutineRule.testScope.testScheduler.runCurrent()

        // Success should not set an error
        assertEquals(null, subject.viewState.value.error)
    }

    @Test
    fun `when resumeOpenId4VciWithAuthorization with DeferredSuccess, then error state is not set`() = coroutineRule.runTest {
        whenever(interactor.issuanceState).thenReturn(
            flowOf(IssueDocumentsPartialState.DeferredSuccess(emptyMap()))
        )

        subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")
        coroutineRule.testScope.testScheduler.runCurrent()

        assertEquals(null, subject.viewState.value.error)
    }

    @Test
    fun `when resumeOpenId4VciWithAuthorization with no terminal state, then timeout sets error`() = coroutineRule.runTest {
        // Never-emitting flow — withTimeout will fire after 5s
        whenever(interactor.issuanceState).thenReturn(MutableSharedFlow())

        subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")
        coroutineRule.testScope.testScheduler.runCurrent()

        // Advance virtual time past the 5s timeout
        coroutineRule.testScope.testScheduler.advanceTimeBy(5_001)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertNotNull(subject.viewState.value.error)
        assertEquals("issuance was interrupted", subject.viewState.value.error?.errorSubTitle)
    }

    @Test
    fun `when resumeOpenId4VciWithAuthorization with Failure on hot flow, error survives past timeout`() = coroutineRule.runTest {
        // Hot StateFlow that never completes — reproduces the prod flow type and the
        // return@collect bug: under the old code withTimeout would fire after 5s and
        // overwrite the original failure message with issuance_interrupted_error.
        val issuanceState = MutableStateFlow<IssueDocumentsPartialState>(
            IssueDocumentsPartialState.Failure("error message")
        )
        whenever(interactor.issuanceState).thenReturn(issuanceState.asStateFlow())

        subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")
        coroutineRule.testScope.testScheduler.runCurrent()

        // Immediate: original failure message is shown
        assertNotNull(subject.viewState.value.error)
        assertEquals("error message", subject.viewState.value.error?.errorSubTitle)

        // Advance virtual time past the 5s timeout — error must NOT be overwritten
        coroutineRule.testScope.testScheduler.advanceTimeBy(5_001)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertNotNull(subject.viewState.value.error)
        assertEquals("error message", subject.viewState.value.error?.errorSubTitle)
    }

    @Test
    fun `when resumeOpenId4VciWithAuthorization with Success on hot flow, no error appears past timeout`() = coroutineRule.runTest {
        val issuanceState = MutableStateFlow<IssueDocumentsPartialState>(
            IssueDocumentsPartialState.Success(emptyList())
        )
        whenever(interactor.issuanceState).thenReturn(issuanceState.asStateFlow())

        subject.resumeOpenId4VciWithAuthorization("https://redirect.example/code=abc")
        coroutineRule.testScope.testScheduler.runCurrent()

        // Immediate: success does not set an error
        assertEquals(null, subject.viewState.value.error)

        // Advance virtual time past the 5s timeout — no spurious error dialog
        coroutineRule.testScope.testScheduler.advanceTimeBy(5_001)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertEquals(null, subject.viewState.value.error)
    }

    //endregion

    //region sendRequestedDocuments

    /**
     * `Event.DoWork` makes the ViewModel collect [PresentationLoadingInteractor.observeResponse]
     * in a background coroutine; the send step is only triggered once that flow emits
     * `RequestReadyToBeSent`. Stubbing the flow with that state first keeps the collection alive
     * (an unstubbed mock would return null and crash) and reaches the failure under test.
     */
    private fun givenObserveResponseEmitsRequestReadyToBeSent() {
        whenever(interactor.observeResponse()).thenReturn(
            flowOf(PresentationLoadingObserveResponsePartialState.RequestReadyToBeSent)
        )
    }

    @Test
    fun `when sendRequestedDocuments fails, the typed presentation error drives the error state`() =
        coroutineRule.runTest {
            givenObserveResponseEmitsRequestReadyToBeSent()
            val typedError = PresentationError.WalletSide(
                traceId = "trace-123",
                cause = IllegalStateException("Key data missing for credential credential-id"),
                displayMessage = "missing unlock data",
                subType = PresentationError.WalletSideSubType.MISSING_UNLOCK_DATA,
            )
            whenever(interactor.sendRequestedDocuments()).thenReturn(
                PresentationLoadingSendRequestedDocumentPartialState.Failure(error = typedError)
            )

            subject.setEvent(Event.DoWork(context = mockContext))
            coroutineRule.testScope.testScheduler.runCurrent()

            // WD-4274: non-blocking errors are shown as the designed dialog, not inline.
            assertEquals(null, subject.viewState.value.error)
            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            assertEquals(R.string.global_error_title, dialog.titleRes)
            assertEquals(R.string.global_error_paragraph, dialog.bodyTextRes)
            assertEquals(typedError.errorCode, dialog.errorCode)
            assertEquals(typedError.traceId, dialog.traceId)
        }

    @Test
    fun `when sendRequestedDocuments fails with no network, the offline state is shown with a retry`() =
        coroutineRule.runTest {
            givenObserveResponseEmitsRequestReadyToBeSent()
            val typedError = PresentationError.WalletSide(
                traceId = "trace-123",
                cause = null,
                displayMessage = "no network",
                subType = PresentationError.WalletSideSubType.NO_NETWORK,
            )
            whenever(interactor.sendRequestedDocuments()).thenReturn(
                PresentationLoadingSendRequestedDocumentPartialState.Failure(error = typedError)
            )

            subject.setEvent(Event.DoWork(context = mockContext))
            coroutineRule.testScope.testScheduler.runCurrent()

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            assertEquals(R.string.global_no_internet_error_title, dialog.titleRes)
            // The recommended action for NO_NETWORK is CHECK_CONNECTION: retry is offered.
            dialog.onPrimaryButtonClick()
            coroutineRule.testScope.testScheduler.runCurrent()
            verify(interactor, times(2)).sendRequestedDocuments()
        }

    @Test
    fun `when sendRequestedDocuments fails on the relying-party side, no retry is offered`() =
        coroutineRule.runTest {
            givenObserveResponseEmitsRequestReadyToBeSent()
            val typedError = PresentationError.RelyingPartySide(
                traceId = "trace-123",
                cause = null,
                displayMessage = "invalid request",
                subType = PresentationError.RelyingPartySideSubType.INVALID_REQUEST,
            )
            whenever(interactor.sendRequestedDocuments()).thenReturn(
                PresentationLoadingSendRequestedDocumentPartialState.Failure(error = typedError)
            )

            subject.setEvent(Event.DoWork(context = mockContext))
            coroutineRule.testScope.testScheduler.runCurrent()

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            // WD-4267: retry must not be offered when retrying cannot change the outcome.
            dialog.onPrimaryButtonClick()
            coroutineRule.testScope.testScheduler.runCurrent()
            verify(interactor, times(1)).sendRequestedDocuments()
        }

    @Test
    fun `when sendRequestedDocuments succeeds, no error state is set`() =
        coroutineRule.runTest {
            givenObserveResponseEmitsRequestReadyToBeSent()
            whenever(interactor.sendRequestedDocuments()).thenReturn(
                PresentationLoadingSendRequestedDocumentPartialState.Success
            )

            subject.setEvent(Event.DoWork(context = mockContext))
            coroutineRule.testScope.testScheduler.runCurrent()

            assertEquals(null, subject.viewState.value.error)
            assertEquals(null, subject.viewState.value.errorDialog)
        }

    @Test
    fun `when sendRequestedDocuments fails intermittently, a successful retry continues to the success screen`() =
        coroutineRule.runTest {
            // First attempt: the send step fails with a transient timeout.
            // Retry (Event.DoWork) re-runs the flow; the re-send succeeds and the
            // verifier's response arrives, so the flow continues to the success screen.
            whenever(interactor.observeResponse()).thenReturn(
                flowOf(PresentationLoadingObserveResponsePartialState.RequestReadyToBeSent),
                flowOf(
                    PresentationLoadingObserveResponsePartialState.RequestReadyToBeSent,
                    PresentationLoadingObserveResponsePartialState.Success,
                ),
            )
            whenever(interactor.reissueLowBatchDocuments()).thenReturn(
                flowOf(PresentationLoadingReissuePartialState.NotNeeded)
            )
            whenever(interactor.sendRequestedDocuments()).thenReturn(
                PresentationLoadingSendRequestedDocumentPartialState.Failure(
                    error = PresentationError.Intermittent(
                        traceId = "trace-123",
                        cause = null,
                        displayMessage = "timeout",
                        subType = PresentationError.IntermittentSubType.NETWORK,
                    )
                ),
                PresentationLoadingSendRequestedDocumentPartialState.Success,
            )

            subject.effect.runFlowTest {
                subject.setEvent(Event.DoWork(context = mockContext))
                coroutineRule.testScope.testScheduler.runCurrent()

                val dialog = assertNotNull(subject.viewState.value.errorDialog)
                assertEquals(R.string.global_timeout_slow_internet_error_title, dialog.titleRes)

                dialog.onPrimaryButtonClick()
                coroutineRule.testScope.advanceUntilIdle()

                verify(interactor, times(2)).sendRequestedDocuments()
                assertEquals(null, subject.viewState.value.errorDialog)
                val effect = assertIs<Effect.Navigation.SwitchScreen>(awaitItem())
                assertEquals(
                    PresentationScreens.PresentationSuccess.screenRoute,
                    effect.screenRoute,
                )
            }
        }

    @Test
    fun `when sendRequestedDocuments fails as relying party unreachable, no retry is offered`() =
        coroutineRule.runTest {
            givenObserveResponseEmitsRequestReadyToBeSent()
            whenever(interactor.sendRequestedDocuments()).thenReturn(
                PresentationLoadingSendRequestedDocumentPartialState.Failure(
                    error = PresentationError.RelyingPartySide(
                        traceId = "trace-123",
                        cause = null,
                        displayMessage = "503",
                        subType = PresentationError.RelyingPartySideSubType.RELYING_PARTY_UNREACHABLE,
                    )
                )
            )

            subject.setEvent(Event.DoWork(context = mockContext))
            coroutineRule.testScope.testScheduler.runCurrent()

            val dialog = assertNotNull(subject.viewState.value.errorDialog)
            // RETRY_LATER (WD-4273): the user is asked to try again later; no retry.
            assertEquals(R.string.global_try_again_later_button, dialog.primaryButtonTextRes)
            dialog.onPrimaryButtonClick()
            coroutineRule.testScope.testScheduler.runCurrent()
            verify(interactor, times(1)).sendRequestedDocuments()
        }

    //endregion
}