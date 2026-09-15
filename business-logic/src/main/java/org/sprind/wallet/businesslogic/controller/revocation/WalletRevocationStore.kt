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

package org.sprind.wallet.businesslogic.controller.revocation

import eu.europa.ec.businesslogic.controller.storage.PrefsController
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.annotation.Single
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the wallet self-lock state.
 *
 * The MDVM confirms a revocation with an `ACCOUNT_REVOKED` renewal response; the wallet then
 * locks itself: the revoked flag is set, wallet data is selectively wiped, and the app shows a
 * blocking screen until the user acknowledges and resets. The flag deliberately survives the
 * selective wipe (the wipe enumerates its deletions and never clears all preferences) so a
 * revoked wallet cannot silently re-register as a fresh instance.
 *
 * The recheck-pending flag records that a revocation push arrived but its authenticated renewal
 * did not reach a conclusive MDVM response (e.g. the device was offline). It is only ever set by
 * the push handler — the wallet performs no unconditional revocation checks.
 */
interface WalletRevocationStore {

    /**
     * Emits the current revoked state; seeded from preferences at construction. In-process
     * observers (the blocking-screen host) use this because preference change listeners never
     * fire for this app's encrypted preferences.
     */
    val revokedFlow: StateFlow<Boolean>

    fun isRevoked(): Boolean

    /** Marks the wallet revoked with commit semantics: the writer may die right afterwards. */
    fun markRevoked()

    fun clearRevoked()

    fun isRecheckPending(): Boolean

    fun markRecheckPending()

    fun clearRecheckPending()
}

@Single(binds = [WalletRevocationStore::class])
class WalletRevocationStoreImpl(
    private val prefsController: PrefsController,
) : WalletRevocationStore {

    private val revoked = MutableStateFlow(readRevoked())

    override val revokedFlow: StateFlow<Boolean> = revoked.asStateFlow()

    override fun isRevoked(): Boolean = readRevoked()

    override fun markRevoked() {
        prefsController.setBoolSync(WALLET_REVOKED_KEY, true)
        revoked.value = true
    }

    override fun clearRevoked() {
        prefsController.clear(WALLET_REVOKED_KEY)
        revoked.value = false
    }

    override fun isRecheckPending(): Boolean =
        prefsController.getBool(RECHECK_PENDING_KEY, false)

    override fun markRecheckPending() {
        prefsController.setBoolSync(RECHECK_PENDING_KEY, true)
    }

    override fun clearRecheckPending() {
        prefsController.clear(RECHECK_PENDING_KEY)
    }

    private fun readRevoked(): Boolean = prefsController.getBool(WALLET_REVOKED_KEY, false)

    companion object {
        /** Same key name as the iOS wallet uses for its self-lock flag. */
        private const val WALLET_REVOKED_KEY = "wallet_revoked"
        private const val RECHECK_PENDING_KEY = "wallet_revocation_recheck_pending"
    }
}
