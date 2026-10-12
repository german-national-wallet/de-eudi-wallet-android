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

package org.sprind.wallet.onboardingfeature.setup.model

import eu.europa.ec.uilogic.navigation.Screen
import eu.europa.ec.uilogic.navigation.SetupScreens
import org.sprind.wallet.uilogic.navigation.NavigationGuardPriority

/**
 * The app setup steps shown after onboarding. The wallet revocation step sits between
 * [NOTIFICATIONS] and [SUCCESS] but is owned by the revocation feature.
 *
 * @property screen the screen the step is shown on.
 * @property priority the priority of the step's navigation guard; defines the step order.
 * @property storageKey the preference key persisting the step's completion. Never change it, or
 *   users who already completed the step are sent through it again.
 */
enum class SetupStep(
    val screen: Screen,
    val priority: Int,
    val storageKey: String,
) {
    PLATFORM_AUTH(
        screen = SetupScreens.PlatformAuth,
        priority = NavigationGuardPriority.SETUP_PLATFORM_AUTH,
        storageKey = "WalletSetupPlatformAuthCompleted",
    ),
    PRIVACY_POLICY(
        screen = SetupScreens.PrivacyPolicy,
        priority = NavigationGuardPriority.SETUP_PRIVACY_POLICY,
        storageKey = "WalletSetupPrivacyPolicyCompleted",
    ),
    NOTIFICATIONS(
        screen = SetupScreens.Notifications,
        priority = NavigationGuardPriority.SETUP_NOTIFICATIONS,
        storageKey = "WalletSetupNotificationsCompleted",
    ),
    SUCCESS(
        screen = SetupScreens.Success,
        priority = NavigationGuardPriority.SETUP_SUCCESS,
        storageKey = "WalletSetupSuccessCompleted",
    ),
}
