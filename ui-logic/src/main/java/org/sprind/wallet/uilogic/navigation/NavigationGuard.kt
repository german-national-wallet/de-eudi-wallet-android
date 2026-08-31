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
 * Consult a [NavigationGuard] for proper direction to the destination.
 * This is used to show for example onboarding flows before showing the dashboard.
 * Destination and direction are loosely typed as [String]
 * to allow passing [eu.europa.ec.uilogic.navigation.Screen.screenRoute]s,
 * [eu.europa.ec.uilogic.navigation.ModuleRoute.route], etc.
 *
 * @property priority To resolve priority if multiple [NavigationGuard]s guard the same destination.
 * Higher values are prioritized.
 * @property destination The destination this guard is guiding towards.
 */
interface NavigationGuard {
    val priority: Int
        get() = 50

    val destination: String

    /**
     * The direction that should be followed.
     * @return [String] the route pointing into the right direction.
     */
    fun getDirection(): String
}

/**
 * Consult multiple [NavigationGuard]s for direction.
 *
 * Consults all guards with matching destination for direction.
 * Priority is resolved by individual priority values (higher values are prioritized).
 * If the first matching guard points directly to the destination the next guard is consulted.
 * Till all matching guards point to the destination.
 *
 * @param destination The destination to reach at the end.
 * @return [String]
 */
fun List<NavigationGuard>.getDirection(destination: String): String {
    return sortedByDescending { it.priority }
        .firstOrNull { it.destination == destination && it.getDirection() != destination }
        ?.getDirection() ?: destination
}