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

/**
 * Guards the dashboard until [step] is completed, pointing to the step's screen instead.
 *
 * Each step has its own subclass so that every guard is a distinct DI definition bound to
 * [NavigationGuard].
 */
sealed class SetupStepGuard(
    private val step: SetupStep,
    private val setupStorageProvider: SetupStorageProvider,
) : NavigationGuard {
    override val priority: Int
        get() = step.priority

    override val destination: String
        get() = ModuleRoute.DashboardModule.route

    override fun getDirection(): String =
        if (setupStorageProvider.isStepCompleted(step)) {
            destination
        } else {
            step.screen.screenRoute
        }
}

class PlatformAuthSetupGuard(
    setupStorageProvider: SetupStorageProvider,
) : SetupStepGuard(SetupStep.PLATFORM_AUTH, setupStorageProvider)

class PrivacyPolicySetupGuard(
    setupStorageProvider: SetupStorageProvider,
) : SetupStepGuard(SetupStep.PRIVACY_POLICY, setupStorageProvider)

class NotificationsSetupGuard(
    setupStorageProvider: SetupStorageProvider,
) : SetupStepGuard(SetupStep.NOTIFICATIONS, setupStorageProvider)

class SuccessSetupGuard(
    setupStorageProvider: SetupStorageProvider,
) : SetupStepGuard(SetupStep.SUCCESS, setupStorageProvider)
