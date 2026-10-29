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

package org.sprind.wallet.onboardingfeature.setup.platformauth

import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Rule
import org.junit.Test
import org.sprind.wallet.onboardingfeature.setup.platformauth.PlatformAuthSetupViewModel.Companion.CHECKING_DURATION_MILLIS
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformAuthSetupViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Test
    fun `when the step opens then it shows the checking phase until it has elapsed`() =
        coroutineRule.runTest {
            val subject = PlatformAuthSetupViewModel()
            assertTrue(subject.viewState.value.isChecking)

            advance(CHECKING_DURATION_MILLIS - 1)
            assertTrue(subject.viewState.value.isChecking)

            advance(1)
            assertEquals(PlatformAuthSetupState(isChecking = false), subject.viewState.value)
        }

    @Test
    fun `given the check is running when continuing then the sheet is not shown`() =
        coroutineRule.runTest {
            val subject = PlatformAuthSetupViewModel()

            subject.setEvent(PlatformAuthSetupEvent.Continue)

            assertFalse(subject.viewState.value.isSheetVisible)
        }

    @Test
    fun `given the check is done when continuing then the sheet is shown`() =
        coroutineRule.runTest {
            val subject = checkedViewModel()

            subject.setEvent(PlatformAuthSetupEvent.Continue)

            assertTrue(subject.viewState.value.isSheetVisible)
        }

    @Test
    fun `given the sheet is shown when dismissing it then it closes without completing the step`() =
        coroutineRule.runTest {
            val subject = checkedViewModel()
            subject.setEvent(PlatformAuthSetupEvent.Continue)

            subject.effect.runFlowTest {
                subject.setEvent(PlatformAuthSetupEvent.DismissSheet)

                assertFalse(subject.viewState.value.isSheetVisible)
                expectNoEvents()
            }
        }

    @Test
    fun `given the sheet is shown when confirming it then the step completes`() =
        coroutineRule.runTest {
            val subject = checkedViewModel()
            subject.setEvent(PlatformAuthSetupEvent.Continue)

            subject.effect.runFlowTest {
                subject.setEvent(PlatformAuthSetupEvent.ConfirmSheet)

                assertEquals(PlatformAuthSetupEffect.Complete, awaitItem())
            }
            assertFalse(subject.viewState.value.isSheetVisible)
        }

    @Test
    fun `given the sheet is not shown when confirming then the step does not complete`() =
        coroutineRule.runTest {
            val subject = checkedViewModel()

            subject.effect.runFlowTest {
                subject.setEvent(PlatformAuthSetupEvent.ConfirmSheet)

                expectNoEvents()
            }
        }

    private fun checkedViewModel(): PlatformAuthSetupViewModel {
        val subject = PlatformAuthSetupViewModel()
        advance(CHECKING_DURATION_MILLIS)
        return subject
    }

    private fun advance(millis: Long) {
        with(coroutineRule.testDispatcher.scheduler) {
            advanceTimeBy(millis)
            runCurrent()
        }
    }
}
