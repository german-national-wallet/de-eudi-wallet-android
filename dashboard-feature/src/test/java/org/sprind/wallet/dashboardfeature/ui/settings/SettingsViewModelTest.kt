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

import androidx.core.net.toUri
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.expectNoEvents
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.navigation.RevocationScreens
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.sprind.wallet.businesslogic.config.SettingsLinks
import kotlin.test.assertEquals
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
        whenever(configLogic.settingsLinks).thenReturn(links)
        whenever(configLogic.appVersion).thenReturn(APP_VERSION)
        whenever(configLogic.appVersionCode).thenReturn(APP_VERSION_CODE)
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    //region menu

    @Test
    fun `when the screen opens, then the menu carries the entries the design lists`() =
        coroutineRule.runTest {
            val entries = subject.viewState.value.menuGroups.flatMap { it.entries }

            assertEquals(
                listOf(
                    SettingsMenuEntry.FAQ_AND_SUPPORT,
                    SettingsMenuEntry.WALLET_REVOCATION,
                    SettingsMenuEntry.NOTIFICATIONS,
                    SettingsMenuEntry.ABOUT,
                    SettingsMenuEntry.ACCESSIBILITY_DECLARATION,
                    SettingsMenuEntry.PRIVACY_POLICY,
                    SettingsMenuEntry.TERMS_AND_CONDITIONS,
                    SettingsMenuEntry.IMPRINT,
                ),
                entries,
            )
        }

    @Test
    fun `when the flavor carries a debug menu and writes log files, then the log export is listed`() =
        coroutineRule.runTest {
            whenever(configLogic.isDebugMenuEnabled).thenReturn(true)
            whenever(configLogic.isLogWriterEnabled).thenReturn(true)

            assertTrue(subject.hasLogExportEntry())
        }

    @Test
    fun `when the flavor carries no debug menu, then the log export is not listed`() =
        coroutineRule.runTest {
            whenever(configLogic.isDebugMenuEnabled).thenReturn(false)
            whenever(configLogic.isLogWriterEnabled).thenReturn(true)

            assertFalse(subject.hasLogExportEntry())
        }

    @Test
    fun `when the flavor writes no log files, then the log export is not listed`() =
        coroutineRule.runTest {
            whenever(configLogic.isDebugMenuEnabled).thenReturn(true)
            whenever(configLogic.isLogWriterEnabled).thenReturn(false)

            assertFalse(subject.hasLogExportEntry())
        }

    @Test
    fun `when the screen opens, then it names the build it is running`() =
        coroutineRule.runTest {
            val state = subject.viewState.value

            assertEquals(APP_VERSION, state.appVersion)
            assertEquals(APP_VERSION_CODE, state.appVersionCode)
        }

    //endregion

    //region web pages

    @Test
    fun `when the support entry is tapped, then the support page opens in the app`() =
        coroutineRule.runTest {
            assertOpensWebPage(
                entry = SettingsMenuEntry.FAQ_AND_SUPPORT,
                expectedUrl = links.faqAndSupport,
            )
        }

    @Test
    fun `when the accessibility entry is tapped, then the declaration opens in the app`() =
        coroutineRule.runTest {
            assertOpensWebPage(
                entry = SettingsMenuEntry.ACCESSIBILITY_DECLARATION,
                expectedUrl = links.accessibilityDeclaration,
            )
        }

    @Test
    fun `when the about entry is tapped, then the about page opens in the app`() =
        coroutineRule.runTest {
            assertOpensWebPage(
                entry = SettingsMenuEntry.ABOUT,
                expectedUrl = links.about,
            )
        }

    @Test
    fun `when the privacy policy entry is tapped, then the policy opens in the app`() =
        coroutineRule.runTest {
            assertOpensWebPage(
                entry = SettingsMenuEntry.PRIVACY_POLICY,
                expectedUrl = links.privacyPolicy,
            )
        }

    @Test
    fun `when the terms entry is tapped, then the terms open in the app`() =
        coroutineRule.runTest {
            assertOpensWebPage(
                entry = SettingsMenuEntry.TERMS_AND_CONDITIONS,
                expectedUrl = links.termsAndConditions,
            )
        }

    @Test
    fun `when the imprint entry is tapped, then the imprint opens in the app`() =
        coroutineRule.runTest {
            assertOpensWebPage(
                entry = SettingsMenuEntry.IMPRINT,
                expectedUrl = links.imprint,
            )
        }

    //endregion

    //region other destinations

    @Test
    fun `when the revocation entry is tapped, then the wallet key screen opens`() =
        coroutineRule.runTest {
            subject.setEvent(Event.MenuEntryClicked(SettingsMenuEntry.WALLET_REVOCATION))

            subject.effect.runFlowTest {
                assertEquals(
                    Effect.Navigation.SwitchScreen(RevocationScreens.KeyInfo.screenRoute),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `when the notification entry is tapped, then the system settings are opened`() =
        coroutineRule.runTest {
            subject.setEvent(Event.MenuEntryClicked(SettingsMenuEntry.NOTIFICATIONS))

            subject.effect.runFlowTest {
                assertEquals(Effect.OpenNotificationSettings, awaitItem())
            }
        }

    //endregion

    //region log export

    @Test
    fun `when the log export is tapped and available, then the log files are shared`() =
        coroutineRule.runTest {
            whenever(configLogic.isDebugMenuEnabled).thenReturn(true)
            whenever(configLogic.isLogWriterEnabled).thenReturn(true)
            val fileUris = arrayListOf("content://logs/wallet.log".toUri())
            whenever(dashboardInteractor.retrieveLogFileUris()).thenReturn(fileUris)

            subject.setEvent(Event.MenuEntryClicked(SettingsMenuEntry.EXPORT_LOGS))

            subject.effect.runFlowTest {
                assertEquals(Effect.ShareLogs(fileUris), awaitItem())
            }
        }

    @Test
    fun `when the log export is tapped but unavailable, then no log file is read`() =
        coroutineRule.runTest {
            subject.setEvent(Event.MenuEntryClicked(SettingsMenuEntry.EXPORT_LOGS))

            subject.effect.expectNoEvents()
            verify(dashboardInteractor, never()).retrieveLogFileUris()
        }

    //endregion

    private fun SettingsViewModel.hasLogExportEntry(): Boolean =
        viewState.value.menuGroups
            .flatMap { it.entries }
            .contains(SettingsMenuEntry.EXPORT_LOGS)

    private suspend fun assertOpensWebPage(entry: SettingsMenuEntry, expectedUrl: String) {
        subject.setEvent(Event.MenuEntryClicked(entry))

        subject.effect.runFlowTest {
            assertEquals(
                Effect.Navigation.OpenWebPage(
                    SettingsWebPageRoute(url = expectedUrl, titleRes = entry.labelRes)
                ),
                awaitItem(),
            )
        }
    }

    private companion object {
        const val APP_VERSION = "0.52.0-Dev"
        const val APP_VERSION_CODE = 875

        val links = SettingsLinks(
            faqAndSupport = "https://example.org/support",
            accessibilityDeclaration = "https://example.org/accessibility",
            about = "https://example.org/about",
            privacyPolicy = "https://example.org/privacy",
            termsAndConditions = "https://example.org/terms",
            imprint = "https://example.org/imprint",
        )
    }
}