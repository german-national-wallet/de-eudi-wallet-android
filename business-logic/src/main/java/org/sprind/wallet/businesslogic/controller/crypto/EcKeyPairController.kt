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
import org.multipaz.crypto.EcCurve
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore.PrivateKeyEntry
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Offers generation and access to EC Key Pairs.
 */
interface EcKeyPairController {
    /**
     * Generates a keystore-backed EC key pair which can be used for the
     * given purposes.
     *
     * @param keystoreAlias The alias under which the key is stored.
     * @param purposes from [KeyProperties].PURPOSE_*, for example
     *        `KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY`
     */
    fun generateEcKeyPair(keystoreAlias: String, purposes: Int)

    /**
     * @return whether an EC KeyPair generated with
     *         [generateEcKeyPair(keystoreAlias, purposes)] exists.
     */
    fun hasEcKeyPair(keystoreAlias: String): Boolean

    /**
     * Obtains the EC KeyPair that was previously generated with
     * [generateEcKeyPair(keystoreAlias, purposes)]. Since the key
     * lives in Android KeyStore and not in app memory, the PrivateKey part
     * is only a handle to the key.
     *
     * @param keystoreAlias The alias under which the key is stored.
     * @throws IllegalStateException if [!hasEcKeyPair(keystoreAlias)]
     */
    fun getEcKeyPair(keystoreAlias: String): KeyPair
}

private const val ANDROID_KEY_STORE_PROVIDER_NAME = "AndroidKeyStore"

/**
 * An implementation of [EcKeyPairController] backed by Android KeyStore.
 */
internal class EcKeyPairControllerImpl(private val keyStoreInstanceProvider: KeyStoreInstanceProvider) : EcKeyPairController {

    private val androidKeyStore by lazy { keyStoreInstanceProvider.createInstanceAndLoad() }

    internal fun maybeGetEcKeyPair(keystoreAlias: String): KeyPair? {
        if (!androidKeyStore.containsAlias(keystoreAlias)) { return null }
        val entry = androidKeyStore.getEntry(
            keystoreAlias,
            null
        )
        if (entry !is PrivateKeyEntry) { return null }
        val publicKey = entry.certificate.publicKey
        if (publicKey !is ECPublicKey) { return null }
        return KeyPair(publicKey, entry.privateKey)
    }

    override fun hasEcKeyPair(keystoreAlias: String): Boolean =
        maybeGetEcKeyPair(keystoreAlias) != null

    override fun getEcKeyPair(keystoreAlias: String): KeyPair =
        maybeGetEcKeyPair(keystoreAlias) ?: throw IllegalStateException("Keystore entry expected, but missing: $keystoreAlias")

    override fun generateEcKeyPair(keystoreAlias: String, purposes: Int) {
        if (androidKeyStore.containsAlias(keystoreAlias)) {
            androidKeyStore.deleteEntry(keystoreAlias)
        }
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEY_STORE_PROVIDER_NAME,
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keystoreAlias,
            purposes,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE_P256_NAME))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        keyPairGenerator.initialize(keyGenParameterSpec)
        keyPairGenerator.generateKeyPair()
    }

    companion object {
        private val CURVE_P256_NAME = EcCurve.P256.SECGName // "secp256r1"
    }
}