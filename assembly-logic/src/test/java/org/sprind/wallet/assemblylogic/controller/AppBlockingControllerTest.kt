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

package org.sprind.wallet.assemblylogic.controller

import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.config.EnvironmentConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.sprind.wallet.flags.FeatureFlagManager
import org.sprind.wallet.flags.FeatureFlagUpdateService
import org.sprind.wallet.flags.models.FeatureFlag
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppBlockingControllerTest {

    @Test
    fun `platform authentication has priority over minimum app version`() {
        val controller = controller(
            appVersion = "1.0.0",
            minimumAppVersion = "2.0.0",
            shouldShowBlockingScreen = true,
        )

        assertEquals(
            AppBlockingState.PlatformAuthentication,
            controller.blockingState(allRules),
        )
    }

    @Test
    fun `wallet revoked has priority over platform authentication and minimum app version`() {
        val controller = controller(
            appVersion = "1.0.0",
            minimumAppVersion = "2.0.0",
            shouldShowBlockingScreen = true,
            isRevoked = true,
        )

        assertEquals(
            AppBlockingState.WalletRevoked,
            controller.blockingState(allRules),
        )
    }

    @Test
    fun `default rule set evaluates the wallet revoked rule`() {
        val controller = controller(isRevoked = true)

        assertEquals(
            AppBlockingState.WalletRevoked,
            controller.blockingState(),
        )
    }

    @Test
    fun `a wallet that is not revoked falls through to the other rules`() {
        val controller = controller(
            appVersion = "1.0.0",
            minimumAppVersion = "2.0.0",
            isRevoked = false,
        )

        assertEquals(
            AppBlockingState.MinimumAppVersion,
            controller.blockingState(allRules),
        )
    }

    @Test
    fun `default rule set evaluates both platform authentication and minimum app version`() {
        val controller = controller(
            appVersion = "1.0.0",
            minimumAppVersion = "2.0.0",
            shouldShowBlockingScreen = false,
        )

        assertEquals(
            AppBlockingState.MinimumAppVersion,
            controller.blockingState(),
        )
    }

    @Test
    fun `minimum app version blocks when platform authentication is valid`() {
        val controller = controller(
            appVersion = "1.0.0",
            minimumAppVersion = "2.0.0",
            shouldShowBlockingScreen = false,
        )

        assertEquals(
            AppBlockingState.MinimumAppVersion,
            controller.blockingState(allRules),
        )
    }

    @Test
    fun `no blocker is returned when app version satisfies minimum version`() {
        val controller = controller(
            appVersion = "2.0.0",
            minimumAppVersion = "2.0.0",
            shouldShowBlockingScreen = false,
        )

        assertNull(controller.blockingState(allRules))
    }

    @Test
    fun `flavor-suffixed version equal to minimum is not blocked`() {
        val controller = controller(
            appVersion = "0.25.3-Dev",
            minimumAppVersion = "0.25.3",
            shouldShowBlockingScreen = false,
        )

        assertNull(controller.blockingState(allRules))
    }

    @Test
    fun `flavor-suffixed version below minimum is blocked`() {
        val controller = controller(
            appVersion = "0.25.2-Dev",
            minimumAppVersion = "0.25.3",
            shouldShowBlockingScreen = false,
        )

        assertEquals(
            AppBlockingState.MinimumAppVersion,
            controller.blockingState(allRules),
        )
    }

    @Test
    fun `flavor-suffixed version above minimum is not blocked`() {
        val controller = controller(
            appVersion = "0.25.4-Dev",
            minimumAppVersion = "0.25.3",
            shouldShowBlockingScreen = false,
        )

        assertNull(controller.blockingState(allRules))
    }

    @Test
    fun `invalid minimum app version is ignored`() {
        val controller = controller(
            appVersion = "1.0.0",
            minimumAppVersion = "not-semver",
            shouldShowBlockingScreen = false,
        )

        assertNull(controller.blockingState(allRules))
    }

    @Test
    fun `refresh feature flags asks the service to refresh then reloads the flag cache`() = runTest {
        val updateService = FakeFeatureFlagUpdateService()
        val featureFlagManager = FakeFeatureFlagManager(minimumAppVersion = "0.0.0")
        val controller = controller(
            featureFlagManager = featureFlagManager,
            updateService = updateService,
        )

        controller.refreshFeatureFlagsIfNeeded()

        assertTrue(updateService.refreshCalled)
        assertTrue(featureFlagManager.reloadCalled)
    }

    private fun controller(
        appVersion: String = "1.0.0",
        minimumAppVersion: String = "0.0.0",
        shouldShowBlockingScreen: Boolean = false,
        isRevoked: Boolean = false,
        updateService: FakeFeatureFlagUpdateService = FakeFeatureFlagUpdateService(),
        featureFlagManager: FakeFeatureFlagManager = FakeFeatureFlagManager(minimumAppVersion),
    ) = AppBlockingControllerImpl(
        configLogic = FakeConfigLogic(appVersion),
        featureFlagManager = featureFlagManager,
        featureFlagUpdateService = updateService,
        platformAuthenticationProvider = FakePlatformAuthenticationProvider(shouldShowBlockingScreen),
        walletRevokedProvider = WalletRevokedProvider { isRevoked },
    )

    private val allRules = setOf(
        AppBlockingRule.WalletRevoked,
        AppBlockingRule.PlatformAuthentication,
        AppBlockingRule.MinimumAppVersion,
    )
}

private class FakeConfigLogic(
    override val appVersion: String,
) : ConfigLogic {
    override val environmentConfig: EnvironmentConfig
        get() = error("Not used")
}

private class FakeFeatureFlagManager(
    private val minimumAppVersion: String,
) : FeatureFlagManager {

    var reloadCalled = false

    @Suppress("UNCHECKED_CAST")
    override fun <T> getFlagValue(flag: FeatureFlag<T>): T {
        return when (flag.key) {
            FeatureFlag.minimumAppVersion.key -> minimumAppVersion as T
            else -> flag.value
        }
    }

    override fun reloadFlags() {
        reloadCalled = true
    }
}

private class FakeFeatureFlagUpdateService : FeatureFlagUpdateService {
    var refreshCalled = false

    override suspend fun refreshFlagsIfNeeded() {
        refreshCalled = true
    }
}

private class FakePlatformAuthenticationProvider(
    private val shouldShowBlockingScreen: Boolean,
) : PlatformAuthenticationProvider {

    override fun shouldShowBlockingScreen(): Boolean = shouldShowBlockingScreen
}
