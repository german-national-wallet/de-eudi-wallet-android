package eu.europa.ec.presentationfeature.ui.loading

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.IssueDocumentsPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingInteractor
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private lateinit var closeable: AutoCloseable

    val subject by lazy {
        PresentationLoadingViewModel(
            resourceProvider = resourceProvider,
            interactor = interactor,
            logController = logController,
        )
    }

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.getString(R.string.issuance_interrupted_error))
            .thenReturn("issuance was interrupted")
        whenever(resourceProvider.getString(R.string.loading_header_description))
            .thenReturn("Loading...")
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
}