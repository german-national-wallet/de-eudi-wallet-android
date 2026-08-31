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

package org.sprind.wallet.revocationfeature.storage

import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.sprind.wallet.revocationfeature.provider.RevocationStorageProvider

const val SHARED_PREFERENCE_REVOCATION_SAVED_CODE_CONFIRMATION_KEY =
    "WalletRevocationSavedCodeConfirmation"

internal class PrefsRevocationStorageProvider(
    private val prefsController: PrefsController
) : RevocationStorageProvider {
    override fun hasUserConfirmedSavingCode(): Boolean {
        return prefsController.getBool(
            key = SHARED_PREFERENCE_REVOCATION_SAVED_CODE_CONFIRMATION_KEY,
            defaultValue = false
        )
    }

    override fun storeUserConfirmedSavingCode(value: Boolean) {
        prefsController.setBool(SHARED_PREFERENCE_REVOCATION_SAVED_CODE_CONFIRMATION_KEY, value)
    }
}