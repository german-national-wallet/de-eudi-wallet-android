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

package org.sprind.wallet.corelogic.platformauth

import android.app.KeyguardManager
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController

/**
 * Checks the Platform Authentication (PA) invariant: the wallet must not hold any
 * data while the device has no secure lock screen (no PIN / passcode / biometric
 * unlock configured).
 *
 * Platform Authentication here refers to the device lock screen, i.e. whether the
 * user has set up a PIN/passcode/pattern or biometric to unlock the phone. It is
 * detected via [KeyguardManager.isDeviceSecure]. When the user removes their device
 * lock, [KeyguardManager.isDeviceSecure] becomes false and the encryption-at-rest
 * key is no longer meaningfully protected, so all wallet data must be wiped.
 *
 * The invariant is violated when wallet data exists (there is at least one issued
 * or deferred document) AND the device is not secure. A device with no lock screen
 * and no wallet data is not a violation (the user is simply nudged to set up a lock
 * screen before using the wallet).
 */
class PlatformAuthInvariant(
    private val keyguardManager: KeyguardManager,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
) {

    /**
     * True when wallet data exists but the device has no secure lock screen, i.e.
     * the wallet must be wiped to protect the data whose encryption key is no
     * longer bound to Platform Authentication.
     */
    fun shouldWipe(): Boolean =
        !isDeviceSecure() && hasDocuments()

    /**
     * True when the device has no secure lock screen but there is no wallet data to
     * protect. The user is simply nudged to set up a lock screen before using the
     * wallet, rather than having data wiped.
     */
    fun shouldWarn(): Boolean =
        !isDeviceSecure() && !hasDocuments()

    // True when the device has a secure lock screen (PIN/passcode/pattern or
    // biometric set up). Used to decide whether to show the "set up a lock screen"
    // warning instead of (or after) a wipe.
    private fun isDeviceSecure(): Boolean = keyguardManager.isDeviceSecure

    // True when the wallet has documents. This is a safeguard clause: it returns true also
    // when there is an exception in reading the database, ensuring the database gets wiped-out
    // in such a case.
    private fun hasDocuments(): Boolean = runCatching {
        walletCoreDocumentsController.getAllDocuments().isNotEmpty()
    }.getOrDefault(true)
}