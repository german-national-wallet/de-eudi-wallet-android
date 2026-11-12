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
import eu.europa.ec.resourceslogic.provider.ResourceProvider
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
import org.sprind.wallet.businesslogic.config.LocalizedUrl
import org.sprind.wallet.businesslogic.config.SettingsLinks
import org.sprind.wallet.businesslogic.config.debugConfigAvailable
import java.util.Locale
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

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    private lateinit var closeable: AutoCloseable

    val subject by lazy {
        SettingsViewModel(
            dashboardInteractor = dashboardInteractor,
            configLogic = configLogic,
            resourceProvider = resourceProvider,
        )
    }

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(resourceProvider.getLocale()).thenReturn(Locale.ENGLISH)
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
                buildList {
                    addAll(
                        listOf(
                            SettingsMenuEntry.FAQ_AND_SUPPORT,
                            SettingsMenuEntry.WALLET_REVOCATION,
                            SettingsMenuEntry.NOTIFICATIONS,
                            SettingsMenuEntry.ABOUT,
                            SettingsMenuEntry.ACCESSIBILITY_DECLARATION,
                            SettingsMenuEntry.PRIVACY_POLICY,
                            SettingsMenuEntry.TERMS_AND_CONDITIONS,
                            SettingsMenuEntry.IMPRINT,
                        )
                    )
                    if (debugConfigAvailable) add(SettingsMenuEntry.DEBUG_CONFIG)
                },
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
    fun `when a web page entry is tapped, then its page opens in the app in the language of the app`() =
        coroutineRule.runTest {
            val pages = mapOf(
                SettingsMenuEntry.FAQ_AND_SUPPORT to links.faqAndSupport,
                SettingsMenuEntry.ABOUT to links.about,
                SettingsMenuEntry.ACCESSIBILITY_DECLARATION to links.accessibilityDeclaration,
                SettingsMenuEntry.PRIVACY_POLICY to links.privacyPolicy,
                SettingsMenuEntry.TERMS_AND_CONDITIONS to links.termsAndConditions,
                SettingsMenuEntry.IMPRINT to links.imprint,
            )
            val languages = mapOf(
                Locale.ENGLISH to LocalizedUrl::en,
                Locale.GERMANY to LocalizedUrl::de,
                Locale.FRENCH to LocalizedUrl::en,
            )

            languages.forEach { (locale, expectedFor) ->
                whenever(resourceProvider.getLocale()).thenReturn(locale)

                pages.forEach { (entry, page) ->
                    assertOpensWebPage(
                        entry = entry,
                        expectedUrl = expectedFor(page),
                        message = "$entry in $locale",
                    )
                }
            }
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

    private suspend fun assertOpensWebPage(
        entry: SettingsMenuEntry,
        expectedUrl: String,
        message: String? = null,
    ) {
        subject.setEvent(Event.MenuEntryClicked(entry))

        subject.effect.runFlowTest {
            assertEquals(
                Effect.Navigation.OpenWebPage(
                    SettingsWebPageRoute(url = expectedUrl, titleRes = entry.labelRes)
                ),
                awaitItem(),
                message,
            )
        }
    }

    private companion object {
        const val APP_VERSION = "0.52.0-Dev"
        const val APP_VERSION_CODE = 875

        val links = SettingsLinks(
            faqAndSupport = LocalizedUrl(en = "https://example.org/support", de = "https://example.org/de/support"),
            accessibilityDeclaration = LocalizedUrl(en = "https://example.org/accessibility", de = "https://example.org/de/accessibility"),
            about = LocalizedUrl(en = "https://example.org/about", de = "https://example.org/de/about"),
            privacyPolicy = LocalizedUrl(en = "https://example.org/privacy", de = "https://example.org/de/privacy"),
            termsAndConditions = LocalizedUrl(en = "https://example.org/terms", de = "https://example.org/de/terms"),
            imprint = LocalizedUrl(en = "https://example.org/imprint", de = "https://example.org/de/imprint"),
        )
    }
}