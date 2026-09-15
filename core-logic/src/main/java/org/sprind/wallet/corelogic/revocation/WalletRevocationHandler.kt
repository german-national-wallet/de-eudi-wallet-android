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

package org.sprind.wallet.corelogic.revocation

import eu.europa.ec.authenticationlogic.controller.storage.HardwareKeyStorageController
import eu.europa.ec.authenticationlogic.controller.storage.WalletPinUnBlockTimeStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.DeleteAllDocumentsPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withContext
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmKeyManager
import org.sprind.wallet.authenticationlogic.controller.storage.MdvmRegistrationStorageController
import org.sprind.wallet.authenticationlogic.provider.RwscaStorageController
import org.sprind.wallet.businesslogic.controller.revocation.WalletRevocationStore
import org.sprind.wallet.businesslogic.extensions.runSuspendCatching

/**
 * Executes the wallet self-lock and its user-acknowledged reset.
 *
 * The MDVM confirms a revocation with an `ACCOUNT_REVOKED` renewal response; [lockWallet] then
 * persists the revoked flag, notifies the user, and selectively wipes wallet data in place —
 * deliberately NOT `clearApplicationUserData()`: the process stays alive (the FCM service can
 * finish, the notification renders, the blocking screen appears immediately) and the revoked
 * flag survives, so a revoked wallet cannot silently re-register as a fresh instance.
 *
 * The wipe never touches the document-storage encryption key (`wi_data_enc_symk`) or the
 * document database file — documents are deleted through the live storage API instead, which
 * keeps the in-process key cache and open database handle consistent without a restart.
 */
interface WalletRevocationHandler {

    /** Locks the wallet: flag, then notification, then selective wipe. Idempotent. */
    suspend fun lockWallet()

    /**
     * User-acknowledged reset from the blocking screen: re-runs the wipe defensively (a crash
     * mid-[lockWallet] leaves the flag set but the wipe possibly incomplete), runs
     * [beforeUnlock] (any throw keeps the wallet locked), then clears the revocation state
     * LAST, allowing the wallet to re-onboard as a new instance.
     *
     * @param beforeUnlock extra cleanup that must be persisted strictly before the wallet
     * unlocks (e.g. clearing the revocation-code confirmation so re-onboarding shows the new
     * instance's one-shot code). A failure here fails the reset.
     * @return false when the document wipe or [beforeUnlock] failed — the wallet stays locked
     * and the caller must not proceed (the blocking screen's button acts as a retry).
     */
    suspend fun resetAfterRevocation(beforeUnlock: suspend () -> Unit = {}): Boolean
}

class WalletRevocationHandlerImpl(
    private val store: WalletRevocationStore,
    private val notificationManager: RevocationNotificationManager,
    private val documentsController: WalletCoreDocumentsController,
    private val hardwareKeyStorageController: HardwareKeyStorageController,
    private val mdvmKeyManager: MdvmKeyManager,
    private val mdvmRegistrationStorageController: MdvmRegistrationStorageController,
    private val rwscaStorageController: RwscaStorageController,
    private val walletPinUnBlockTimeStorageController: WalletPinUnBlockTimeStorageController,
    private val logController: LogController,
) : WalletRevocationHandler {

    override suspend fun lockWallet() = withContext(Dispatchers.IO) {
        if (store.isRevoked()) {
            logController.d(TAG) { "Wallet already locked; ignoring repeated lock request" }
            return@withContext
        }

        // Flag first: if anything below fails or the process dies, the wallet is still locked
        // and resetAfterRevocation() re-runs the wipe.
        store.markRevoked()

        runSuspendCatching { notificationManager.postRevocationNotification() }
            .onFailure { logController.e(TAG, it) }

        val documentsWiped = wipeWalletData()
        if (!documentsWiped) {
            // The wallet stays locked either way (the flag is already set); the reset re-runs
            // the wipe and refuses to unlock until it succeeds.
            logController.e(TAG) { "wallet self-lock engaged but the document wipe FAILED" }
        }

        // Stable line asserted by scripts/revoke-e2e.sh — keep message and tag in sync with it.
        logController.i(TAG) { "wallet self-lock engaged (ACCOUNT_REVOKED)" }
    }

    override suspend fun resetAfterRevocation(
        beforeUnlock: suspend () -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val documentsWiped = wipeWalletData()
        if (!documentsWiped) {
            logController.e(TAG) { "reset aborted: document wipe failed; wallet stays locked" }
            return@withContext false
        }
        store.clearRecheckPending()
        // Must persist strictly before the unlock; a throw here keeps the wallet locked.
        beforeUnlock()
        // Last, so an interrupted reset never leaves an unlocked wallet with stale data.
        store.clearRevoked()
        logController.i(TAG) { "wallet self-lock reset by the user" }
        true
    }

    /**
     * Each step runs in its own [runSuspendCatching] (cancellation propagates): one failing
     * collaborator must not stop the rest of the wallet data from being destroyed.
     *
     * @return whether the document wipe — the data-destruction core — succeeded. The remaining
     * key/preference clears are best-effort and only logged.
     */
    private suspend fun wipeWalletData(): Boolean {
        val documentsWiped = runSuspendCatching {
            documentsController.wipeAllDocuments().last()
        }.fold(
            onSuccess = { terminalState ->
                when (terminalState) {
                    is DeleteAllDocumentsPartialState.Success -> true
                    is DeleteAllDocumentsPartialState.Failure -> {
                        logController.e(TAG) {
                            "document wipe failed: ${terminalState.errorMessage}"
                        }
                        false
                    }
                }
            },
            onFailure = {
                logController.e(TAG, it)
                false
            },
        )
        runSuspendCatching {
            hardwareKeyStorageController.removeWalletRegistrationIds()
        }.onFailure { logController.e(TAG, it) }
        runSuspendCatching { mdvmKeyManager.deleteAuthKeys() }
            .onFailure { logController.e(TAG, it) }
        runSuspendCatching { mdvmKeyManager.deleteReattestKeys() }
            .onFailure { logController.e(TAG, it) }
        runSuspendCatching {
            mdvmRegistrationStorageController.clearMdvmRegistration()
        }.onFailure { logController.e(TAG, it) }
        runSuspendCatching { rwscaStorageController.clearRwscaRegistration() }
            .onFailure { logController.e(TAG, it) }
        runSuspendCatching { rwscaStorageController.clearPinInitialized() }
            .onFailure { logController.e(TAG, it) }
        runSuspendCatching { rwscaStorageController.clearPinSalt() }
            .onFailure { logController.e(TAG, it) }
        runSuspendCatching { walletPinUnBlockTimeStorageController.clear() }
            .onFailure { logController.e(TAG, it) }
        return documentsWiped
    }

    companion object {
        private const val TAG = "WalletRevocation"
    }
}
