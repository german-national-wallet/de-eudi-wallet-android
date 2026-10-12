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

package org.sprind.wallet.onboardingfeature.setup.interactor

import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.sprind.wallet.onboardingfeature.setup.model.SetupStep
import org.sprind.wallet.onboardingfeature.setup.provider.SetupStorageProvider
import org.sprind.wallet.uilogic.navigation.NavigationGuard
import org.sprind.wallet.uilogic.navigation.getDirection

interface SetupInteractor {
    /** Persists [step] as completed and returns the route to continue with. */
    fun completeStep(step: SetupStep): String
}

class SetupInteractorImpl(
    private val setupStorageProvider: SetupStorageProvider,
    private val navigationGuards: List<NavigationGuard>,
) : SetupInteractor {
    override fun completeStep(step: SetupStep): String {
        setupStorageProvider.storeStepCompleted(step = step, value = true)
        return navigationGuards.getDirection(destination = ModuleRoute.DashboardModule.route)
    }
}
