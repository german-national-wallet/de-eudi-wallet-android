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

package org.sprind.wallet.onboardingfeature.setup.ui

import eu.europa.ec.testlogic.extension.runFlowTest
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.SetupScreens
import org.junit.Rule
import org.junit.Test
import org.sprind.wallet.onboardingfeature.setup.interactor.SetupInteractor
import org.sprind.wallet.onboardingfeature.setup.model.SetupStep
import kotlin.test.assertEquals

class SetupStepViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Test
    fun `when completing a step then it navigates to the next step keeping the back stack`() =
        coroutineRule.runTest {
            val interactor = FakeSetupInteractor(nextRoute = SetupScreens.PrivacyPolicy.screenRoute)
            val subject = SetupStepViewModel(interactor)

            subject.effect.runFlowTest {
                subject.setEvent(SetupStepEvent.Complete(SetupStep.PLATFORM_AUTH))

                assertEquals(
                    SetupStepEffect.Navigation.SwitchScreen(
                        route = SetupScreens.PrivacyPolicy.screenRoute,
                        clearBackStack = false,
                    ),
                    awaitItem(),
                )
            }
            assertEquals(listOf(SetupStep.PLATFORM_AUTH), interactor.completed)
        }

    @Test
    fun `when completing the last step then it navigates to the dashboard clearing the back stack`() =
        coroutineRule.runTest {
            val interactor = FakeSetupInteractor(nextRoute = ModuleRoute.DashboardModule.route)
            val subject = SetupStepViewModel(interactor)

            subject.effect.runFlowTest {
                subject.setEvent(SetupStepEvent.Complete(SetupStep.SUCCESS))

                assertEquals(
                    SetupStepEffect.Navigation.SwitchScreen(
                        route = ModuleRoute.DashboardModule.route,
                        clearBackStack = true,
                    ),
                    awaitItem(),
                )
            }
        }

    private class FakeSetupInteractor(private val nextRoute: String) : SetupInteractor {
        val completed = mutableListOf<SetupStep>()

        override fun completeStep(step: SetupStep): String {
            completed += step
            return nextRoute
        }
    }
}
