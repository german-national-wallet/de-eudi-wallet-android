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

package org.sprind.wallet.revocationfeature.ui.keyinfo

import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Rule
import org.junit.Test
import org.sprind.wallet.revocationfeature.interactor.RevocationInteractor
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RevocationKeyInfoViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Test
    fun `before the code is read, then nothing is shown`() = coroutineRule.runTest {
        val subject = RevocationKeyInfoViewModel(FakeRevocationInteractor(code = CODE))

        assertNull(subject.viewState.value.revocationCode)
    }

    @Test
    fun `when the screen opens, then the code is shown in groups of four`() =
        coroutineRule.runTest {
            val subject = RevocationKeyInfoViewModel(FakeRevocationInteractor(code = CODE))

            subject.viewState.runFlowTest {
                subject.setEvent(Event.Init)

                assertNull(awaitItem().revocationCode)
                assertEquals(GROUPED_CODE, awaitItem().revocationCode)
            }
        }

    @Test
    fun `when the last group is short, then it is left as it is`() = coroutineRule.runTest {
        val subject = RevocationKeyInfoViewModel(FakeRevocationInteractor(code = "rev1dl5rvz"))

        subject.viewState.runFlowTest {
            subject.setEvent(Event.Init)

            assertNull(awaitItem().revocationCode)
            assertEquals("rev1 dl5r vz", awaitItem().revocationCode)
        }
    }

    @Test
    fun `when the wallet has no code, then nothing is shown`() = coroutineRule.runTest {
        val interactor = FakeRevocationInteractor(code = null)
        val subject = RevocationKeyInfoViewModel(interactor)

        subject.viewState.runFlowTest {
            subject.setEvent(Event.Init)

            assertNull(awaitItem().revocationCode)
        }

        // The screen asked for the code; it is the wallet that has none to give.
        assertTrue(interactor.codeRequested)
    }

    private class FakeRevocationInteractor(
        private val code: String?,
    ) : RevocationInteractor {

        var codeRequested = false
            private set

        override suspend fun getRevocationCode(): String? {
            codeRequested = true
            return code
        }

        override fun hasUserConfirmedSavingCode(): Boolean = true

        override fun storeUserConfirmedSavingCode(value: Boolean) = Unit
    }

    private companion object {
        const val CODE = "rev1dl5rvzak5x72"
        const val GROUPED_CODE = "rev1 dl5r vzak 5x72"
    }
}
