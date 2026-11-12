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

package org.sprind.wallet.onboardingfeature.setup.privacypolicy

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sprind.wallet.businesslogic.config.LocalizedUrl
import org.sprind.wallet.businesslogic.config.SettingsLinks
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyPolicySetupViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val configLogic: ConfigLogic = mock()
    private val resourceProvider: ResourceProvider = mock()

    private lateinit var subject: PrivacyPolicySetupViewModel

    @Before
    fun setUp() {
        whenever(configLogic.settingsLinks).thenReturn(
            SettingsLinks(privacyPolicy = PRIVACY_POLICY, termsAndConditions = TERMS),
        )
        whenever(resourceProvider.getLocale()).thenReturn(Locale.ENGLISH)
        subject = PrivacyPolicySetupViewModel(configLogic, resourceProvider)
    }

    @Test
    fun `when the step opens then the terms are not accepted`() {
        assertFalse(subject.viewState.value.isAccepted)
    }

    @Test
    fun `when the checkbox is toggled then the acceptance follows it`() {
        subject.setEvent(PrivacyPolicySetupEvent.SetAccepted(isAccepted = true))
        assertTrue(subject.viewState.value.isAccepted)

        subject.setEvent(PrivacyPolicySetupEvent.SetAccepted(isAccepted = false))
        assertFalse(subject.viewState.value.isAccepted)
    }

    @Test
    fun `given the terms are not accepted when continuing then the step is not completed`() =
        coroutineRule.runTest {
            subject.effect.runFlowTest {
                subject.setEvent(PrivacyPolicySetupEvent.Continue)

                expectNoEvents()
            }
        }

    @Test
    fun `given the terms are accepted when continuing then the step is completed`() =
        coroutineRule.runTest {
            subject.setEvent(PrivacyPolicySetupEvent.SetAccepted(isAccepted = true))

            subject.effect.runFlowTest {
                subject.setEvent(PrivacyPolicySetupEvent.Continue)

                assertEquals(PrivacyPolicySetupEffect.Complete, awaitItem())
            }
        }

    @Test
    fun `when reading the privacy policy then it opens in the web view`() =
        coroutineRule.runTest {
            subject.effect.runFlowTest {
                subject.setEvent(PrivacyPolicySetupEvent.OpenPrivacyPolicy)

                assertEquals(PrivacyPolicySetupEffect.OpenWebPage(PRIVACY_POLICY_ROUTE), awaitItem())
            }
        }

    @Test
    fun `when opening the terms of use then they open in the web view without accepting them`() =
        coroutineRule.runTest {
            subject.effect.runFlowTest {
                subject.setEvent(PrivacyPolicySetupEvent.OpenTermsOfUse)

                assertEquals(
                    PrivacyPolicySetupEffect.OpenWebPage(
                        SetupWebPageRoute(url = TERMS.en, titleRes = R.string.settings_overview_list_terms_and_conditions),
                    ),
                    awaitItem(),
                )
            }
            assertFalse(subject.viewState.value.isAccepted)
        }

    @Test
    fun `given a german locale when reading the privacy then the german page opens`() =
        coroutineRule.runTest {
            whenever(resourceProvider.getLocale()).thenReturn(Locale.GERMANY)

            subject.effect.runFlowTest {
                subject.setEvent(PrivacyPolicySetupEvent.OpenPrivacyPolicy)

                assertEquals(
                    PrivacyPolicySetupEffect.OpenWebPage(
                        SetupWebPageRoute(url = PRIVACY_POLICY.de, titleRes = R.string.settings_overview_list_privacy_policy),
                    ),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `given a german locale when opening the terms then the german page opens`() =
        coroutineRule.runTest {
            whenever(resourceProvider.getLocale()).thenReturn(Locale.GERMANY)

            subject.effect.runFlowTest {
                subject.setEvent(PrivacyPolicySetupEvent.OpenTermsOfUse)

                assertEquals(
                    PrivacyPolicySetupEffect.OpenWebPage(
                        SetupWebPageRoute(url = TERMS.de, titleRes = R.string.settings_overview_list_terms_and_conditions),
                    ),
                    awaitItem(),
                )
            }
        }

    private companion object {
        val PRIVACY_POLICY = LocalizedUrl(en = "https://example.org/privacy", de = "https://example.org/de/privacy")
        val TERMS = LocalizedUrl(en = "https://example.org/terms", de = "https://example.org/de/terms")
        val PRIVACY_POLICY_ROUTE = SetupWebPageRoute(
            url = PRIVACY_POLICY.en,
            titleRes = R.string.settings_overview_list_privacy_policy,
        )
    }
}
