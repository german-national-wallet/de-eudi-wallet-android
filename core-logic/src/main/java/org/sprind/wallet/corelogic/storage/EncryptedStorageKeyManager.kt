/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.corelogic.storage

import android.util.Base64
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import java.security.SecureRandom

internal const val ENCRYPTED_STORAGE_KEY = "wi_data_enc_symk"

fun interface EncryptedStorageKeyManager {
    fun getOrGenerateEncryptedStorageKey(): ByteArray
}

internal class EncryptedStorageKeyManagerImpl(
    private val prefsController: PrefsController,
) : EncryptedStorageKeyManager {

    @Volatile
    private var cachedKey: ByteArray? = null

    override fun getOrGenerateEncryptedStorageKey(): ByteArray {
        cachedKey?.let { return it }
        return synchronized(this) {
            cachedKey ?: loadOrGenerate().also { cachedKey = it }
        }
    }

    private fun loadOrGenerate(): ByteArray =
        prefsController.getByteArray(ENCRYPTED_STORAGE_KEY) ?: run {
            val random = SecureRandom()
            val newKey = ByteArray(32)
            random.nextBytes(newKey)
            val newBase64Key = Base64.encodeToString(newKey, Base64.DEFAULT)
            prefsController.setStringSync(ENCRYPTED_STORAGE_KEY, newBase64Key)
            newKey
        }

    companion object {
        fun PrefsController.getByteArray(key: String): ByteArray? {
            val value = this.getString(key, "")
            if (value.isEmpty()) return null
            return Base64.decode(value, Base64.DEFAULT)
        }
    }

}