package org.sprind.wallet.presentationfeature.ui.request

import eu.europa.ec.authenticationlogic.jwt.InstantProvider
import eu.europa.ec.commonfeature.interactor.RwscaPinHandler
import eu.europa.ec.commonfeature.interactor.StartPinSessionResult
import eu.europa.ec.commonfeature.ui.request.Effect
import eu.europa.ec.commonfeature.ui.request.Event
import eu.europa.ec.commonfeature.ui.request.RequestScreenStep
import eu.europa.ec.commonfeature.ui.request.model.CollapsedUiItem
import eu.europa.ec.commonfeature.ui.request.model.DocumentPayloadDomain
import eu.europa.ec.commonfeature.ui.request.model.DocumentType
import eu.europa.ec.commonfeature.ui.request.model.ExpandedUiItem
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentClaim
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.corelogic.di.AppCoroutineScope
import eu.europa.ec.presentationfeature.interactor.PresentationDocumentSubmissionPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestDeleteDocumentPartialState
import eu.europa.ec.presentationfeature.interactor.PresentationRequestInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationRequestProcessPartialState
import eu.europa.ec.presentationfeature.ui.request.PresentationRequestViewModel
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.serializer.UiSerializer
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
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.multipaz.request.MdocRequestedClaim
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.businesslogic.model.UserPinImpl
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs


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
        )
    }

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.getString(any())).thenReturn("")
        whenever(presentationRequestInteractor.getWalletPinBlockTime()).thenReturn("")
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

    private fun requestDocumentItemUi(documentType: DocumentType): RequestDocumentItemUi {
        val domainPayload = DocumentPayloadDomain(
            docName = "Document",
            docId = "document-id",
            documentType = documentType,
            docNamespace = "namespace",
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