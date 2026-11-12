/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.presentationfeature.error

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.config.EnvironmentConfig
import eu.europa.ec.corelogic.controller.ResolvePreferredPidConfigurationsPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.di.AppCoroutineScope
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.uilogic.navigation.CardReaderScreens
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.sprind.wallet.commonfeature.interactor.DocumentDeletionPartialState
import org.sprind.wallet.presentationfeature.interactor.PidDeletionInteractor
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class PresentationErrorViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val environmentConfig: EnvironmentConfig = mock {
        on { reportIssueUrl } doReturn REPORT_ISSUE_URL
    }
    private val configLogic: ConfigLogic = mock {
        on { environmentConfig } doReturn environmentConfig
    }

    private val walletCoreDocumentsController: WalletCoreDocumentsController = mock()
    private val pidDeletionInteractor: PidDeletionInteractor = mock()

    private fun viewModel(reason: BlockingErrorReason, traceId: String = "trace") =
        PresentationErrorViewModel(
            reason = reason,
            traceId = traceId,
            configLogic = configLogic,
            walletCoreDocumentsController = walletCoreDocumentsController,
            pidDeletionInteractor = pidDeletionInteractor,
            appCoroutineScope = AppCoroutineScope(coroutineRule.testScope),
        )

    @Test
    fun `state is rebuilt from the navigation reason and trace id`() {
        BlockingErrorReason.entries.forEach { reason ->
            val subject = viewModel(reason)

            assertEquals(
                PresentationErrorUiConfig.blocking(reason, "trace"),
                subject.viewState.value.config,
                reason.name,
            )
        }
    }

    @Test
    fun `an empty trace id argument means no trace id`() {
        val subject = viewModel(BlockingErrorReason.TRUST_FAILURE, traceId = "")

        assertNull(subject.viewState.value.config.traceId)
    }

    @Test
    fun `report issue asks to confirm, then opens the report-issue page`() = coroutineRule.runTest {
        // TRUST_FAILURE and OVERASKING: primary REPORT_ISSUE.
        listOf(BlockingErrorReason.TRUST_FAILURE, BlockingErrorReason.OVERASKING).forEach { reason ->
            val subject = viewModel(reason)

            subject.effect.runFlowTest {
                subject.setEvent(Event.PrimaryActionPressed)
                assertEquals(REPORT_ISSUE_URL, subject.viewState.value.pendingReportUrl, reason.name)

                subject.setEvent(Event.ReportRedirectConfirmed)
                assertEquals(Effect.Navigation.OpenUrl(REPORT_ISSUE_URL), awaitItem())
                assertNull(subject.viewState.value.pendingReportUrl)
            }
        }
    }

    @Test
    fun `dismissing the report confirmation stays on the screen`() = coroutineRule.runTest {
        val subject = viewModel(BlockingErrorReason.TRUST_FAILURE)

        subject.effect.runFlowTest {
            subject.setEvent(Event.PrimaryActionPressed)
            subject.setEvent(Event.ReportRedirectDismissed)

            assertNull(subject.viewState.value.pendingReportUrl)
            expectNoEvents()
        }
    }

    @Test
    fun `to overview secondary action opens the overview`() = coroutineRule.runTest {
        // TRUST_FAILURE, OVERASKING and PID_LOCKED: secondary TO_OVERVIEW (Figma "To overview").
        listOf(
            BlockingErrorReason.TRUST_FAILURE,
            BlockingErrorReason.OVERASKING,
            BlockingErrorReason.PID_LOCKED,
        ).forEach { reason ->
            val subject = viewModel(reason)

            subject.effect.runFlowTest {
                subject.setEvent(Event.SecondaryActionPressed)
                assertEquals(Effect.Navigation.ToDashboard, awaitItem(), reason.name)
            }
        }
    }

    @Test
    fun `missing credential primary action opens the overview`() = coroutineRule.runTest {
        val subject = viewModel(BlockingErrorReason.MISSING_CREDENTIAL)

        subject.effect.runFlowTest {
            subject.setEvent(Event.PrimaryActionPressed)
            assertEquals(Effect.Navigation.ToDashboard, awaitItem())
        }
    }

    @Test
    fun `add Digital ID opens the eID card reader for the preferred PID configurations`() =
        coroutineRule.runTest {
            whenever(walletCoreDocumentsController.resolvePreferredPidConfigurations()).thenReturn(
                ResolvePreferredPidConfigurationsPartialState.Success(
                    setOf(CredentialConfigurationIdentifier("pid-sd-jwt"))
                )
            )
            val subject = viewModel(BlockingErrorReason.MISSING_PID)

            subject.effect.runFlowTest {
                subject.setEvent(Event.PrimaryActionPressed)

                val effect = awaitItem()
                assertTrue(effect is Effect.Navigation.ToPidIssuance)
                assertTrue(effect.screenRoute.startsWith(CardReaderScreens.Reader.screenName))
                assertTrue("pid-sd-jwt" in effect.screenRoute)
            }
        }

    @Test
    fun `add Digital ID falls back to the overview when the PID configurations are unavailable`() =
        coroutineRule.runTest {
            listOf(
                ResolvePreferredPidConfigurationsPartialState.NoPidConfigurationsAdvertised,
                ResolvePreferredPidConfigurationsPartialState.Failure("offline"),
            ).forEach { result ->
                whenever(walletCoreDocumentsController.resolvePreferredPidConfigurations())
                    .thenReturn(result)
                val subject = viewModel(BlockingErrorReason.MISSING_PID)

                subject.effect.runFlowTest {
                    subject.setEvent(Event.PrimaryActionPressed)
                    assertEquals(Effect.Navigation.ToDashboard, awaitItem(), result.toString())
                }
            }
        }

    @Test
    fun `add Digital ID again deletes the locked Digital ID, then opens the eID card reader`() =
        coroutineRule.runTest {
            whenever(pidDeletionInteractor.deletePid())
                .thenReturn(flowOf(DocumentDeletionPartialState.Success))
            whenever(walletCoreDocumentsController.resolvePreferredPidConfigurations()).thenReturn(
                ResolvePreferredPidConfigurationsPartialState.Success(
                    setOf(CredentialConfigurationIdentifier("pid-sd-jwt"))
                )
            )
            val subject = viewModel(BlockingErrorReason.PID_LOCKED)

            subject.effect.runFlowTest {
                subject.setEvent(Event.PrimaryActionPressed)

                val effect = awaitItem()
                assertTrue(effect is Effect.Navigation.ToPidIssuance)
                assertTrue(effect.screenRoute.startsWith(CardReaderScreens.Reader.screenName))
            }
            inOrder(pidDeletionInteractor, walletCoreDocumentsController) {
                verify(pidDeletionInteractor).deletePid()
                verify(walletCoreDocumentsController).resolvePreferredPidConfigurations()
            }
        }

    @Test
    fun `add Digital ID again falls back to the overview when the deletion fails`() =
        coroutineRule.runTest {
            whenever(pidDeletionInteractor.deletePid())
                .thenReturn(flowOf(DocumentDeletionPartialState.Failure("INTERNAL_SERVER_ERROR")))
            val subject = viewModel(BlockingErrorReason.PID_LOCKED)

            subject.effect.runFlowTest {
                subject.setEvent(Event.PrimaryActionPressed)
                assertEquals(Effect.Navigation.ToDashboard, awaitItem())
            }
            verify(walletCoreDocumentsController, never()).resolvePreferredPidConfigurations()
        }

    @Test
    fun `back and to overview are ignored while the locked Digital ID is being deleted`() =
        coroutineRule.runTest {
            val deletion = CompletableDeferred<DocumentDeletionPartialState>()
            whenever(pidDeletionInteractor.deletePid()).thenReturn(flow { emit(deletion.await()) })
            val subject = viewModel(BlockingErrorReason.PID_LOCKED)

            subject.effect.runFlowTest {
                subject.setEvent(Event.PrimaryActionPressed)
                assertTrue(subject.viewState.value.isLoading)

                subject.setEvent(Event.ClosePressed)
                subject.setEvent(Event.SecondaryActionPressed)
                expectNoEvents()

                deletion.complete(DocumentDeletionPartialState.Failure("INTERNAL_SERVER_ERROR"))
                assertEquals(Effect.Navigation.ToDashboard, awaitItem())
            }
        }

    @Test
    fun `the deletion of the locked Digital ID outlives the screen`() =
        coroutineRule.runTest {
            val deletion = CompletableDeferred<DocumentDeletionPartialState>()
            var deletionFinished = false
            whenever(pidDeletionInteractor.deletePid()).thenReturn(
                flow {
                    val result = deletion.await()
                    deletionFinished = true
                    emit(result)
                }
            )
            val subject = viewModel(BlockingErrorReason.PID_LOCKED)

            subject.setEvent(Event.PrimaryActionPressed)
            coroutineRule.testScope.advanceUntilIdle()
            // The screen goes away mid-deletion.
            subject.viewModelScope.cancel()
            deletion.complete(DocumentDeletionPartialState.Success)
            coroutineRule.testScope.advanceUntilIdle()

            assertTrue(deletionFinished)
        }

    @Test
    fun `closing opens the overview`() = coroutineRule.runTest {
        val subject = viewModel(BlockingErrorReason.OVERASKING)

        subject.effect.runFlowTest {
            subject.setEvent(Event.ClosePressed)
            assertEquals(Effect.Navigation.ToDashboard, awaitItem())
        }
    }

    private companion object {
        const val REPORT_ISSUE_URL = "https://eudi-wallet.gov.de"
    }
}
