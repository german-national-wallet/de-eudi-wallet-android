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
import org.sprind.wallet.onboardingfeature.setup.provider.SetupFlowAvailability
import org.sprind.wallet.onboardingfeature.setup.provider.SetupStorageProvider
import org.sprind.wallet.uilogic.navigation.NavigationGuard

/**
 * Guards the dashboard until [step] is completed, pointing to the step's screen instead.
 * While [setupFlowAvailability] is disabled the step is skipped and the guard points to the
 * dashboard.
 *
 * Each step has its own subclass so that every guard is a distinct DI definition bound to
 * [NavigationGuard].
 */
sealed class SetupStepGuard(
    private val step: SetupStep,
    private val setupStorageProvider: SetupStorageProvider,
    private val setupFlowAvailability: SetupFlowAvailability,
) : NavigationGuard {
    override val priority: Int
        get() = step.priority

    override val destination: String
        get() = ModuleRoute.DashboardModule.route

    override fun getDirection(): String =
        if (!setupFlowAvailability.isEnabled() || setupStorageProvider.isStepCompleted(step)) {
            destination
        } else {
            step.screen.screenRoute
        }
}

class PlatformAuthSetupGuard(
    setupStorageProvider: SetupStorageProvider,
    setupFlowAvailability: SetupFlowAvailability,
) : SetupStepGuard(SetupStep.PLATFORM_AUTH, setupStorageProvider, setupFlowAvailability)

class PrivacyPolicySetupGuard(
    setupStorageProvider: SetupStorageProvider,
    setupFlowAvailability: SetupFlowAvailability,
) : SetupStepGuard(SetupStep.PRIVACY_POLICY, setupStorageProvider, setupFlowAvailability)

class NotificationsSetupGuard(
    setupStorageProvider: SetupStorageProvider,
    setupFlowAvailability: SetupFlowAvailability,
) : SetupStepGuard(SetupStep.NOTIFICATIONS, setupStorageProvider, setupFlowAvailability)

class SuccessSetupGuard(
    setupStorageProvider: SetupStorageProvider,
    setupFlowAvailability: SetupFlowAvailability,
) : SetupStepGuard(SetupStep.SUCCESS, setupStorageProvider, setupFlowAvailability)
