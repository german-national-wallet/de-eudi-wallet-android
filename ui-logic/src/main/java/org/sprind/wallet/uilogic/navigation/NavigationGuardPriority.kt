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

package org.sprind.wallet.uilogic.navigation

/**
 * Priorities of the [NavigationGuard]s that guard the dashboard on first launch.
 *
 * Higher values are consulted first, so the declaration order below is the order in which the
 * user walks through onboarding and app setup. Keep them in one place so inserting a step does not
 * require renumbering guards spread over several feature modules.
 */
object NavigationGuardPriority {
    const val ONBOARDING = 60
    const val SETUP_PLATFORM_AUTH = 59
    const val SETUP_PRIVACY_POLICY = 58
    const val SETUP_NOTIFICATIONS = 57
    const val REVOCATION = 56
    const val SETUP_SUCCESS = 55
}
