package eu.europa.ec.issuancefeature.ui.document.add

import org.sprind.wallet.analyticslogic.controller.Telemetry
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.corelogic.controller.IssueDocumentPartialState
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.serializer.UiSerializer
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
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class AddDocumentViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var addDocumentInteractor: AddDocumentInteractor

    @Mock
    private lateinit var uiSerializer: UiSerializer

    @Mock
    private lateinit var rwscaPinHandler: RwscaPinHandler

    @Mock
    private lateinit var telemetry: Telemetry

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun tearDown() {
        closeable.close()
    }
    @Test
    fun `when flow is NO_DOCUMENT, navigatableAction is NONE`() = coroutineRule.runTest {
        val vm = createVm(IssuanceFlowUiConfig.NO_DOCUMENT)
        assertEquals(ScreenNavigateAction.NONE, vm.viewState.value.navigatableAction)
    }

    @Test
    fun `when flow is EXTRA_DOCUMENT, navigatableAction is BACKABLE`() = coroutineRule.runTest {
        val vm = createVm(IssuanceFlowUiConfig.EXTRA_DOCUMENT)
        assertEquals(ScreenNavigateAction.BACKABLE, vm.viewState.value.navigatableAction)
    }

    @Test
    fun `when flow is NO_DOCUMENT, onBackAction triggers Finish effect`() = coroutineRule.runTest {
        val vm = createVm(IssuanceFlowUiConfig.NO_DOCUMENT)

        vm.viewState.value.onBackAction?.invoke()

        vm.effect.runFlowTest {
            assertEquals(Effect.Navigation.Finish, awaitItem())
        }
    }

    @Test
    fun `when flow is EXTRA_DOCUMENT, onBackAction triggers Pop effect`() = coroutineRule.runTest {
        val vm = createVm(IssuanceFlowUiConfig.EXTRA_DOCUMENT)

        vm.viewState.value.onBackAction?.invoke()

        vm.effect.runFlowTest {
            assertEquals(Effect.Navigation.Pop, awaitItem())
        }
    }


    @Test
    fun `when DismissError event, error fields reset`() = coroutineRule.runTest {
        val vm = createVm(IssuanceFlowUiConfig.EXTRA_DOCUMENT)

        vm.setEvent(Event.DismissError)

        assertFalse(vm.viewState.value.isLoading)
        assertNull(vm.viewState.value.error)
        assertNull(vm.viewState.value.errorDialog)
    }

    //region OnResumeIssuance

    private val mockedInterruptedErrorMessage = "issuance was interrupted"

    @Test
    fun `when OnResumeIssuance with Failure, then isLoading false and error set`() = coroutineRule.runTest {
        val issuanceState = MutableStateFlow<IssueDocumentPartialState>(
            IssueDocumentPartialState.Failure("error message")
        )
        whenever(addDocumentInteractor.issuanceState).thenReturn(issuanceState.asStateFlow())
        val vm = createVm(IssuanceFlowUiConfig.EXTRA_DOCUMENT)

        vm.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(vm.viewState.value.isLoading)
        assertNotNull(vm.viewState.value.error)
        assertEquals("error message", vm.viewState.value.error?.errorSubTitle)
    }

    @Test
    fun `when OnResumeIssuance with Success, then isLoading false and Finish effect emitted`() = coroutineRule.runTest {
        val issuanceState = MutableStateFlow<IssueDocumentPartialState>(
            IssueDocumentPartialState.Success(listOf("docId"))
        )
        whenever(addDocumentInteractor.issuanceState).thenReturn(issuanceState.asStateFlow())
        val vm = createVm(IssuanceFlowUiConfig.EXTRA_DOCUMENT)

        vm.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(vm.viewState.value.isLoading)
        assertNull(vm.viewState.value.error)

        vm.effect.runFlowTest {
            assertEquals(Effect.Navigation.Finish, awaitItem())
        }
    }

    @Test
    fun `when OnResumeIssuance with no terminal state, then timeout sets error`() = coroutineRule.runTest {
        val mockedInterruptedMsg = mockedInterruptedErrorMessage
        whenever(resourceProvider.getString(R.string.issuance_interrupted_error))
            .thenReturn(mockedInterruptedMsg)
        // InProgress never transitions — the withTimeout will fire after 5s
        val issuanceState = MutableStateFlow<IssueDocumentPartialState>(
            IssueDocumentPartialState.InProgress
        )
        whenever(addDocumentInteractor.issuanceState).thenReturn(issuanceState.asStateFlow())
        val vm = createVm(IssuanceFlowUiConfig.EXTRA_DOCUMENT)

        vm.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))

        // Advance virtual time past the 5s timeout
        coroutineRule.testScope.testScheduler.advanceTimeBy(5_001)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(vm.viewState.value.isLoading)
        assertNotNull(vm.viewState.value.error)
        assertEquals(mockedInterruptedMsg, vm.viewState.value.error?.errorSubTitle)
    }

    @Test
    fun `when OnResumeIssuance with Failure on hot flow, error survives past timeout`() = coroutineRule.runTest {
        // Hot StateFlow that never completes — reproduces the prod flow type and the
        // return@collect bug: under the old code withTimeout would fire after 5s and
        // overwrite the original failure message with issuance_interrupted_error.
        val issuanceState = MutableStateFlow<IssueDocumentPartialState>(
            IssueDocumentPartialState.Failure("error message")
        )
        whenever(addDocumentInteractor.issuanceState).thenReturn(issuanceState.asStateFlow())
        val vm = createVm(IssuanceFlowUiConfig.EXTRA_DOCUMENT)

        vm.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        // Immediate: original failure message is shown
        assertFalse(vm.viewState.value.isLoading)
        assertNotNull(vm.viewState.value.error)
        assertEquals("error message", vm.viewState.value.error?.errorSubTitle)

        // Advance virtual time past the 5s timeout — error must NOT be overwritten
        coroutineRule.testScope.testScheduler.advanceTimeBy(5_001)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(vm.viewState.value.isLoading)
        assertNotNull(vm.viewState.value.error)
        assertEquals("error message", vm.viewState.value.error?.errorSubTitle)
    }

    @Test
    fun `when OnResumeIssuance with Success on hot flow, no error appears past timeout`() = coroutineRule.runTest {
        val issuanceState = MutableStateFlow<IssueDocumentPartialState>(
            IssueDocumentPartialState.Success(listOf("docId"))
        )
        whenever(addDocumentInteractor.issuanceState).thenReturn(issuanceState.asStateFlow())
        val vm = createVm(IssuanceFlowUiConfig.EXTRA_DOCUMENT)

        vm.setEvent(Event.OnResumeIssuance("https://redirect.example/code=abc"))
        coroutineRule.testScope.testScheduler.runCurrent()

        // Immediate: success clears loading and error
        assertFalse(vm.viewState.value.isLoading)
        assertNull(vm.viewState.value.error)

        // Advance virtual time past the 5s timeout — no spurious error dialog
        coroutineRule.testScope.testScheduler.advanceTimeBy(5_001)
        coroutineRule.testScope.testScheduler.runCurrent()

        assertFalse(vm.viewState.value.isLoading)
        assertNull(vm.viewState.value.error)
    }

    //endregion

    private fun createVm(flowType: IssuanceFlowUiConfig): AddDocumentViewModel =
        AddDocumentViewModel(
            addDocumentInteractor = addDocumentInteractor,
            resourceProvider = resourceProvider,
            uiSerializer = uiSerializer,
            rwscaPinHandler = rwscaPinHandler,
            flowType = flowType,
            telemetry = telemetry,
        )
}
