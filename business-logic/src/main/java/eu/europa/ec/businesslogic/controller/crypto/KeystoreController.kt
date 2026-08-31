/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.businesslogic.controller.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import org.sprind.wallet.businesslogic.controller.crypto.CryptoKeyGenerator
import org.sprind.wallet.businesslogic.controller.crypto.EcKeyPairController
import org.sprind.wallet.businesslogic.controller.crypto.KeyStoreInstanceProvider
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface KeystoreController {

    fun retrieveOrGenerateBiometricSecretKey(): SecretKey?

    /**
     * Retrieves the existing wallet registration secret key if exists or generates a new one if it is the
     * first time.
     */
    fun retrieveOrGenerateWalletRegistrationSecretKey(): SecretKey?

    /**
     * Retrieves the existing wallet instance private key secret key if exists or generates a new one if it is the
     * first time.
     */
    fun retrieveOrGenerateWalletInstancePrivateKeySecretKey(): SecretKey?

    /**
     * Retrieves the existing wallet instance attestation private key secret key if exists or generates a new one if it is the
     * first time.
     */
    fun retrieveOrGenerateWalletInstanceAttestationPrivateKeySecretKey(): SecretKey?

    /**
     * Resets the following aliases in preferences to empty String
     * KEY_WALLET_REGISTRATION_ALIAS
     * KEY_WALLET_ATTESTATION_ALIAS
     * KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS
     * KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS
     * * TODO: this is a quick fix for the issue WD-2191
     */
    fun removeWalletKeysAndAliases()
}

internal class KeystoreControllerImpl(
    private val prefKeys: PrefKeys,
    private val logController: LogController,
    private val keyStoreInstanceProvider: KeyStoreInstanceProvider,
    private val cryptoKeyGenerator: CryptoKeyGenerator,
) : KeystoreController {
    private val logTag = javaClass.simpleName

    companion object {
        private const val STORE_TYPE = "AndroidKeyStore"
    }

    private lateinit var androidKeyStore: KeyStore

    init {
        loadKeyStore()
    }

    /**
     * Load/Init KeyStore
     */
    private fun loadKeyStore() {
        try {
            androidKeyStore = keyStoreInstanceProvider.createInstanceAndLoad()
        } catch (e: Exception) {
            logController.e(logTag, e)
        }
    }

    /**
     * Retrieves the existing biometric secret key if exists or generates a new one if it is the
     * first time.
     */
    override fun retrieveOrGenerateBiometricSecretKey(): SecretKey {
        return androidKeyStore.let {
            val alias = prefKeys.getBiometricAlias()
            if (alias.isEmpty()) {
                val newAlias = createPublicKey()
                generateBiometricSecretKey(newAlias)
                prefKeys.setBiometricAlias(newAlias)
                getBiometricSecretKey(it, newAlias)
            } else {
                getBiometricSecretKey(it, alias)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun generateBiometricSecretKey(alias: String) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE_TYPE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .setUserAuthenticationValidityDurationSeconds(-1)
                .build()
        )
        keyGenerator.generateKey()
        logController.d(logTag) { "[STORAGE] store key=$alias dest=keystore" }
    }

    private fun getBiometricSecretKey(keyStore: KeyStore, alias: String): SecretKey {
        keyStore.load(null)
        return keyStore.getKey(alias, null) as SecretKey
    }

    /**
     * Get random GUID string
     *
     * @return a string containing 64 characters
     */
    private fun createPublicKey(): GUID =
        (UUID.randomUUID().toString() + UUID.randomUUID()
            .toString()).take(CryptoControllerImpl.MAX_GUID_LENGTH)

    override fun retrieveOrGenerateWalletRegistrationSecretKey(): SecretKey {
        val walletInstanceAlias = "wi_id"
        return androidKeyStore.let { keyStore ->
            val alias = prefKeys.getAlias(PrefKeys.KEY_WALLET_REGISTRATION_ALIAS)
            if (alias.isEmpty()) {
                cryptoKeyGenerator.generateEncryptDecryptAesKey(
                    provider = STORE_TYPE,
                    keyStoreAlias = walletInstanceAlias
                )
                prefKeys.saveAlias(PrefKeys.KEY_WALLET_REGISTRATION_ALIAS, walletInstanceAlias)
                logController.d(logTag) { "[STORAGE] store key=$walletInstanceAlias dest=keystore" }
            }
            keyStore.getSecretKey(walletInstanceAlias)
        }
    }

    override fun retrieveOrGenerateWalletInstancePrivateKeySecretKey(): SecretKey {
        val hardwareKeyAlias = "wi_priv_key"
        return androidKeyStore.let { keyStore ->
            val alias = prefKeys.getAlias(PrefKeys.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS)
            if (alias.isEmpty()) {
                cryptoKeyGenerator.generateEncryptDecryptAesKey(
                    provider = STORE_TYPE,
                    keyStoreAlias = hardwareKeyAlias
                )
                prefKeys.saveAlias(PrefKeys.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS, hardwareKeyAlias)
                logController.d(logTag) { "[STORAGE] store key=$hardwareKeyAlias dest=keystore" }
            }
            keyStore.getSecretKey(hardwareKeyAlias)
        }
    }

    override fun retrieveOrGenerateWalletInstanceAttestationPrivateKeySecretKey(): SecretKey {
        val hardwareKeyAlias = "wia_priv_key"
        return androidKeyStore.let { keyStore ->
            val alias =
                prefKeys.getAlias(PrefKeys.KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS)
            if (alias.isEmpty()) {
                cryptoKeyGenerator.generateEncryptDecryptAesKey(
                    provider = STORE_TYPE,
                    keyStoreAlias = hardwareKeyAlias
                )
                prefKeys.saveAlias(
                    PrefKeys.KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS,
                    hardwareKeyAlias
                )
                logController.d(logTag) { "[STORAGE] store key=$hardwareKeyAlias dest=keystore" }
            }
            keyStore.getSecretKey(hardwareKeyAlias)
        }
    }

    override fun removeWalletKeysAndAliases() {
        val aliasKeysToDelete = listOf(
            PrefKeys.KEY_WALLET_REGISTRATION_ALIAS,
            PrefKeys.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS,
            PrefKeys.KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS,
        )
        aliasKeysToDelete.map { aliasKey ->
            val alias = prefKeys.getAlias(aliasKey)
            try {
                if (androidKeyStore.containsAlias(alias)) {
                    androidKeyStore.deleteEntry(alias)
                    logController.d(logTag) { "[STORAGE] clear key=$alias dest=keystore" }
                }
                prefKeys.saveAlias(aliasKey, "")
            } catch (e: Exception) {
                logController.e(this.javaClass.simpleName, e)
            }
        }
    }

    private fun KeyStore.getSecretKey(alias: String): SecretKey {
        load(null)
        return getKey(alias, null) as SecretKey
    }

    private fun KeyStore.getPrivateKeyEntry(alias: String): PrivateKeyEntry {
        load(null)
        val entry = getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("$alias Key not found in Keystore")

        return entry
    }
}