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

package org.sprind.wallet.businesslogic.controller.storage

import android.security.KeyStoreException
import android.security.keystore.UserNotAuthenticatedException
import org.sprind.wallet.businesslogic.controller.crypto.InsecureKeyStorageException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

/**
 * Why [eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController] could not open the
 * encrypted preferences.
 */
sealed class EncryptedPrefsUnavailableException(message: String, cause: Throwable) :
    Exception(message, cause) {

    /** The user has not authenticated recently. The data is intact and readable after the next unlock. */
    class Locked(cause: Throwable) :
        EncryptedPrefsUnavailableException("locked until the next device unlock", cause)

    /** The keystore itself failed (busy, IPC failure, daemon restart). The next attempt may succeed. */
    class Transient(cause: Throwable) :
        EncryptedPrefsUnavailableException("keystore temporarily unavailable", cause)

    /**
     * The device has no secure lock screen, or no secure hardware for the master key. The data
     * is not lost, and wiping would not make the device capable.
     */
    class InsecureDevice(cause: Throwable) :
        EncryptedPrefsUnavailableException("device cannot hold the master key", cause)

    /**
     * The keyset cannot be decrypted: the master key is missing, has changed or was permanently
     * invalidated, or the keyset is corrupt. The stored data cannot be read again.
     */
    class KeysetLost(cause: Throwable) :
        EncryptedPrefsUnavailableException("keyset cannot be decrypted", cause)
}

/**
 * Classifies a failure to open the encrypted preferences. Returns `null` for failures that are
 * not about the keystore or the keyset, which the caller rethrows unchanged.
 */
internal fun Exception.toEncryptedPrefsUnavailable(
    isDeviceSecure: Boolean,
): EncryptedPrefsUnavailableException? {
    if (this !is GeneralSecurityException && this !is IOException && this !is ProviderException) {
        return null
    }
    val chain = generateSequence<Throwable>(this) { it.cause }
    return when {
        chain.any { it is UserNotAuthenticatedException } ->
            EncryptedPrefsUnavailableException.Locked(this)
        chain.any { it is KeyStoreException && (it.isTransientFailure || it.isSystemError) } ->
            EncryptedPrefsUnavailableException.Transient(this)
        // Without a secure lock screen the auth-bound master key cannot be created at all.
        !isDeviceSecure || chain.any { it is InsecureKeyStorageException } ->
            EncryptedPrefsUnavailableException.InsecureDevice(this)
        else -> EncryptedPrefsUnavailableException.KeysetLost(this)
    }
}
