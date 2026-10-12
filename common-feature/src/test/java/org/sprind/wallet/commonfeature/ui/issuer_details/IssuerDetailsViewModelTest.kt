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

package org.sprind.wallet.commonfeature.ui.issuer_details

import androidx.core.net.toUri
import eu.europa.ec.commonfeature.model.DocumentType
import eu.europa.ec.commonfeature.ui.issuer_details.Effect
import eu.europa.ec.commonfeature.ui.issuer_details.Event
import eu.europa.ec.commonfeature.ui.issuer_details.IssuerDetailsViewModel
import eu.europa.ec.commonfeature.ui.issuer_details.model.IssuerInfo
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.testlogic.extension.expectNoEvents
import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class)
class IssuerDetailsViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Test
    fun `when PrivacyPolicyPressed, then the privacy policy link is opened`() =
        assertPrivacyPolicyOpens(privacyPolicy = PRIVACY_POLICY_URL)

    @Test
    fun `when PrivacyPolicyPressed with a link without scheme, then it is opened over https`() =
        assertPrivacyPolicyOpens(
            privacyPolicy = PRIVACY_POLICY_WITHOUT_SCHEME,
        )

    @Test
    fun `when PrivacyPolicyPressed without a privacy policy, then nothing is opened`() =
        coroutineRule.runTest {
            val subject = createViewModel(privacyPolicy = " ")

            subject.setEvent(Event.PrivacyPolicyPressed)

            subject.effect.expectNoEvents()
        }

    private fun assertPrivacyPolicyOpens(privacyPolicy: String) = coroutineRule.runTest {
        val subject = createViewModel(privacyPolicy = privacyPolicy)

        subject.setEvent(Event.PrivacyPolicyPressed)

        subject.effect.runFlowTest {
            assertEquals(Effect.Navigation.OpenLink(PRIVACY_POLICY_URL.toUri()), awaitItem())
        }
    }

    private fun createViewModel(privacyPolicy: String) = IssuerDetailsViewModel(
        issuerInfo = IssuerInfo(
            issuerName = "University XY",
            documentType = DocumentType.EAA,
            privacyPolicy = privacyPolicy,
        )
    )

    private companion object {
        const val PRIVACY_POLICY_WITHOUT_SCHEME = "university-xy.de/privacy"
        const val PRIVACY_POLICY_URL = "https://$PRIVACY_POLICY_WITHOUT_SCHEME"
    }
}
