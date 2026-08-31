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

package org.sprind.wallet.authenticationlogic.controller.mdvm

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import okio.ByteString
import org.multipaz.crypto.EcCurve
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec

/**
 * Information about wi_mdvm_auth_prvk/pubk or wi_mdvm_reattest_prvk/pubk
 * key pair, created by [MdvmKeyManager].
 */
data class MdvmKeyInfo(
    val keystoreAlias: String,
    val keyPair: KeyPair,
    val attestationChain: List<Certificate>,
)

interface MdvmKeyManager {
    /**
     * Creates a new `wi_mdvm_auth_prvk` / `pubk` pair, silently overwriting any that
     * may already exist.
     *
     * @return the key material (including `keystoreAlias`) of the newly created key pair.
     */
    fun createAuthKeys(challengeNonce: ByteString): MdvmKeyInfo

    /**
     * @return the key material for an existing `wi_mdvm_auth_prvk` / `pubk` pair from KeyStore
     *         for the alias [wiMdvmAuthKeysAlias], or `null` if the key is not found.
     */
    fun getExistingAuthKeys(wiMdvmAuthKeysAlias: String): KeyPair?

    /**
     * Creates a new `wi_mdvm_reattest_prvk` / `pubk` pair, silently overwriting any that
     * may already exist.
     */
    fun createReattestKeys(challengeNonce: ByteString): MdvmKeyInfo

    /**
     * Deletes the `wi_mdvm_reattest_prvk` / `pubk` pair, if present.
     *
     * @return whether the key pair existed (and was deleted).
     */
    fun deleteReattestKeys(): Boolean
}

/**
 * An implementation of [MdvmKeyManager] backed by KeyStore. The [MdvmKeyInfo.keyPair]
 * returned by this class is only a handle, since the private key material never leaves
 * KeyStore.
 */
internal class AndroidMdvmKeyManager(
    context: Context,
) : MdvmKeyManager {

    private val keystoreHasStrongbox = context.packageManager
        ?.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE) ?: false

    private val keyStore by lazy {
        KeyStore.getInstance(ANDROID_KEY_STORE_PROVIDER_NAME).apply { load(null) }
    }

    private fun createKeys(challengeNonce: ByteString, keystoreAlias: String): MdvmKeyInfo {
        // Clear up any keys left over from a previous unsuccessful attempt
        keyStore.removeEntryIfPresent(keystoreAlias)
        val wiMdvmAuthKeys = generateKeyPair(challengeNonce, keystoreAlias)
        val wiAndroidKeyAttestationChain = keyStore.getCertificateChain(keystoreAlias).toList()
        return MdvmKeyInfo(
            keystoreAlias = keystoreAlias,
            keyPair = wiMdvmAuthKeys,
            attestationChain = wiAndroidKeyAttestationChain,
        )
    }

    override fun createAuthKeys(challengeNonce: ByteString): MdvmKeyInfo {
        return createKeys(
            challengeNonce = challengeNonce,
            keystoreAlias = WI_MDVM_AUTH_KEYS_ALIAS,
        )
    }

    override fun getExistingAuthKeys(wiMdvmAuthKeysAlias: String): KeyPair? {
        if (!keyStore.containsAlias(wiMdvmAuthKeysAlias)) return null
        val privateKey = keyStore.getKey(wiMdvmAuthKeysAlias, null) as? PrivateKey ?: return null
        val publicKey = keyStore.getCertificate(wiMdvmAuthKeysAlias)?.publicKey ?: return null
        return KeyPair(publicKey, privateKey)
    }

    override fun createReattestKeys(challengeNonce: ByteString): MdvmKeyInfo {
        return createKeys(
            challengeNonce = challengeNonce,
            keystoreAlias = WI_MDVM_REATTEST_KEYS_ALIAS,
        )
    }

    override fun deleteReattestKeys(): Boolean =
        keyStore.removeEntryIfPresent(WI_MDVM_REATTEST_KEYS_ALIAS)

    private fun KeyStore.removeEntryIfPresent(alias: String): Boolean {
        val existed = containsAlias(alias)
        if (existed) deleteEntry(alias)
        return existed
    }

    private fun generateKeyPair(challengeNonce: ByteString, keystoreAlias: String) =
        KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEY_STORE_PROVIDER_NAME
        ).apply {
            initialize(
                KeyGenParameterSpec.Builder(keystoreAlias, KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE_P256_NAME))
                    .setAttestationChallenge(challengeNonce.toByteArray())
                    .setIsStrongBoxBacked(keystoreHasStrongbox)
                    .build()
            )
        }.generateKeyPair()

    companion object {
        internal const val WI_MDVM_AUTH_KEYS_ALIAS = "wi_mdvm_auth_keys"
        internal const val WI_MDVM_REATTEST_KEYS_ALIAS = "wi_mdvm_reattest_keys"
        internal val CURVE_P256_NAME = EcCurve.P256.SECGName // "secp256r1"
        internal const val ANDROID_KEY_STORE_PROVIDER_NAME = "AndroidKeyStore"
    }
}
