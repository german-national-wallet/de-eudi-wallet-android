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
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private fun viewModel(reason: BlockingErrorReason, traceId: String = "trace") =
        PresentationErrorViewModel(reason = reason, traceId = traceId, configLogic = configLogic)

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
        // TRUST_FAILURE and OVERASKING: secondary TO_OVERVIEW (Figma "To overview").
        listOf(BlockingErrorReason.TRUST_FAILURE, BlockingErrorReason.OVERASKING).forEach { reason ->
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
