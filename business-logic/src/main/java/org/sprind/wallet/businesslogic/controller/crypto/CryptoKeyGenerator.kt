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
import java.security.UnrecoverableKeyException
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

    /**
     * Generates an AES-256-GCM key for encryption and decryption that can only be used within
     * [authValiditySeconds] after the user authenticated with a strong biometric or the device
     * credential (unlocking the device counts). The key goes in the TEE.
     */
    fun generateAuthBoundEncryptDecryptAesKey(
        provider: String,
        keyStoreAlias: String,
        authValiditySeconds: Int,
    )

    /**
     * Checks that the existing key under [keyStoreAlias] is in secure hardware, with the same
     * rule and the same outcome as the generate functions: software keys only where
     * `ENABLE_SOFTWARE_KEYS` allows them, otherwise the key is deleted and
     * [InsecureKeyStorageException] is thrown. For keys that may have been created by code other
     * than this generator. Throws [UnrecoverableKeyException] when there is no such key.
     */
    fun requireHardwareBackedKey(provider: String, keyStoreAlias: String)
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
): SecretKey = generateKey().requireHardwareBacked(provider.name, alias, allowSoftwareKeys)

/**
 * Returns this key if the keystore holds it in secure hardware (TEE, StrongBox or
 * unknown-secure); otherwise deletes it under [alias] and throws [InsecureKeyStorageException].
 */
internal fun SecretKey.requireHardwareBacked(
    providerName: String,
    alias: String,
    allowSoftwareKeys: Boolean = BuildConfig.ENABLE_SOFTWARE_KEYS,
): SecretKey {
    val keyInfo = SecretKeyFactory.getInstance(algorithm, providerName)
        .getKeySpec(this, KeyInfo::class.java) as KeyInfo
    if (!allowSoftwareKeys && !isSecureHardwareLevel(keyInfo.securityLevel)) {
        KeyStore.getInstance(providerName).apply { load(null) }.deleteEntry(alias)
        throw InsecureKeyStorageException(alias, keyInfo.securityLevel)
    }
    return this
}

internal class CryptoKeyGeneratorImpl : CryptoKeyGenerator {
    override fun generateEncryptDecryptAesKey(
        provider: String,
        keyStoreAlias: String,
    ) {
        generate(provider, encryptDecryptAesKeySpec(keyStoreAlias))
    }

    override fun requireHardwareBackedKey(provider: String, keyStoreAlias: String) {
        val key = KeyStore.getInstance(provider).apply { load(null) }.getKey(keyStoreAlias, null)
            as? SecretKey
            ?: throw UnrecoverableKeyException("No secret key under alias $keyStoreAlias")
        key.requireHardwareBacked(provider, keyStoreAlias)
    }

    private fun generate(provider: String, spec: KeyGenParameterSpec) {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)
            .apply { init(spec) }
            .generateHardwareBackedKey(spec.keystoreAlias)
    }

    override fun generateAuthBoundEncryptDecryptAesKey(
        provider: String,
        keyStoreAlias: String,
        authValiditySeconds: Int,
    ) {
        generate(provider, authBoundEncryptDecryptAesKeySpec(keyStoreAlias, authValiditySeconds))
    }

    companion object {
        internal fun encryptDecryptAesKeySpec(keyStoreAlias: String): KeyGenParameterSpec =
            aesGcmKeySpecBuilder(keyStoreAlias)
                .setUserAuthenticationRequired(false)
                .build()

        private fun aesGcmKeySpecBuilder(keyStoreAlias: String): KeyGenParameterSpec.Builder =
            KeyGenParameterSpec.Builder(
                keyStoreAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setKeySize(AES_KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)

        internal fun authBoundEncryptDecryptAesKeySpec(
            keyStoreAlias: String,
            authValiditySeconds: Int,
        ): KeyGenParameterSpec =
            aesGcmKeySpecBuilder(keyStoreAlias)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    authValiditySeconds,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
                .build()
    }
}