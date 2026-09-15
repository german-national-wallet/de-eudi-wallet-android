package eu.europa.ec.issuancefeature.ui.document.offer

import eu.europa.ec.commonfeature.config.OfferUiConfig
import eu.europa.ec.issuancefeature.interactor.document.DocumentOfferInteractor
import eu.europa.ec.issuancefeature.interactor.document.IssueDocumentsInteractorPartialState
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
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    //region OnResumeIssuance

    @Test
    fun `when OnResumeIssuance with Failure, then isLoading false and error set`() = coroutineRule.runTest {
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
        whenever(documentOfferInteractor.issuanceState).thenReturn(
            flowOf(IssueDocumentsInteractorPartialState.Success(emptyList()))
        )

        subject.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(subject.viewState.value.isLoading)
        assertNull(subject.viewState.value.error)
    }

    @Test
    fun `when OnResumeIssuance with DeferredSuccess, then isLoading false and error cleared`() = coroutineRule.runTest {
        whenever(documentOfferInteractor.issuanceState).thenReturn(
            flowOf(IssueDocumentsInteractorPartialState.DeferredSuccess("success/route"))
        )

        subject.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(subject.viewState.value.isLoading)
        assertNull(subject.viewState.value.error)
    }

    @Test
    fun `when OnResumeIssuance with no terminal state, then timeout sets error`() = coroutineRule.runTest {
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
    }

    @Test
    fun `when OnResumeIssuance with Failure on hot flow, error survives past timeout`() = coroutineRule.runTest {
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
}