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

package org.sprind.wallet.onboardingfeature.setup.notifications

import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class NotificationConsentViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Test
    fun `when consent is granted then it asks the screen to request the permission`() =
        coroutineRule.runTest {
            val subject = NotificationConsentViewModel()

            subject.effect.runFlowTest {
                subject.setEvent(NotificationConsentEvent(NotificationConsentEvent.Event.GrantConsent))

                assertEquals(
                    NotificationConsentEffect(NotificationConsentEffect.Effect.GrantConsent),
                    awaitItem(),
                )
            }
        }
}
