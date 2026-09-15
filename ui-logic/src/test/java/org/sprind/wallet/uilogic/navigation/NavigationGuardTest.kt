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

import org.junit.Test
import kotlin.test.assertEquals

class NavigationGuardTest {

    private val dashboardRoute = "dashboard"
    private val onboardingIntroRoute = "onboardingIntro"
    private val revocationRoute = "revocation"
    private val helpRoute = "revocation"

    val activeOnboardingIntroGuard = object : NavigationGuard {
        override val priority = 60
        override val destination = dashboardRoute
        override fun getDirection() = onboardingIntroRoute
    }

    val inactiveOnboardingIntroGuard = object : NavigationGuard {
        override val priority = 60
        override val destination = dashboardRoute
        override fun getDirection() = destination
    }

    val activeRevocationGuard = object : NavigationGuard {
        override val destination = dashboardRoute
        override fun getDirection() = revocationRoute
    }

    @Test
    fun `when getting direction for guarded destination then guards point to intermediate route`() {
        val guards = listOf(activeRevocationGuard)
        assertEquals(revocationRoute, guards.getDirection(dashboardRoute))
    }

    @Test
    fun `when getting direction for unguarded destination then guards point to destination route`() {
        val guards = listOf(activeRevocationGuard)
        assertEquals(helpRoute, guards.getDirection(helpRoute))
    }

    @Test
    fun `given multiple guards when getting direction for guarded destination then guard with the highest priority takes precedence`() {
        // revocation guard added to both ends to properly test that onboarding intro takes the priority
        val guards = listOf(activeRevocationGuard, activeOnboardingIntroGuard, activeRevocationGuard)

        assertEquals(onboardingIntroRoute, guards.getDirection(dashboardRoute))
    }

    @Test
    fun `given multiple (in)active guards when getting direction for guarded destination then all guards are considered`() {
        // inactive guard added to both ends to properly test that the first active guard takes the priority
        val guards = listOf(inactiveOnboardingIntroGuard, activeOnboardingIntroGuard, inactiveOnboardingIntroGuard)

        assertEquals(onboardingIntroRoute, guards.getDirection(dashboardRoute))
    }
}