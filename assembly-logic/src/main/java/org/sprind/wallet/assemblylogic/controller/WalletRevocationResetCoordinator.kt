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

import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.sprind.wallet.corelogic.revocation.WalletRevocationHandler
import org.sprind.wallet.pushnotificationsfeature.service.WalletFirebaseMessagingService
import org.sprind.wallet.revocationfeature.provider.RevocationStorageProvider

/**
 * Performs the user-acknowledged reset from the wallet-revoked blocking screen.
 *
 * Lives in assembly-logic because it composes core-logic (the revocation handler) with
 * revocation-feature (the revocation-code-seen confirmation) and push-notifications-feature
 * (the FCM registration flag), which no logic module can see.
 *
 * Both cleanups run inside the handler's before-unlock hook, i.e. strictly before the revoked
 * flag is cleared: clearing the code confirmation makes re-onboarding show the revocation-code
 * flow again (the new wallet instance gets its own one-shot code), and clearing the
 * FCM-registered flag makes the next resume re-register the push token under the new MDVM
 * identity — without it, revocation pushes for the new instance are never delivered.
 */
class WalletRevocationResetCoordinator(
    private val walletRevocationHandler: WalletRevocationHandler,
    private val revocationStorageProvider: RevocationStorageProvider,
    private val prefsController: PrefsController,
) {

    /** @return false when the reset failed and the wallet stays locked (caller may retry). */
    suspend fun reset(): Boolean =
        walletRevocationHandler.resetAfterRevocation {
            revocationStorageProvider.storeUserConfirmedSavingCode(false)
            prefsController.clear(WalletFirebaseMessagingService.FCM_TOKEN_REGISTERED_KEY)
        }
}
