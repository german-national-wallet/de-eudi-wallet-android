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
import java.io.File

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
 * The invariant is violated when wallet data exists AND the device is not secure. A
 * device with no lock screen and no wallet data is not a violation (the user is
 * simply nudged to set up a lock screen before using the wallet).
 *
 * "Wallet data exists" is decided by the presence of the encrypted storage database
 * file ([walletStorageDatabaseFile]) on disk, not by querying the documents: this
 * check runs in `Application.onCreate`, also for background starts, and opening the
 * storage needs the encrypted preferences, whose master key may be unavailable
 * then. The file is created when the storage is first initialised, so an onboarded
 * wallet without documents counts as holding data and is wiped rather than warned.
 */
class PlatformAuthInvariant(
    private val keyguardManager: KeyguardManager,
    private val walletStorageDatabaseFile: File,
) {

    /**
     * True when wallet data exists but the device has no secure lock screen, i.e.
     * the wallet must be wiped to protect the data whose encryption key is no
     * longer bound to Platform Authentication.
     */
    fun shouldWipe(): Boolean =
        !isDeviceSecure() && hasWalletData()

    /**
     * True when the device has no secure lock screen but there is no wallet data to
     * protect. The user is simply nudged to set up a lock screen before using the
     * wallet, rather than having data wiped.
     */
    fun shouldWarn(): Boolean =
        !isDeviceSecure() && !hasWalletData()

    /**
     * True when the device has a secure lock screen (PIN/passcode/pattern or biometric set
     * up). Without one the Platform Authentication screen applies, whether the wallet is empty
     * ([shouldWarn]) or holds data ([shouldWipe]); callers that only need that distinction
     * use this instead of evaluating both.
     */
    fun isDeviceSecure(): Boolean = keyguardManager.isDeviceSecure

    private fun hasWalletData(): Boolean = walletStorageDatabaseFile.exists()
}