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

import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.commonfeature.util.TestsData.mockedMdlId
import eu.europa.ec.commonfeature.util.TestsData.mockedPidId
import eu.europa.ec.corelogic.controller.IssueDocumentsPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.copy
import eu.europa.ec.testfeature.getMockedMdlWithBasicFields
import eu.europa.ec.testfeature.getMockedPidWithBasicFields
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class DocumentOfferInteractorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock lateinit var walletCoreDocumentsController: WalletCoreDocumentsController
    @Mock lateinit var deviceAuthenticationInteractor: DeviceAuthenticationInteractor
    @Mock lateinit var resourceProvider: ResourceProvider
    @Mock lateinit var uiSerializer: UiSerializer

    private lateinit var closeable: AutoCloseable
    private lateinit var interactor: DocumentOfferInteractor

    private val mockedConfigNavigation = ConfigNavigation(
        navigationType = NavigationType.PushRoute(
            route = "dashboard",
            popUpToRoute = null,
        )
    )

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)

        interactor = DocumentOfferInteractorImpl(
            walletCoreDocumentsController = walletCoreDocumentsController,
            deviceAuthenticationInteractor = deviceAuthenticationInteractor,
            resourceProvider = resourceProvider,
            uiSerializer = uiSerializer,

        )
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `Given controller emits AwaitingConsent When issueDocuments collects, Then interactor maps to AwaitingConsent`() {
        coroutineRule.runTest {
            whenever(walletCoreDocumentsController.issueDocumentsByOfferUri(any(), anyOrNull())).thenReturn(
                flowOf(IssueDocumentsPartialState.AwaitingConsent)
            )

            interactor.issueDocuments(
                offerUri = "offer-uri",
                issuerName = "issuer",
                navigation = mockedConfigNavigation,
                txCode = null
            ).runFlowTest {
                assertEquals(
                    IssueDocumentsInteractorPartialState.AwaitingConsent,
                    awaitItem()
                )
            }
        }
    }

    @Test
    fun `Given controller issuanceState emits AwaitingConsent, Then interactor issuanceState maps to AwaitingConsent`() {
        coroutineRule.runTest {
            // issuanceState is wired up once at construction time, so the controller
            // stub must be in place before building this test's own interactor instance.
            whenever(walletCoreDocumentsController.issuanceState).thenReturn(
                MutableSharedFlow<IssueDocumentsPartialState>(replay = 1).apply {
                    tryEmit(IssueDocumentsPartialState.AwaitingConsent)
                }
            )
            val interactorWithStubbedState = DocumentOfferInteractorImpl(
                walletCoreDocumentsController = walletCoreDocumentsController,
                deviceAuthenticationInteractor = deviceAuthenticationInteractor,
                resourceProvider = resourceProvider,
                uiSerializer = uiSerializer,
            )

            interactorWithStubbedState.issuanceState.runFlowTest {
                assertEquals(
                    IssueDocumentsInteractorPartialState.AwaitingConsent,
                    awaitItem()
                )
            }
        }
    }

    @Test
    fun `When continueIssuanceAfterConsent is called, Then it delegates to the controller`() {
        coroutineRule.runTest {
            interactor.continueIssuanceAfterConsent()

            verify(walletCoreDocumentsController).continueIssuanceAfterConsent()
        }
    }

    @Test
    fun `Given pre exiting documents When issueDocuments is called, Then duplicates get deleted`() {
        coroutineRule.runTest {
            val newPidId = "new pid id"
            val mockedPidWithBasicFields = getMockedPidWithBasicFields()
            val mockedMdlWithBasicFields = getMockedMdlWithBasicFields()
            val mockedNewPid = getMockedPidWithBasicFields().copy(id = newPidId)

            whenever(walletCoreDocumentsController.getAllIssuedDocuments())
                .thenReturn(listOf(
                    mockedPidWithBasicFields,
                    mockedMdlWithBasicFields,
                    mockedNewPid,
                ))

            whenever(walletCoreDocumentsController.issueDocumentsByOfferUri(any(), any())).thenReturn(
                flowOf(IssueDocumentsPartialState.Success(listOf(newPidId)))
            )

            interactor.issueDocuments(
                offerUri = "",
                issuerName = "",
                navigation = mockedConfigNavigation,
                txCode = ""
            ).runFlowTest {
                awaitItem()
                verify(walletCoreDocumentsController).deleteDocument(mockedPidId)
                verify(walletCoreDocumentsController, never()).deleteDocument(mockedMdlId)
                verify(walletCoreDocumentsController, never()).deleteDocument(newPidId)
            }
        }
    }
}
