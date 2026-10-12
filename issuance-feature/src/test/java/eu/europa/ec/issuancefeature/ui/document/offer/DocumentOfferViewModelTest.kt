package eu.europa.ec.issuancefeature.ui.document.offer

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.issuancefeature.interactor.document.DocumentOfferInteractor
import eu.europa.ec.issuancefeature.interactor.document.IssueDocumentsInteractorPartialState
import eu.europa.ec.issuancefeature.interactor.document.ResolveDocumentOfferInteractorPartialState
import eu.europa.ec.issuancefeature.interactor.document.ResolvedOffer
import eu.europa.ec.issuancefeature.interactor.document.ResolvedOfferDocument
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class DocumentOfferViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var documentOfferInteractor: DocumentOfferInteractor

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var uiSerializer: UiSerializer

    private lateinit var closeable: AutoCloseable

    private val mockedOfferUiConfig = OfferUiConfig(
        offerURI = "https://issuer.example/offer",
        onSuccessNavigation = ConfigNavigation(
            navigationType = NavigationType.PushRoute(
                route = "dashboard",
                popUpToRoute = null,
            )
        ),
        onCancelNavigation = ConfigNavigation(
            navigationType = NavigationType.Pop
        ),
    )

    private val mockedAuthCodeFlowResolvedOffer = ResolvedOffer(
        offerUri = mockedOfferUiConfig.offerURI,
        issuerName = "issuer",
        issuerLogo = null,
        // Non-empty: Event.Init relies on `documents.isEmpty()` to tell a fresh
        // resolve apart from a screen resume once already resolved. An empty list
        // here would make every Event.Init re-resolve from scratch instead.
        documents = listOf(
            ResolvedOfferDocument(id = "doc-1", title = "Document", details = emptyList())
        ),
        txCodeLength = null,
        isAuthorizationCodeFlow = true,
    )

    val subject by lazy {
        DocumentOfferViewModel(
            documentOfferInteractor = documentOfferInteractor,
            resourceProvider = resourceProvider,
            uiSerializer = uiSerializer,
            offerSerializedConfig = "mocked-base64-config",
        )
    }

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        // Stub the uiSerializer so setInitialState() doesn't throw
        whenever(uiSerializer.fromBase64<OfferUiConfig>(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.anyOrNull(),
        )).thenReturn(mockedOfferUiConfig)
        whenever(resourceProvider.getString(R.string.issuance_interrupted_error))
            .thenReturn("issuance was interrupted")
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    /** Stubs a successful resolve of [mockedAuthCodeFlowResolvedOffer]. */
    private fun stubResolveSuccess() {
        whenever(documentOfferInteractor.resolveDocumentOffer(mockedOfferUiConfig.offerURI)).thenReturn(
            flowOf(ResolveDocumentOfferInteractorPartialState.Success(mockedAuthCodeFlowResolvedOffer))
        )
    }

    /** Stubs the auto-started issuance call (browser-open + consent-gate) to emit [flow]. */
    private fun stubIssueDocuments(flow: Flow<IssueDocumentsInteractorPartialState>) {
        whenever(
            documentOfferInteractor.issueDocuments(
                offerUri = any(),
                issuerName = any(),
                navigation = any(),
                txCode = anyOrNull(),
            )
        ).thenReturn(flow)
    }

    //region OnResumeIssuance

    /**
     * Drives the subject through a successful resolve of an auth-code-flow offer so
     * `resolvedOffer` is populated in state, as it would be by the time a real
     * VCI_RESUME_ACTION broadcast could ever arrive. The auto-started issuance
     * attempt is stubbed to never emit, so it doesn't interfere with the
     * `Event.OnResumeIssuance` under test — this mirrors the "ViewModel was
     * recreated while the browser round-trip was in flight" recovery scenario.
     */
    private fun setResolvedOfferInState() {
        stubResolveSuccess()
        stubIssueDocuments(MutableSharedFlow())
        subject.setEvent(Event.Init(deepLink = null))
        coroutineRule.testScope.testScheduler.runCurrent()
    }

    @Test
    fun `when OnResumeIssuance with Failure, then isLoading false and error set`() = coroutineRule.runTest {
        setResolvedOfferInState()
        whenever(documentOfferInteractor.issuanceState).thenReturn(
            flowOf(IssueDocumentsInteractorPartialState.Failure("error message"))
        )

        subject.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(subject.viewState.value.isLoading)
        assertNotNull(subject.viewState.value.error)
        assertEquals("error message", subject.viewState.value.error?.errorSubTitle)
    }

    @Test
    fun `when OnResumeIssuance with Success, then isLoading false and error cleared`() = coroutineRule.runTest {
        setResolvedOfferInState()
        whenever(documentOfferInteractor.issuanceState).thenReturn(
            flowOf(IssueDocumentsInteractorPartialState.Success(emptyList()))
        )

        subject.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(subject.viewState.value.isLoading)
        assertNull(subject.viewState.value.error)
    }

    @Test
    fun `when OnResumeIssuance with AwaitingConsent, then isLoading false and error cleared`() = coroutineRule.runTest {
        setResolvedOfferInState()
        whenever(documentOfferInteractor.issuanceState).thenReturn(
            flowOf(IssueDocumentsInteractorPartialState.AwaitingConsent)
        )

        subject.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(subject.viewState.value.isLoading)
        assertNull(subject.viewState.value.error)
        assertTrue(subject.viewState.value.awaitingConsent)
    }

    @Test
    fun `when OnResumeIssuance with DeferredSuccess, then isLoading false and error cleared`() = coroutineRule.runTest {
        setResolvedOfferInState()
        whenever(documentOfferInteractor.issuanceState).thenReturn(
            flowOf(IssueDocumentsInteractorPartialState.DeferredSuccess("success/route"))
        )

        subject.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(subject.viewState.value.isLoading)
        assertNull(subject.viewState.value.error)
    }

    @Test
    fun `when OnResumeIssuance with no terminal state, then timeout sets error with retry`() = coroutineRule.runTest {
        setResolvedOfferInState()
        // Never-emitting flow — withTimeout will fire after 5s
        whenever(documentOfferInteractor.issuanceState)
            .thenReturn(kotlinx.coroutines.flow.MutableSharedFlow())

        subject.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        // Advance virtual time past the 5s timeout
        coroutineRule.testScope.testScheduler.advanceTimeBy(5_001)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(subject.viewState.value.isLoading)
        assertNotNull(subject.viewState.value.error)
        assertEquals("issuance was interrupted", subject.viewState.value.error?.errorSubTitle)
        // A retry must be offered — there is no other way back into the flow.
        assertNotNull(subject.viewState.value.error?.onRetry)
    }

    @Test
    fun `when OnResumeIssuance with Failure on hot flow, error survives past timeout`() = coroutineRule.runTest {
        setResolvedOfferInState()
        // Hot StateFlow that never completes — reproduces the prod flow type and the
        // return@collect bug: under the old code withTimeout would fire after 5s and
        // overwrite the original failure message with issuance_interrupted_error.
        val issuanceState = MutableStateFlow<IssueDocumentsInteractorPartialState>(
            IssueDocumentsInteractorPartialState.Failure("error message")
        )
        whenever(documentOfferInteractor.issuanceState).thenReturn(issuanceState.asStateFlow())

        subject.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        // Immediate: original failure message is shown
        assertFalse(subject.viewState.value.isLoading)
        assertNotNull(subject.viewState.value.error)
        assertEquals("error message", subject.viewState.value.error?.errorSubTitle)

        // Advance virtual time past the 5s timeout — error must NOT be overwritten
        coroutineRule.testScope.testScheduler.advanceTimeBy(5_001)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(subject.viewState.value.isLoading)
        assertNotNull(subject.viewState.value.error)
        assertEquals("error message", subject.viewState.value.error?.errorSubTitle)
    }

    @Test
    fun `when OnResumeIssuance with Success on hot flow, no error appears past timeout`() = coroutineRule.runTest {
        setResolvedOfferInState()
        val issuanceState = MutableStateFlow<IssueDocumentsInteractorPartialState>(
            IssueDocumentsInteractorPartialState.Success(emptyList())
        )
        whenever(documentOfferInteractor.issuanceState).thenReturn(issuanceState.asStateFlow())

        subject.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        // Immediate: success clears loading and error
        assertFalse(subject.viewState.value.isLoading)
        assertNull(subject.viewState.value.error)

        // Advance virtual time past the 5s timeout — no spurious error dialog
        coroutineRule.testScope.testScheduler.advanceTimeBy(5_001)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(subject.viewState.value.isLoading)
        assertNull(subject.viewState.value.error)
    }

    //endregion

    //region Authorization-code flow: consent shown after auth

    @Test
    fun `when auth-code-flow offer resolves, then issuance auto-starts and stops at AwaitingConsent`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            stubIssueDocuments(flowOf(IssueDocumentsInteractorPartialState.AwaitingConsent))

            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()

            // Consent is shown only now, after the (auto-started) authorization step.
            assertFalse(subject.viewState.value.isLoading)
            assertNull(subject.viewState.value.error)
            assertEquals(mockedAuthCodeFlowResolvedOffer, subject.viewState.value.resolvedOffer)
        }

    @Test
    fun `when auth-code-flow offer resolves, then it stays loading until AwaitingConsent arrives`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            // Never-emitting flow: simulates the browser still being open.
            stubIssueDocuments(MutableSharedFlow())

            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()

            assertTrue(subject.viewState.value.isLoading)
        }

    @Test
    fun `when authorization fails before consent, then isLoading false, error set and offer cache cleared`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            stubIssueDocuments(flowOf(IssueDocumentsInteractorPartialState.Failure("auth failed")))

            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()

            assertFalse(subject.viewState.value.isLoading)
            assertNotNull(subject.viewState.value.error)
            assertEquals("auth failed", subject.viewState.value.error?.errorSubTitle)
            verify(documentOfferInteractor).clearCachedOffer(mockedAuthCodeFlowResolvedOffer.offerUri)
        }

    @Test
    fun `when consent confirmed for auth-code-flow offer, then it continues issuance and reaches Success`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            stubIssueDocuments(flowOf(IssueDocumentsInteractorPartialState.AwaitingConsent))
            whenever(resourceProvider.getString(R.string.eaa_issuance_success_title))
                .thenReturn("success")

            // Resolve first, so consent is showing (isLoading = false, resolvedOffer set).
            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()
            assertFalse(subject.viewState.value.isLoading)

            // Now the user taps "Add": this must NOT re-authorize, only continue issuance.
            whenever(documentOfferInteractor.issuanceState).thenReturn(
                flowOf(IssueDocumentsInteractorPartialState.Success(emptyList()))
            )
            subject.setEvent(Event.StickyButtonPressed(mock(Context::class.java)))
            coroutineRule.testScope.testScheduler.runCurrent()

            verify(documentOfferInteractor).continueIssuanceAfterConsent()
            assertFalse(subject.viewState.value.isLoading)
            assertNull(subject.viewState.value.error)
        }

    @Test
    fun `when auth fails, retry restarts the whole flow and can reach AwaitingConsent`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            stubIssueDocuments(flowOf(IssueDocumentsInteractorPartialState.Failure("auth failed")))

            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()

            val retry = subject.viewState.value.error?.onRetry
            assertNotNull(retry)

            // A retry must be able to reach a genuinely different outcome — reopening
            // the browser and succeeding this time — not just repeat the same failure.
            stubIssueDocuments(flowOf(IssueDocumentsInteractorPartialState.AwaitingConsent))

            retry.invoke()
            coroutineRule.testScope.testScheduler.runCurrent()

            assertFalse(subject.viewState.value.isLoading)
            assertNull(subject.viewState.value.error)
            assertTrue(subject.viewState.value.awaitingConsent)
        }

    @Test
    fun `when Add is tapped without a live consent gate, then it restarts the flow instead of hanging`() =
        coroutineRule.runTest {
            // Reproduces a consent-like state (isLoading = false, no error) reached
            // without ever having gone through AwaitingConsent.
            stubResolveSuccess()
            stubIssueDocuments(MutableSharedFlow()) // never resolves — still "stuck" in the browser
            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()
            assertFalse(subject.viewState.value.awaitingConsent)

            // Now make a fresh attempt succeed once the user taps "Add" again.
            stubIssueDocuments(flowOf(IssueDocumentsInteractorPartialState.AwaitingConsent))

            subject.setEvent(Event.StickyButtonPressed(mock(Context::class.java)))
            coroutineRule.testScope.testScheduler.runCurrent()

            verify(documentOfferInteractor, never()).continueIssuanceAfterConsent()
            assertFalse(subject.viewState.value.isLoading)
            assertTrue(subject.viewState.value.awaitingConsent)
        }

    @Test
    fun `when app backgrounds while the browser is open, isLoading stays true`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            stubIssueDocuments(MutableSharedFlow()) // browser still open
            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()
            assertTrue(subject.viewState.value.isLoading)

            subject.setEvent(Event.OnPause)
            coroutineRule.testScope.testScheduler.runCurrent()

            // Unlike other loading states, backgrounding here must not surface a
            // stale, non-functional consent screen.
            assertTrue(subject.viewState.value.isLoading)
        }

    @Test
    fun `when user backs out of the browser without finishing, then it stops being stuck loading after the grace period`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            // No redirect will ever arrive on this flow — simulates the browser
            // being closed (e.g. device back) without completing authorization.
            stubIssueDocuments(MutableSharedFlow())

            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()
            assertTrue(subject.viewState.value.isLoading)
            assertTrue(subject.viewState.value.browserAuthInProgress)

            // Activity pauses (browser opens on top) then resumes (user came back).
            subject.setEvent(Event.OnPause)
            coroutineRule.testScope.testScheduler.runCurrent()
            assertTrue(subject.viewState.value.isLoading)

            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()

            // Still within the grace period — no verdict yet, must not be stuck-but-silent.
            assertTrue(subject.viewState.value.isLoading)

            coroutineRule.testScope.testScheduler.advanceTimeBy(801)
            coroutineRule.testScope.testScheduler.runCurrent()

            // Grace period elapsed with nothing arriving: no longer stuck, and the
            // user has a working way forward.
            assertFalse(subject.viewState.value.isLoading)
            assertFalse(subject.viewState.value.browserAuthInProgress)
            assertNotNull(subject.viewState.value.error)
            assertNotNull(subject.viewState.value.error?.onRetry)
        }

    @Test
    fun `when a redirect lands during the grace period, then abandonment recovery does not override it`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            // extraBufferCapacity so tryEmit below reliably succeeds regardless of
            // exactly when the collector inside startAuthCodeFlowIssuance subscribes.
            val issuance = MutableSharedFlow<IssueDocumentsInteractorPartialState>(extraBufferCapacity = 1)
            stubIssueDocuments(issuance)

            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()
            assertTrue(subject.viewState.value.browserAuthInProgress)

            // App resumes (starts the grace-period check) just before the genuine
            // redirect's own event lands on the same flow.
            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()
            issuance.tryEmit(IssueDocumentsInteractorPartialState.AwaitingConsent)
            coroutineRule.testScope.testScheduler.runCurrent()

            assertTrue(subject.viewState.value.awaitingConsent)
            assertFalse(subject.viewState.value.browserAuthInProgress)
            assertNull(subject.viewState.value.error)

            // Once the grace period elapses, the earlier, now-stale check must not
            // clobber the legitimate state that already landed.
            coroutineRule.testScope.testScheduler.advanceTimeBy(801)
            coroutineRule.testScope.testScheduler.runCurrent()

            assertTrue(subject.viewState.value.awaitingConsent)
            assertNull(subject.viewState.value.error)
        }

    @Test
    fun `when the redirect is confirmed via OnResumeIssuance, browserAuthInProgress clears immediately`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            // The auto-started attempt never resolves on its own — mirrors a cold
            // ViewModel recreation where only the VCI_RESUME_ACTION broadcast (not
            // this original collector) will ever observe the outcome.
            stubIssueDocuments(MutableSharedFlow())
            // The authorization-code/token exchange that resumeOpenId4VciWithAuthorization
            // kicks off is still pending — deliberately outlives the grace period below,
            // the way a real network round-trip on a slow connection would.
            whenever(documentOfferInteractor.issuanceState).thenReturn(MutableSharedFlow())

            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()
            assertTrue(subject.viewState.value.browserAuthInProgress)

            // ON_RESUME arms the abandonment grace-period check...
            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()

            // ...but the VCI_RESUME_ACTION broadcast then confirms a genuine
            // redirect while the token exchange is still slow. Being called at all
            // is definitive proof a redirect arrived, so browserAuthInProgress must
            // clear right away rather than waiting for the exchange to finish.
            subject.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
            coroutineRule.testScope.testScheduler.runCurrent()
            assertFalse(subject.viewState.value.browserAuthInProgress)

            // Advance well past the grace period: a slow-but-healthy token exchange
            // must never be mistaken for the user having abandoned the browser.
            coroutineRule.testScope.testScheduler.advanceTimeBy(801)
            coroutineRule.testScope.testScheduler.runCurrent()

            assertTrue(subject.viewState.value.isLoading)
            assertNull(subject.viewState.value.error)

            // A further Event.Init (e.g. another brief backgrounding while the
            // exchange is still running) must not re-arm the check either, since
            // browserAuthInProgress is already false.
            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()
            coroutineRule.testScope.testScheduler.advanceTimeBy(801)
            coroutineRule.testScope.testScheduler.runCurrent()

            assertTrue(subject.viewState.value.isLoading)
            assertNull(subject.viewState.value.error)
        }

    @Test
    fun `when consent is confirmed but credential submission stalls, then it times out with a retryable error`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            stubIssueDocuments(flowOf(IssueDocumentsInteractorPartialState.AwaitingConsent))

            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()
            assertFalse(subject.viewState.value.isLoading)

            // Submission never emits anything — e.g. a stalled network call.
            whenever(documentOfferInteractor.issuanceState).thenReturn(MutableSharedFlow())
            subject.setEvent(Event.StickyButtonPressed(mock(Context::class.java)))
            coroutineRule.testScope.testScheduler.runCurrent()
            assertTrue(subject.viewState.value.isLoading)

            // Well within the timeout: still loading, no verdict yet.
            coroutineRule.testScope.testScheduler.advanceTimeBy(19_000)
            coroutineRule.testScope.testScheduler.runCurrent()
            assertTrue(subject.viewState.value.isLoading)

            // Past the timeout: must not be stuck loading forever, and must offer
            // a way back in (restarting the flow, since consent was already consumed).
            coroutineRule.testScope.testScheduler.advanceTimeBy(2_000)
            coroutineRule.testScope.testScheduler.runCurrent()
            assertFalse(subject.viewState.value.isLoading)
            assertNotNull(subject.viewState.value.error)
            assertNotNull(subject.viewState.value.error?.onRetry)
        }

    @Test
    fun `when consent is confirmed and submission succeeds well within the timeout, then no spurious error appears`() =
        coroutineRule.runTest {
            stubResolveSuccess()
            stubIssueDocuments(flowOf(IssueDocumentsInteractorPartialState.AwaitingConsent))
            whenever(resourceProvider.getString(R.string.eaa_issuance_success_title)).thenReturn("success")

            subject.setEvent(Event.Init(deepLink = null))
            coroutineRule.testScope.testScheduler.runCurrent()

            whenever(documentOfferInteractor.issuanceState).thenReturn(
                flowOf(IssueDocumentsInteractorPartialState.Success(emptyList()))
            )
            subject.setEvent(Event.StickyButtonPressed(mock(Context::class.java)))
            coroutineRule.testScope.testScheduler.runCurrent()

            // Advance past what the timeout would have been — success already
            // landed, so this must not retroactively surface an error.
            coroutineRule.testScope.testScheduler.advanceTimeBy(21_000)
            coroutineRule.testScope.testScheduler.runCurrent()

            assertFalse(subject.viewState.value.isLoading)
            assertNull(subject.viewState.value.error)
        }
    //endregion

    //region User authentication after consent

    /**
     * Mirrors the controller's issuanceState: a replaying shared flow, so the
     * latest UserAuthRequired is re-delivered to every new subscriber until the
     * next state replaces it.
     */
    private fun stubIssuanceStateAfterConsent(): MutableSharedFlow<IssueDocumentsInteractorPartialState> {
        stubResolveSuccess()
        stubIssueDocuments(flowOf(IssueDocumentsInteractorPartialState.AwaitingConsent))
        whenever(resourceProvider.getString(R.string.eaa_issuance_success_title)).thenReturn("success")
        val issuanceState = MutableSharedFlow<IssueDocumentsInteractorPartialState>(replay = 1)
        whenever(documentOfferInteractor.issuanceState).thenReturn(issuanceState)

        subject.setEvent(Event.Init(deepLink = null))
        coroutineRule.testScope.testScheduler.runCurrent()
        return issuanceState
    }

    private fun captureShownPrompt(times: Int = 1): DeviceAuthenticationResult {
        val captor = argumentCaptor<DeviceAuthenticationResult>()
        verify(documentOfferInteractor, times(times)).handleUserAuthentication(
            context = any(),
            crypto = any(),
            notifyOnAuthenticationFailure = any(),
            resultHandler = captor.capture(),
        )
        return captor.lastValue
    }

    @Test
    fun `when user authentication is required after consent, then the prompt is shown and issuance resumes on success`() =
        coroutineRule.runTest {
            val issuanceState = stubIssuanceStateAfterConsent()
            var resumed = false
            val authRequest = IssueDocumentsInteractorPartialState.UserAuthRequired(
                crypto = BiometricCrypto(null),
                resultHandler = DeviceAuthenticationResult(onAuthenticationSuccess = { resumed = true }),
            )

            subject.setEvent(Event.StickyButtonPressed(mock(Context::class.java)))
            coroutineRule.testScope.testScheduler.runCurrent()
            issuanceState.emit(authRequest)
            coroutineRule.testScope.testScheduler.runCurrent()

            val prompt = captureShownPrompt()
            assertTrue(subject.viewState.value.isLoading)

            prompt.onAuthenticationSuccess()
            coroutineRule.testScope.testScheduler.runCurrent()
            assertTrue(resumed)

            // The replayed, already-handled request must not prompt a second time.
            captureShownPrompt(times = 1)

            issuanceState.emit(IssueDocumentsInteractorPartialState.Success(emptyList()))
            coroutineRule.testScope.testScheduler.runCurrent()

            assertFalse(subject.viewState.value.isLoading)
            assertNull(subject.viewState.value.error)
        }

    @Test
    fun `when the user takes longer than the submit timeout on the prompt, then issuance does not time out`() =
        coroutineRule.runTest {
            val issuanceState = stubIssuanceStateAfterConsent()

            subject.setEvent(Event.StickyButtonPressed(mock(Context::class.java)))
            coroutineRule.testScope.testScheduler.runCurrent()
            issuanceState.emit(
                IssueDocumentsInteractorPartialState.UserAuthRequired(
                    crypto = BiometricCrypto(null),
                    resultHandler = DeviceAuthenticationResult(),
                )
            )
            coroutineRule.testScope.testScheduler.runCurrent()
            val prompt = captureShownPrompt()

            coroutineRule.testScope.testScheduler.advanceTimeBy(60_000)
            coroutineRule.testScope.testScheduler.runCurrent()
            assertTrue(subject.viewState.value.isLoading)
            assertNull(subject.viewState.value.error)

            prompt.onAuthenticationSuccess()
            issuanceState.emit(IssueDocumentsInteractorPartialState.Success(emptyList()))
            coroutineRule.testScope.testScheduler.runCurrent()

            assertFalse(subject.viewState.value.isLoading)
            assertNull(subject.viewState.value.error)
        }

    @Test
    fun `when the user cancels the prompt, then issuance is cancelled and a retryable error is shown`() =
        coroutineRule.runTest {
            val issuanceState = stubIssuanceStateAfterConsent()
            var cancelled = false

            subject.setEvent(Event.StickyButtonPressed(mock(Context::class.java)))
            coroutineRule.testScope.testScheduler.runCurrent()
            issuanceState.emit(
                IssueDocumentsInteractorPartialState.UserAuthRequired(
                    crypto = BiometricCrypto(null),
                    resultHandler = DeviceAuthenticationResult(onAuthenticationError = { cancelled = true }),
                )
            )
            coroutineRule.testScope.testScheduler.runCurrent()

            captureShownPrompt().onAuthenticationError()
            coroutineRule.testScope.testScheduler.runCurrent()
            assertTrue(cancelled)

            // wallet-core reports the cancelled issuance as a failure.
            issuanceState.emit(IssueDocumentsInteractorPartialState.Failure("Canceled"))
            coroutineRule.testScope.testScheduler.runCurrent()

            assertFalse(subject.viewState.value.isLoading)
            assertNotNull(subject.viewState.value.error?.onRetry)
        }

    @Test
    fun `when cancelling the issuance throws, then the prompt is still released and the outcome is shown`() =
        coroutineRule.runTest {
            val issuanceState = stubIssuanceStateAfterConsent()

            subject.setEvent(Event.StickyButtonPressed(mock(Context::class.java)))
            coroutineRule.testScope.testScheduler.runCurrent()
            issuanceState.emit(
                IssueDocumentsInteractorPartialState.UserAuthRequired(
                    crypto = BiometricCrypto(null),
                    resultHandler = DeviceAuthenticationResult(
                        onAuthenticationError = { throw IllegalStateException("cancel failed") }
                    ),
                )
            )
            coroutineRule.testScope.testScheduler.runCurrent()

            val thrown = runCatching { captureShownPrompt().onAuthenticationError() }
            assertTrue(thrown.isFailure)
            coroutineRule.testScope.testScheduler.runCurrent()

            // The ViewModel went back to awaiting the outcome instead of hanging on the
            // prompt, so a later terminal state still reaches the UI.
            issuanceState.emit(IssueDocumentsInteractorPartialState.Failure("Canceled"))
            coroutineRule.testScope.testScheduler.runCurrent()

            assertFalse(subject.viewState.value.isLoading)
            assertNotNull(subject.viewState.value.error?.onRetry)
        }
    //endregion
}
