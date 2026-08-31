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

package org.sprind.wallet.businesslogic.controller.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator

interface CryptoKeyGenerator {

    /**
     * Generates an AES secret key for encryption and decryption.
     *
     * @param algorithm The name of the algorithm.
     * @param provider The security provider.
     * @param keyStoreAlias The alias under which the key is stored.
     */
    fun generateEncryptDecryptAesKey(provider: String, keyStoreAlias: String)
}

internal class CryptoKeyGeneratorImpl : CryptoKeyGenerator {
    override fun generateEncryptDecryptAesKey(
        provider: String,
        keyStoreAlias: String,
    ) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            provider
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyStoreAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).apply {
                setUserAuthenticationRequired(false)
            }.build()

        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

}