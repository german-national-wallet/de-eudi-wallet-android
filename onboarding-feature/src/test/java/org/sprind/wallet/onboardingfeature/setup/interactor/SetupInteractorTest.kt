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
import eu.europa.ec.uilogic.navigation.SetupScreens
import org.junit.Test
import org.sprind.wallet.onboardingfeature.setup.model.SetupStep
import org.sprind.wallet.onboardingfeature.setup.provider.SetupStorageProvider
import org.sprind.wallet.uilogic.navigation.NavigationGuard
import org.sprind.wallet.uilogic.navigation.NavigationGuardPriority
import org.sprind.wallet.uilogic.navigation.getDirection
import org.sprind.wallet.onboardingfeature.setup.provider.SetupFlowAvailability
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupInteractorTest {

    private val dashboard = ModuleRoute.DashboardModule.route

    private val storage = FakeSetupStorageProvider()

    private var setupEnabled = true
    private val availability = SetupFlowAvailability { setupEnabled }

    /** Stands in for the revocation feature's guard, which sits between notifications and success. */
    private var revocationConfirmed = false
    private val revocationGuard = object : NavigationGuard {
        override val priority = NavigationGuardPriority.REVOCATION
        override val destination = dashboard
        override fun getDirection() =
            if (revocationConfirmed) destination else ModuleRoute.RevocationModule.route
    }

    private val guards: List<NavigationGuard> = listOf(
        SuccessSetupGuard(storage, availability),
        revocationGuard,
        NotificationsSetupGuard(storage, availability),
        PrivacyPolicySetupGuard(storage, availability),
        PlatformAuthSetupGuard(storage, availability),
    )

    private val subject = SetupInteractorImpl(storage, guards)

    @Test
    fun `given nothing completed when entering the app then the platform auth step is shown first`() {
        assertEquals(SetupScreens.PlatformAuth.screenRoute, guards.getDirection(dashboard))
    }

    @Test
    fun `when completing each step then the steps follow the design order and end on the dashboard`() {
        assertEquals(
            SetupScreens.PrivacyPolicy.screenRoute,
            subject.completeStep(SetupStep.PLATFORM_AUTH)
        )
        assertEquals(
            SetupScreens.Notifications.screenRoute,
            subject.completeStep(SetupStep.PRIVACY_POLICY)
        )
        assertEquals(
            ModuleRoute.RevocationModule.route,
            subject.completeStep(SetupStep.NOTIFICATIONS)
        )

        revocationConfirmed = true
        assertEquals(SetupScreens.Success.screenRoute, guards.getDirection(dashboard))

        assertEquals(dashboard, subject.completeStep(SetupStep.SUCCESS))
    }

    @Test
    fun `when completing a step then it is persisted`() {
        subject.completeStep(SetupStep.PRIVACY_POLICY)

        assertTrue(storage.isStepCompleted(SetupStep.PRIVACY_POLICY))
    }

    @Test
    fun `given a completed step when getting its guard direction then it points to the dashboard`() {
        storage.storeStepCompleted(SetupStep.PLATFORM_AUTH, true)

        assertEquals(dashboard, PlatformAuthSetupGuard(storage, availability).getDirection())
    }

    @Test
    fun `setup guard priorities keep the setup order around onboarding and revocation`() {
        val ordered = listOf(
            NavigationGuardPriority.ONBOARDING,
            SetupStep.PLATFORM_AUTH.priority,
            SetupStep.PRIVACY_POLICY.priority,
            SetupStep.NOTIFICATIONS.priority,
            NavigationGuardPriority.REVOCATION,
            SetupStep.SUCCESS.priority,
        )
        assertEquals(ordered.sortedDescending(), ordered)
        assertEquals(ordered.size, ordered.toSet().size)
    }

    @Test
    fun `setup steps are guarded on their own screens`() {
        SetupStep.entries.forEach { step ->
            val guard = guards.filterIsInstance<SetupStepGuard>().single { it.priority == step.priority }
            assertEquals(step.screen.screenRoute, guard.getDirection())
        }
    }

    @Test
    fun `given the setup flow is disabled when entering the app then every setup step is skipped`() {
        setupEnabled = false

        guards.filterIsInstance<SetupStepGuard>().forEach { guard ->
            assertEquals(dashboard, guard.getDirection())
        }
        // Only the other features' guards remain, e.g. revocation.
        assertEquals(ModuleRoute.RevocationModule.route, guards.getDirection(dashboard))

        revocationConfirmed = true
        assertEquals(dashboard, guards.getDirection(dashboard))
        SetupStep.entries.forEach { assertFalse(storage.isStepCompleted(it)) }
    }

    @Test
    fun `given the setup flow is enabled later then the pending steps are shown`() {
        setupEnabled = false
        revocationConfirmed = true
        assertEquals(dashboard, guards.getDirection(dashboard))

        setupEnabled = true
        assertEquals(SetupScreens.PlatformAuth.screenRoute, guards.getDirection(dashboard))
    }

    private class FakeSetupStorageProvider : SetupStorageProvider {
        private val completed = mutableSetOf<SetupStep>()

        override fun isStepCompleted(step: SetupStep) = step in completed

        override fun storeStepCompleted(step: SetupStep, value: Boolean) {
            if (value) completed += step else completed -= step
        }
    }
}
