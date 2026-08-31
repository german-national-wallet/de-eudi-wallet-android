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

import eu.europa.ec.businesslogic.BuildConfig
import eu.europa.ec.businesslogic.config.ConfigLogic
import org.sprind.wallet.businesslogic.model.SemanticVersion
import org.sprind.wallet.corelogic.platformauth.PlatformAuthInvariant
import org.sprind.wallet.flags.FeatureFlagManager
import org.sprind.wallet.flags.FeatureFlagUpdateService
import org.sprind.wallet.flags.models.FeatureFlag

interface AppBlockingController {
    fun blockingState(
        rules: Set<AppBlockingRule> = setOf(
            AppBlockingRule.PlatformAuthentication,
            AppBlockingRule.MinimumAppVersion,
        ),
    ): AppBlockingState?
    suspend fun refreshFeatureFlagsIfNeeded()
}

enum class AppBlockingState {
    PlatformAuthentication,
    MinimumAppVersion,
}

enum class AppBlockingRule {
    PlatformAuthentication,
    MinimumAppVersion,
}

fun interface PlatformAuthenticationProvider {
    fun shouldShowBlockingScreen(): Boolean
}

class AppBlockingControllerImpl(
    private val configLogic: ConfigLogic,
    private val featureFlagManager: FeatureFlagManager,
    private val featureFlagUpdateService: FeatureFlagUpdateService,
    private val platformAuthenticationProvider: PlatformAuthenticationProvider,
) : AppBlockingController {

    override fun blockingState(rules: Set<AppBlockingRule>): AppBlockingState? {
        if (
            rules.contains(AppBlockingRule.PlatformAuthentication) &&
            platformAuthenticationProvider.shouldShowBlockingScreen()
        ) {
            return AppBlockingState.PlatformAuthentication
        }
        if (rules.contains(AppBlockingRule.MinimumAppVersion) && isMinimumAppVersionBlocked()) {
            return AppBlockingState.MinimumAppVersion
        }
        return null
    }

    override suspend fun refreshFeatureFlagsIfNeeded() {
        featureFlagUpdateService.refreshFlagsIfNeeded()
        // The storage change listener is unreliable (a new EncryptedSharedPreferences
        // instance is created per access, so writes never notify it), so reload the
        // in-memory cache explicitly to reflect the freshly fetched flags this resume.
        featureFlagManager.reloadFlags()
    }

    private fun isMinimumAppVersionBlocked(): Boolean {
        val currentVersion = SemanticVersion.parse(configLogic.appVersion) ?: return false
        val minimumVersion = SemanticVersion.parse(
            featureFlagManager.getFlagValue(FeatureFlag.minimumAppVersion)
        ) ?: return false
        // Compare release cores only: APP_VERSION always carries a "-<flavor>" suffix
        // (a semver prerelease) that must not rank the build below the minimum.
        return compareValuesBy(
            currentVersion,
            minimumVersion,
            SemanticVersion::major,
            SemanticVersion::minor,
            SemanticVersion::patch,
        ) < 0
    }
}

class PlatformAuthenticationProviderImpl(
    private val platformAuthInvariant: PlatformAuthInvariant,
) : PlatformAuthenticationProvider {

    override fun shouldShowBlockingScreen(): Boolean =
        !BuildConfig.IS_SIMULATOR.toBoolean() && platformAuthInvariant.shouldWarn()
}
