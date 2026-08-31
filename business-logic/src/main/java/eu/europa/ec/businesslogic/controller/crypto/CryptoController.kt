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

import android.util.Base64
import org.sprind.wallet.businesslogic.controller.crypto.CipherInstanceProvider
import org.sprind.wallet.businesslogic.controller.crypto.CryptoKeyGenerator
import java.security.KeyStore.PrivateKeyEntry
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

typealias GUID = String

interface CryptoController {
    fun generateCodeVerifier(): String


    /**
     * Retrieves a [Cipher] instance configured for either encryption or decryption.
     *
     * This function initializes a [Cipher] object using the AES/GCM/NoPadding transformation.
     * The behavior of the cipher (encryption or decryption) is determined by the [encrypt] parameter.
     *
     * - If [encrypt] is `true`, the cipher is initialized in `Cipher.ENCRYPT_MODE`.
     * - If [encrypt] is `false`, the cipher is initialized in `Cipher.DECRYPT_MODE`, and the
     *   [ivBytes] parameter is required to provide the Initialization Vector (IV) for the
     *   GCMParameterSpec.
     *
     * The secret key used for cipher initialization is retrieved or generated via the
     * `keystoreController`.
     *
     * @param encrypt A [Boolean] indicating whether the cipher should be initialized for
     * encryption (`true`) or decryption (`false`). Defaults to `false`.
     * @param ivBytes An optional [ByteArray] containing the Initialization Vector. This is
     * required only when `encrypt` is `false` (i.e., for decryption).
     * @param userAuthenticationRequired A [Boolean] indicating if user authentication is
     * required for the cryptographic operation. Defaults to `true`.
     * @return A configured [Cipher] instance if initialization is successful, or `null` if an
     * exception occurs during initialization.
     */
    fun getCipher(
        encrypt: Boolean = false,
        ivBytes: ByteArray? = null,
        userAuthenticationRequired: Boolean = true
    ): Cipher?

    /**
     * Returns the [Cipher] needed to create the [androidx.biometric.BiometricPrompt.CryptoObject]
     * for biometric authentication.
     * [encrypt] should be set to true if the cipher should encrypt, false otherwise.
     * [ivBytes] is needed only for decryption to create the [GCMParameterSpec].
     */
    fun getBiometricCipher(encrypt: Boolean = false, ivBytes: ByteArray? = null): Cipher?

    /**
     * Returns the [Cipher] needed to create the wallet registration key crypto object
     * [encrypt] should be set to true if the cipher should encrypt, false otherwise.
     * [ivBytes] is needed only for decryption to create the [GCMParameterSpec].
     */
    fun getWalletRegistrationCipher(encrypt: Boolean = false, ivBytes: ByteArray? = null): Cipher?

    /**
     * Returns the [Cipher] needed to create the wallet instance private key crypto object
     * [encrypt] should be set to true if the cipher should encrypt, false otherwise.
     * [ivBytes] is needed only for decryption to create the [GCMParameterSpec].
     */
    fun getWalletInstancePrivateKeyCipher(
        encrypt: Boolean = false,
        ivBytes: ByteArray? = null
    ): Cipher?

    /**
     * Returns the [Cipher] needed to create the wallet instance attestation private key crypto object
     * [encrypt] should be set to true if the cipher should encrypt, false otherwise.
     * [ivBytes] is needed only for decryption to create the [GCMParameterSpec].
     */
    fun getWalletInstanceAttestationPrivateKeyCipher(
        encrypt: Boolean = false,
        ivBytes: ByteArray? = null
    ): Cipher?

    fun encryptDecryptData(cipher: Cipher?, byteArray: ByteArray): ByteArray

    /**
     * Returns the [ByteArray] after the encryption/decryption from the given [Cipher].
     * [cipher] the biometric cipher needed. This can be null but then an empty [ByteArray] is
     * returned.
     * [byteArray] that needed to be encrypted or decrypted (Depending always on [Cipher] provided.
     */
    fun encryptDecryptBiometric(cipher: Cipher?, byteArray: ByteArray): ByteArray

    /**
     * This method will remove all private keys and aliases created during wallet registration
     * * TODO: this is a quick fix for the issue WD-2191
     */
    fun removeWalletRegistrationData()
}

internal class CryptoControllerImpl(
    private val keystoreController: KeystoreController,
    private val cipherInstanceProvider: CipherInstanceProvider,
    private val cryptoKeyGenerator: CryptoKeyGenerator,
) : CryptoController {

    companion object {
        private const val AES_EXTERNAL_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 128
        const val MAX_GUID_LENGTH = 64
    }

    override fun generateCodeVerifier(): String {
        val code = ByteArray(32)
        SecureRandom().apply {
            nextBytes(code)
        }
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    override fun getCipher(
        encrypt: Boolean,
        ivBytes: ByteArray?,
        userAuthenticationRequired: Boolean
    ): Cipher? =
        try {
            Cipher.getInstance(AES_EXTERNAL_TRANSFORMATION).apply {
                if (encrypt) {
                    init(
                        Cipher.ENCRYPT_MODE,
                        keystoreController.retrieveOrGenerateBiometricSecretKey()
                    )
                } else {
                    init(
                        Cipher.DECRYPT_MODE,
                        keystoreController.retrieveOrGenerateBiometricSecretKey(),
                        GCMParameterSpec(IV_SIZE, ivBytes)
                    )
                }
            }
        } catch (_: Exception) {
            null
        }

    override fun getWalletInstancePrivateKeyCipher(encrypt: Boolean, ivBytes: ByteArray?) =
        getCipher(
            keystoreController.retrieveOrGenerateWalletInstancePrivateKeySecretKey(),
            encrypt,
            ivBytes
        )

    override fun getWalletRegistrationCipher(encrypt: Boolean, ivBytes: ByteArray?): Cipher? =
        getCipher(
            keystoreController.retrieveOrGenerateWalletRegistrationSecretKey(),
            encrypt,
            ivBytes
        )

    override fun getWalletInstanceAttestationPrivateKeyCipher(
        encrypt: Boolean,
        ivBytes: ByteArray?
    ) =
        getCipher(
            keystoreController.retrieveOrGenerateWalletInstanceAttestationPrivateKeySecretKey(),
            encrypt,
            ivBytes
        )

    override fun encryptDecryptData(cipher: Cipher?, byteArray: ByteArray): ByteArray =
        cipher?.doFinal(byteArray) ?: ByteArray(0)

    override fun getBiometricCipher(encrypt: Boolean, ivBytes: ByteArray?): Cipher? =
        try {
            Cipher.getInstance(AES_EXTERNAL_TRANSFORMATION).apply {
                if (encrypt) {
                    init(
                        Cipher.ENCRYPT_MODE,
                        keystoreController.retrieveOrGenerateBiometricSecretKey()
                    )
                } else {
                    init(
                        Cipher.DECRYPT_MODE,
                        keystoreController.retrieveOrGenerateBiometricSecretKey(),
                        GCMParameterSpec(IV_SIZE, ivBytes)
                    )
                }
            }
        } catch (e: Exception) {
            null
        }

    private fun getCipher(secretKey: SecretKey?, encrypt: Boolean, ivBytes: ByteArray?): Cipher? =
        try {
            cipherInstanceProvider.getNewInstance(AES_EXTERNAL_TRANSFORMATION).apply {
                if (encrypt) {
                    init(
                        Cipher.ENCRYPT_MODE,
                        secretKey
                    )
                } else {
                    init(
                        Cipher.DECRYPT_MODE,
                        secretKey,
                        GCMParameterSpec(IV_SIZE, ivBytes)
                    )
                }
            }
        } catch (e: Exception) {
            null
        }

    override fun encryptDecryptBiometric(cipher: Cipher?, byteArray: ByteArray): ByteArray {
        return cipher?.doFinal(byteArray) ?: ByteArray(0)
    }

    override fun removeWalletRegistrationData() {
        keystoreController.removeWalletKeysAndAliases()
    }
}