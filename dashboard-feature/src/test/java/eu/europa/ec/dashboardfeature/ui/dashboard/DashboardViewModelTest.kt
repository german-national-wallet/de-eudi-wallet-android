package eu.europa.ec.dashboardfeature.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.corelogic.controller.ResolvePreferredPidConfigurationsPartialState
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractorGetIssuedDocumentsPartialState
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.navigation.RouterHost
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.flow.flowOf
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
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.businesslogic.config.EidCardType
import org.sprind.wallet.businesslogic.config.UserRuntimeConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class DashboardViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var dashboardInteractor: DashboardInteractor

    @Mock
    private lateinit var addDocumentInteractor: AddDocumentInteractor

    @Mock
    private lateinit var uiSerializer: UiSerializer

    @Mock
    private lateinit var telemetry: Telemetry

    @Mock
    private lateinit var userRuntimeConfig: UserRuntimeConfig

    @Mock
    private lateinit var routerHost: RouterHost

    private lateinit var closeable: AutoCloseable

    @Mock
    private lateinit var navController: NavHostController

    @Mock
    private lateinit var navBackStackEntry: NavBackStackEntry

    @Mock
    private lateinit var savedStateHandle: SavedStateHandle

    val subject by lazy {
        DashboardViewModel(
            dashboardInteractor = dashboardInteractor,
            uiSerializer = uiSerializer,
            telemetry = telemetry,
            userRuntimeConfig = userRuntimeConfig,
            routerHost = routerHost,
        )
    }

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(dashboardInteractor.getAppVersion()).thenReturn("1.0.0")
        whenever(userRuntimeConfig.eidCardType).thenReturn(EidCardType.PHYSICAL)
        whenever(routerHost.getNavController()).thenReturn(navController)
        whenever(navController.currentBackStackEntry).thenReturn(navBackStackEntry)
        whenever(navBackStackEntry.savedStateHandle).thenReturn(savedStateHandle)
        // Stub getIssuedDocuments to emit Success immediately so Event.Init doesn't hang
        whenever(dashboardInteractor.getIssuedDocuments())
            .thenReturn(flowOf(DashboardInteractorGetIssuedDocumentsPartialState.Success(
                pidDocument = null,
                eaaDocuments = emptyList(),
            )))
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    //region OnInterruptedIssuance

    @Test
    fun `when OnInterruptedIssuance event, then errorDialog is set with ISSUANCE_INTERRUPTED error code`() =
        coroutineRule.runTest {
            subject.setEvent(Event.OnInterruptedIssuance)
            coroutineRule.testScope.testScheduler.runCurrent()

            val errorDialog = subject.viewState.value.errorDialog
            assertNotNull(errorDialog)
            assertEquals("ISSUANCE_INTERRUPTED", errorDialog!!.errorCode)
            assertEquals(R.string.issuance_interrupted_error_title, errorDialog.titleRes)
            assertEquals(R.string.issuance_interrupted_error_paragraph, errorDialog.bodyTextRes)
        }

    @Test
    fun `when OnInterruptedIssuance event, then isLoading is false`() =
        coroutineRule.runTest {
            subject.setEvent(Event.OnInterruptedIssuance)
            coroutineRule.testScope.testScheduler.runCurrent()

            assertFalse(subject.viewState.value.isLoading)
        }

    //endregion

    //region DismissError

    @Test
    fun `when DismissError event after OnInterruptedIssuance, then errorDialog is cleared`() =
        coroutineRule.runTest {
            subject.setEvent(Event.OnInterruptedIssuance)
            coroutineRule.testScope.testScheduler.runCurrent()
            assertNotNull(subject.viewState.value.errorDialog)

            subject.setEvent(Event.DismissError)
            coroutineRule.testScope.testScheduler.runCurrent()

            assertNull(subject.viewState.value.errorDialog)
            assertNull(subject.viewState.value.error)
        }

    //endregion

    //region SavedStateHandle → OnInterruptedIssuance

    @Test
    fun `when Event Init with INTERRUPTED_ISSUANCE_REDIRECT_KEY in SavedStateHandle, then OnInterruptedIssuance is dispatched`() =
        coroutineRule.runTest {
            whenever(savedStateHandle.get<String>(CoreActions.INTERRUPTED_ISSUANCE_REDIRECT_KEY))
                .thenReturn("https://redirect.example/code=abc")

            subject.setEvent(Event.Init)
            coroutineRule.testScope.testScheduler.runCurrent()

            // OnInterruptedIssuance should have been dispatched, setting errorDialog
            val errorDialog = subject.viewState.value.errorDialog
            assertNotNull(errorDialog)
            assertEquals("ISSUANCE_INTERRUPTED", errorDialog!!.errorCode)
        }

    @Test
    fun `when Event Init with INTERRUPTED_ISSUANCE_REDIRECT_KEY in SavedStateHandle, then key is removed`() =
        coroutineRule.runTest {
            whenever(savedStateHandle.get<String>(CoreActions.INTERRUPTED_ISSUANCE_REDIRECT_KEY))
                .thenReturn("https://redirect.example/code=abc")

            subject.setEvent(Event.Init)
            coroutineRule.testScope.testScheduler.runCurrent()

            // The key should have been removed from the SavedStateHandle
            org.mockito.kotlin.verify(savedStateHandle).remove<String>(
                CoreActions.INTERRUPTED_ISSUANCE_REDIRECT_KEY
            )
        }

    @Test
    fun `when Event Init without INTERRUPTED_ISSUANCE_REDIRECT_KEY, then errorDialog is not set`() =
        coroutineRule.runTest {
            whenever(savedStateHandle.get<String>(CoreActions.INTERRUPTED_ISSUANCE_REDIRECT_KEY))
                .thenReturn(null)

            subject.setEvent(Event.Init)
            coroutineRule.testScope.testScheduler.runCurrent()

            assertNull(subject.viewState.value.errorDialog)
        }

    //endregion

    //region IssuePreferredPidDocument

    @Test
    fun `when IssuePreferredPidDocument resolves successfully, then errorDialog is not set`() =
        coroutineRule.runTest {
            val resolved = setOf<CredentialConfigurationIdentifier>(
                CredentialConfigurationIdentifier("pid-mso-mdoc_2-beta"),
                CredentialConfigurationIdentifier("pid-sd-jwt_2-beta"),
            )
            whenever(dashboardInteractor.resolvePreferredPidConfigurations())
                .thenReturn(
                    ResolvePreferredPidConfigurationsPartialState.Success(resolved)
                )

            subject.setEvent(Event.IssuePreferredPidDocument)
            coroutineRule.testScope.testScheduler.runCurrent()

            // Success must not surface an error dialog.
            assertNull(subject.viewState.value.errorDialog)
        }

    @Test
    fun `when IssuePreferredPidDocument resolves with no PID advertised, then errorDialog has NO_PID_CONFIG_ADVERTISED`() =
        coroutineRule.runTest {
            whenever(dashboardInteractor.resolvePreferredPidConfigurations())
                .thenReturn(
                    ResolvePreferredPidConfigurationsPartialState.NoPidConfigurationsAdvertised
                )

            subject.setEvent(Event.IssuePreferredPidDocument)
            coroutineRule.testScope.testScheduler.runCurrent()

            val errorDialog = subject.viewState.value.errorDialog
            assertNotNull(errorDialog)
            assertEquals("NO_PID_CONFIG_ADVERTISED", errorDialog!!.errorCode)
        }

    @Test
    fun `when IssuePreferredPidDocument resolves with Failure, then errorDialog has PID_CONFIG_RESOLVE_FAILED`() =
        coroutineRule.runTest {
            whenever(dashboardInteractor.resolvePreferredPidConfigurations())
                .thenReturn(
                    ResolvePreferredPidConfigurationsPartialState.Failure("network down")
                )

            subject.setEvent(Event.IssuePreferredPidDocument)
            coroutineRule.testScope.testScheduler.runCurrent()

            val errorDialog = subject.viewState.value.errorDialog
            assertNotNull(errorDialog)
            assertEquals("PID_CONFIG_RESOLVE_FAILED", errorDialog!!.errorCode)
        }

    //endregion

    //endregion
}
