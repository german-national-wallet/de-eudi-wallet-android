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

package org.sprind.wallet.dashboardfeature.ui.settings

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class SettingsViewModelTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var dashboardInteractor: DashboardInteractor

    @Mock
    private lateinit var configLogic: ConfigLogic

    private lateinit var closeable: AutoCloseable

    val subject by lazy {
        SettingsViewModel(
            dashboardInteractor = dashboardInteractor,
            configLogic = configLogic,
        )
    }

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(configLogic.isDebugMenuEnabled).thenReturn(false)
        whenever(configLogic.isLogWriterEnabled).thenReturn(false)
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    //region debug menu

    @Test
    fun `when the flavor carries a debug menu, then isDebugMenuEnabled is true`() =
        coroutineRule.runTest {
            whenever(configLogic.isDebugMenuEnabled).thenReturn(true)

            assertTrue(subject.viewState.value.isDebugMenuEnabled)
        }

    @Test
    fun `when the flavor carries no debug menu, then isDebugMenuEnabled is false`() =
        coroutineRule.runTest {
            whenever(configLogic.isDebugMenuEnabled).thenReturn(false)

            assertFalse(subject.viewState.value.isDebugMenuEnabled)
        }

    @Test
    fun `when the flavor writes log files, then isLogWriterEnabled is true`() =
        coroutineRule.runTest {
            whenever(configLogic.isLogWriterEnabled).thenReturn(true)

            assertTrue(subject.viewState.value.isLogWriterEnabled)
        }

    @Test
    fun `when the flavor writes no log files, then isLogWriterEnabled is false`() =
        coroutineRule.runTest {
            whenever(configLogic.isLogWriterEnabled).thenReturn(false)

            assertFalse(subject.viewState.value.isLogWriterEnabled)
        }

    //endregion
}
