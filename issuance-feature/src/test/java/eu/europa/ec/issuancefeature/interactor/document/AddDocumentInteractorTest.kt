/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.issuancefeature.interactor.document

import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractor
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractorImpl
import eu.europa.ec.commonfeature.interactor.AddDocumentInteractorPartialState
import eu.europa.ec.commonfeature.interactor.IssuanceEvent
import eu.europa.ec.commonfeature.util.TestsData.mockedPidId
import eu.europa.ec.commonfeature.util.TestsData.mockedUriPath1
import eu.europa.ec.corelogic.controller.FetchScopedDocumentsPartialState
import eu.europa.ec.corelogic.controller.IssuanceMethod
import eu.europa.ec.corelogic.controller.IssueDocumentPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.handler.AusweisSdkAuthorizationHandler
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.mockedGenericErrorMessage
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.extension.toFlow
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.toList
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Locale

class AddDocumentInteractorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock lateinit var walletCoreDocumentsController: WalletCoreDocumentsController
    @Mock lateinit var resourceProvider: ResourceProvider
    @Mock
    private lateinit var authHandler: AusweisSdkAuthorizationHandler

    private lateinit var closeable: AutoCloseable
    private lateinit var interactorScope: CoroutineScope
    private lateinit var interactor: AddDocumentInteractor

    private val locale: Locale = Locale("en")

    private val issuanceMethod = IssuanceMethod.OPENID4VCI
    private val configId = "id"
    private val issuerId = "https://issuer.example"

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)

        interactorScope = TestScope(UnconfinedTestDispatcher())

        interactor = AddDocumentInteractorImpl(
            walletCoreDocumentsController = walletCoreDocumentsController,
            resourceProvider = resourceProvider,
            interactorScope = interactorScope,
            ausweisSdkAuthorizationHandler = authHandler

        )

        whenever(resourceProvider.genericErrorMessage()).thenReturn(mockedGenericErrorMessage)
        whenever(resourceProvider.getLocale()).thenReturn(locale)
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    //region getAddDocumentOption

    @Test
    fun `When getAddDocumentOption is called, Then Success is emitted with mapped options`() {
        coroutineRule.runTest {
            whenever(walletCoreDocumentsController.getScopedDocuments(any())).thenReturn(
                FetchScopedDocumentsPartialState.Success(emptyList())
            )

            interactor.getAddDocumentOption(flowType = IssuanceFlowUiConfig.NO_DOCUMENT).runFlowTest {
                assertEquals(
                    AddDocumentInteractorPartialState.Success(options = emptyList()),
                    awaitItem()
                )
            }
        }
    }

    @Test
    fun `Given NO_DOCUMENT flow with PID and EAA documents, Then only PID is returned`() {
        coroutineRule.runTest {
            val scopedDocuments = listOf(
                eu.europa.ec.corelogic.model.ScopedDocumentDomain(
                    name = "EU PID",
                    configurationId = "pid_config",
                    credentialIssuerId = "https://issuer.example",
                    formatType = null,
                    isPid = true
                ),
                eu.europa.ec.corelogic.model.ScopedDocumentDomain(
                    name = "mDL",
                    configurationId = "mdl_config",
                    credentialIssuerId = "https://issuer.example",
                    formatType = null,
                    isPid = false
                ),
                eu.europa.ec.corelogic.model.ScopedDocumentDomain(
                    name = "Age Verification",
                    configurationId = "age_config",
                    credentialIssuerId = "https://issuer.example",
                    formatType = null,
                    isPid = false
                )
            )

            whenever(walletCoreDocumentsController.getScopedDocuments(any())).thenReturn(
                FetchScopedDocumentsPartialState.Success(scopedDocuments)
            )

            interactor.getAddDocumentOption(flowType = IssuanceFlowUiConfig.NO_DOCUMENT).runFlowTest {
                val result = awaitItem() as AddDocumentInteractorPartialState.Success
                assertEquals(1, result.options.size)
                assertEquals("EU PID", (result.options[0].itemData.mainContentData as eu.europa.ec.uilogic.component.ListItemMainContentData.Text).text)
            }
        }
    }

    //endregion

    //region startIssueDocumentAttested

    @Test
    fun `When startIssueDocumentAttested is called, Then controller issueDocumentAttested is invoked`() {
        coroutineRule.runTest {
            val spec = mock<WalletInstanceAttestationSpec>()
            val configIds = setOf(CredentialConfigurationIdentifier("id"))

            whenever(
                walletCoreDocumentsController.issueDocumentAttested(
                    issuanceMethod = issuanceMethod,
                    configIds = configIds.map { it.value },
                    issuerId = issuerId,
                    walletInstanceAttestationSpec = spec
                )
            ).thenReturn(IssueDocumentPartialState.InProgress.toFlow())

            interactor.startIssueDocumentAttested(
                issuanceMethod = issuanceMethod,
                configIds = configIds,
                issuerId = issuerId,
                walletInstanceAttestationSpec = spec
            )

            verify(walletCoreDocumentsController).issueDocumentAttested(
                issuanceMethod = issuanceMethod,
                configIds = configIds.map { it.value }.toList(),
                issuerId = issuerId,
                walletInstanceAttestationSpec = spec
            )
        }
    }

    @Test
    fun `Given controller emits Success, Then issuanceState is Success and Completed event is emitted`() {
        coroutineRule.runTest {
            val spec = mock<WalletInstanceAttestationSpec>()
            val configIds = setOf(CredentialConfigurationIdentifier(configId))

            whenever(
                walletCoreDocumentsController.issueDocumentAttested(
                    issuanceMethod = issuanceMethod,
                    configIds = configIds.map { it.value },
                    issuerId = issuerId,
                    walletInstanceAttestationSpec = spec
                )
            ).thenReturn(IssueDocumentPartialState.Success(listOf(mockedPidId)).toFlow())

            interactor.issuanceEvents.runFlowTest {
                interactor.startIssueDocumentAttested(
                    issuanceMethod = issuanceMethod,
                    configIds = configIds.toSet(),
                    issuerId = issuerId,
                    walletInstanceAttestationSpec = spec
                )

                assertEquals(IssuanceEvent.Completed, awaitItem())
            }

            assertEquals(
                IssueDocumentPartialState.Success(listOf(mockedPidId)),
                interactor.issuanceState.value
            )
        }
    }

    @Test
    fun `Given controller emits Failure, Then issuanceState is Failure and Failed event is emitted`() {
        coroutineRule.runTest {
            val spec = mock<WalletInstanceAttestationSpec>()
            val error = "ISSUANCE_FAILED"
            val configIds = setOf(CredentialConfigurationIdentifier(configId))

            whenever(
                walletCoreDocumentsController.issueDocumentAttested(
                    issuanceMethod = issuanceMethod,
                    configIds = configIds.map { it.value },
                    issuerId = issuerId,
                    walletInstanceAttestationSpec = spec
                )
            ).thenReturn(IssueDocumentPartialState.Failure(errorMessage = error).toFlow())

            interactor.issuanceEvents.runFlowTest {
                interactor.startIssueDocumentAttested(
                    issuanceMethod = issuanceMethod,
                    configIds = configIds,
                    issuerId = issuerId,
                    walletInstanceAttestationSpec = spec
                )

                assertEquals(IssuanceEvent.Failed(error), awaitItem())
            }

            assertEquals(
                IssueDocumentPartialState.Failure(errorMessage = error),
                interactor.issuanceState.value
            )
        }
    }

    @Test
    fun `Given controller throws, Then Failed event with generic error is emitted and state is Failure`() {
        coroutineRule.runTest {
            val spec = mock<WalletInstanceAttestationSpec>()
            val configIds = setOf(CredentialConfigurationIdentifier(configId))

            whenever(
                walletCoreDocumentsController.issueDocumentAttested(
                    issuanceMethod = issuanceMethod,
                    configIds = configIds.map { it.value },
                    issuerId = issuerId,
                    walletInstanceAttestationSpec = spec
                )
            ).thenReturn(flow { throw RuntimeException() })

            interactor.issuanceEvents.runFlowTest {
                interactor.startIssueDocumentAttested(
                    issuanceMethod = issuanceMethod,
                    configIds = configIds,
                    issuerId = issuerId,
                    walletInstanceAttestationSpec = spec
                )

                assertEquals(IssuanceEvent.Failed(mockedGenericErrorMessage), awaitItem())
            }

            assertEquals(
                IssueDocumentPartialState.Failure(errorMessage = mockedGenericErrorMessage),
                interactor.issuanceState.value
            )
        }
    }

    //endregion

    //region resumeOpenId4VciWithAuthorization

    // Case of resumeOpenId4VciWithAuthorization being called on the interactor
    // the expected result is the resumeOpenId4VciWithAuthorization function to be executed on
    // the walletCoreDocumentsController
    @Test
    fun `When interactor resumeOpenId4VciWithAuthorization is called, Then resumeOpenId4VciWithAuthorization should be invoked on the controller`() {
        // When
        interactor.resumeOpenId4VciWithAuthorization(mockedUriPath1)

        verify(walletCoreDocumentsController, times(1))
            .resumeOpenId4VciWithAuthorization(mockedUriPath1)
    }

    @Test
    fun `Given controller issuanceState emits Failure, Then interactor resumeOpenId4VciWithAuthorization forwards Failure to its own issuanceState`() {
        coroutineRule.runTest {
            val errorMessage = "issuance was interrupted"
            val controllerIssuanceState = kotlinx.coroutines.flow.MutableSharedFlow<
                eu.europa.ec.corelogic.controller.IssueDocumentsPartialState>(
                replay = 1
            )
            controllerIssuanceState.tryEmit(
                eu.europa.ec.corelogic.controller.IssueDocumentsPartialState.Failure(errorMessage)
            )
            whenever(walletCoreDocumentsController.issuanceState)
                .thenReturn(controllerIssuanceState)

            interactor.resumeOpenId4VciWithAuthorization(mockedUriPath1)

            // The interactorScope is a TestScope with UnconfinedTestDispatcher, so the
            // launched coroutine runs eagerly. Advance to let the collect propagate.
            (interactorScope as kotlinx.coroutines.test.TestScope).advanceUntilIdle()

            assertEquals(
                IssueDocumentPartialState.Failure(errorMessage),
                interactor.issuanceState.value
            )
        }
    }

    @Test
    fun `Given controller issuanceState emits Success, Then interactor resumeOpenId4VciWithAuthorization forwards Success and emits Completed`() {
        coroutineRule.runTest {
            val controllerIssuanceState = kotlinx.coroutines.flow.MutableSharedFlow<
                eu.europa.ec.corelogic.controller.IssueDocumentsPartialState>(
                replay = 1
            )
            controllerIssuanceState.tryEmit(
                eu.europa.ec.corelogic.controller.IssueDocumentsPartialState.Success(
                    listOf(mockedPidId)
                )
            )
            whenever(walletCoreDocumentsController.issuanceState)
                .thenReturn(controllerIssuanceState)

            interactor.issuanceEvents.runFlowTest {
                interactor.resumeOpenId4VciWithAuthorization(mockedUriPath1)
                assertEquals(IssuanceEvent.Completed, awaitItem())
            }

            (interactorScope as kotlinx.coroutines.test.TestScope).advanceUntilIdle()

            assertEquals(
                IssueDocumentPartialState.Success(listOf(mockedPidId)),
                interactor.issuanceState.value
            )
        }
    }

    @Test
    fun `Given controller issuanceState emits DeferredSuccess, Then interactor resumeOpenId4VciWithAuthorization forwards DeferredSuccess`() {
        coroutineRule.runTest {
            val deferredDocuments = mapOf(mockedPidId to "mso_mdoc")
            val controllerIssuanceState = kotlinx.coroutines.flow.MutableSharedFlow<
                eu.europa.ec.corelogic.controller.IssueDocumentsPartialState>(
                replay = 1
            )
            controllerIssuanceState.tryEmit(
                eu.europa.ec.corelogic.controller.IssueDocumentsPartialState.DeferredSuccess(
                    deferredDocuments
                )
            )
            whenever(walletCoreDocumentsController.issuanceState)
                .thenReturn(controllerIssuanceState)

            interactor.resumeOpenId4VciWithAuthorization(mockedUriPath1)

            (interactorScope as kotlinx.coroutines.test.TestScope).advanceUntilIdle()

            assertEquals(
                IssueDocumentPartialState.DeferredSuccess(deferredDocuments),
                interactor.issuanceState.value
            )
        }
    }

    @Test
    fun `Given second resumeOpenId4VciWithAuthorization, previous collector is cancelled`() {
        coroutineRule.runTest {
            val controllerIssuanceState = kotlinx.coroutines.flow.MutableSharedFlow<
                eu.europa.ec.corelogic.controller.IssueDocumentsPartialState>(
                replay = 0,
                extraBufferCapacity = 16
            )
            whenever(walletCoreDocumentsController.issuanceState)
                .thenReturn(controllerIssuanceState)

            interactor.resumeOpenId4VciWithAuthorization(mockedUriPath1)

            // Capture the job launched by the first resume via the interactorScope's
            // TestScope; the scope's coroutineContext.job has the launch as a child.
            val firstJob = (interactorScope as kotlinx.coroutines.test.TestScope)
                .coroutineContext[kotlinx.coroutines.Job]!!.children.single()

            interactor.resumeOpenId4VciWithAuthorization(mockedUriPath1)

            // The first collector must have been cancelled; the second resume launches
            // a new collector, so exactly one child is active now.
            assertTrue("first collector should be cancelled", !firstJob.isActive)
            val activeChildren = (interactorScope as kotlinx.coroutines.test.TestScope)
                .coroutineContext[kotlinx.coroutines.Job]!!.children
                .filter { it.isActive }
                .toList()
            assertEquals("exactly one active collector after second resume", 1, activeChildren.size)
        }
    }

    //endregion
}
