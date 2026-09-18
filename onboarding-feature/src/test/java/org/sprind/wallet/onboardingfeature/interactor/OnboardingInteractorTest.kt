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

package org.sprind.wallet.onboardingfeature.interactor

import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.junit.Test
import kotlin.test.assertEquals

class OnboardingInteractorTest {

    @Test
    fun `given user has not completed onboarding when getting direction then points to onboarding`() {
        val interactor = object : OnboardingInteractor {
            override fun hasUserCompletedOnboarding(): Boolean = false

            override fun storeUserCompletedOnboarding(value: Boolean) = Unit
        }
        assertEquals(ModuleRoute.OnboardingModule.route, interactor.getDirection())
    }

    @Test
    fun `given user has completed onboarding when getting direction then points to dashboard`() {
        val interactor = object : OnboardingInteractor {
            override fun hasUserCompletedOnboarding(): Boolean = true

            override fun storeUserCompletedOnboarding(value: Boolean) = Unit
        }
        assertEquals(ModuleRoute.DashboardModule.route, interactor.getDirection())
    }
}