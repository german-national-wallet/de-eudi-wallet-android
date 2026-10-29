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
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import eu.europa.ec.businesslogic.BuildConfig
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

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

const val AES_KEY_SIZE_BITS = 256

private val SECURE_HARDWARE_LEVELS = setOf(
    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,
    KeyProperties.SECURITY_LEVEL_STRONGBOX,
    KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE,
)

internal fun isSecureHardwareLevel(securityLevel: Int): Boolean =
    securityLevel in SECURE_HARDWARE_LEVELS

class InsecureKeyStorageException(alias: String, securityLevel: Int) :
    GeneralSecurityException("Key $alias is not in secure hardware (security level $securityLevel)")

/**
 * Generates the key and rejects it, deleting it again, unless the keystore placed it in secure
 * hardware (TEE, StrongBox or unknown-secure). Emulators only have a software keystore, hence
 * [allowSoftwareKeys].
 */
internal fun KeyGenerator.generateHardwareBackedKey(
    alias: String,
    allowSoftwareKeys: Boolean = BuildConfig.ENABLE_SOFTWARE_KEYS,
): SecretKey {
    val key = generateKey()
    val keyInfo = SecretKeyFactory.getInstance(key.algorithm, provider.name)
        .getKeySpec(key, KeyInfo::class.java) as KeyInfo
    if (!allowSoftwareKeys && !isSecureHardwareLevel(keyInfo.securityLevel)) {
        KeyStore.getInstance(provider.name).apply { load(null) }.deleteEntry(alias)
        throw InsecureKeyStorageException(alias, keyInfo.securityLevel)
    }
    return key
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

        keyGenerator.init(encryptDecryptAesKeySpec(keyStoreAlias))
        keyGenerator.generateHardwareBackedKey(keyStoreAlias)
    }

    companion object {
        internal fun encryptDecryptAesKeySpec(keyStoreAlias: String): KeyGenParameterSpec =
            KeyGenParameterSpec.Builder(
                keyStoreAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setKeySize(AES_KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
    }
}