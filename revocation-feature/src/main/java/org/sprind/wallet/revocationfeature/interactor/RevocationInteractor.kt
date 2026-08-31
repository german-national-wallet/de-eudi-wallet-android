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

package org.sprind.wallet.revocationfeature.interactor

import eu.europa.ec.authenticationlogic.controller.storage.WalletRegistrationStorageController
import eu.europa.ec.uilogic.navigation.ModuleRoute
import org.sprind.wallet.uilogic.navigation.NavigationGuard
import org.sprind.wallet.revocationfeature.provider.RevocationStorageProvider

interface RevocationInteractor : NavigationGuard {
    /**
     * Get revocation code (received during registration)
     */
    suspend fun getRevocationCode(): String?

    fun hasUserConfirmedSavingCode(): Boolean

    fun storeUserConfirmedSavingCode(value: Boolean)

    override val destination: String
        get() = ModuleRoute.DashboardModule.route

    override fun getDirection(): String =
        if (hasUserConfirmedSavingCode()) destination else ModuleRoute.RevocationModule.route
}

class RevocationInteractorImpl(
    private val walletRegistrationStorageController: WalletRegistrationStorageController,
    private val revocationStorageProvider: RevocationStorageProvider,
) : RevocationInteractor {
    override suspend fun getRevocationCode(): String? {
        return walletRegistrationStorageController.getWalletRegistration()
            ?.walletInstanceRevocationCode
    }

    override fun hasUserConfirmedSavingCode(): Boolean =
        revocationStorageProvider.hasUserConfirmedSavingCode()

    override fun storeUserConfirmedSavingCode(value: Boolean) =
        revocationStorageProvider.storeUserConfirmedSavingCode(value)
}
