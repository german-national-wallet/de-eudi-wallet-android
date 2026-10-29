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
import eu.europa.ec.businesslogic.controller.log.LogController
import org.sprind.wallet.businesslogic.controller.crypto.CipherInstanceProvider
import org.sprind.wallet.businesslogic.controller.crypto.CryptoError
import org.sprind.wallet.businesslogic.controller.crypto.CryptoKeyGenerator
import org.sprind.wallet.businesslogic.controller.crypto.toCryptoError
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

typealias GUID = String

/**
 * AES-GCM operations on Android Keystore keys. Every operation returns a [Result] whose failure
 * is a [CryptoError]; callers must fail closed on it.
 */
interface CryptoController {
    fun generateCodeVerifier(): String

    /**
     * Returns a cipher on the biometric key: `ENCRYPT_MODE` if [encrypt], otherwise
     * `DECRYPT_MODE` with [ivBytes] as the GCM IV.
     */
    fun getCipher(
        encrypt: Boolean = false,
        ivBytes: ByteArray? = null,
    ): Result<Cipher>

    /**
     * Returns the [Cipher] needed to create the [androidx.biometric.BiometricPrompt.CryptoObject]
     * for biometric authentication.
     * [encrypt] should be set to true if the cipher should encrypt, false otherwise.
     * [ivBytes] is needed only for decryption to create the [GCMParameterSpec].
     */
    fun getBiometricCipher(encrypt: Boolean = false, ivBytes: ByteArray? = null): Result<Cipher>

    /**
     * Returns the [Cipher] needed to create the wallet registration key crypto object
     * [encrypt] should be set to true if the cipher should encrypt, false otherwise.
     * [ivBytes] is needed only for decryption to create the [GCMParameterSpec].
     */
    fun getWalletRegistrationCipher(encrypt: Boolean = false, ivBytes: ByteArray? = null): Result<Cipher>

    /**
     * Returns the [Cipher] needed to create the wallet instance private key crypto object
     * [encrypt] should be set to true if the cipher should encrypt, false otherwise.
     * [ivBytes] is needed only for decryption to create the [GCMParameterSpec].
     */
    fun getWalletInstancePrivateKeyCipher(
        encrypt: Boolean = false,
        ivBytes: ByteArray? = null
    ): Result<Cipher>

    /**
     * Returns the [Cipher] needed to create the wallet instance attestation private key crypto object
     * [encrypt] should be set to true if the cipher should encrypt, false otherwise.
     * [ivBytes] is needed only for decryption to create the [GCMParameterSpec].
     */
    fun getWalletInstanceAttestationPrivateKeyCipher(
        encrypt: Boolean = false,
        ivBytes: ByteArray? = null
    ): Result<Cipher>

    /** Encrypts or decrypts [byteArray], depending on the mode [cipher] was initialised in. */
    fun encryptDecryptData(cipher: Cipher, byteArray: ByteArray): Result<ByteArray>

    /** Same as [encryptDecryptData], for a cipher unlocked by a biometric prompt. */
    fun encryptDecryptBiometric(cipher: Cipher, byteArray: ByteArray): Result<ByteArray>

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
    private val logController: LogController,
) : CryptoController {

    private val logTag = javaClass.simpleName

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

    override fun getCipher(encrypt: Boolean, ivBytes: ByteArray?): Result<Cipher> =
        getBiometricCipher(encrypt, ivBytes)

    override fun getBiometricCipher(encrypt: Boolean, ivBytes: ByteArray?): Result<Cipher> =
        getCipher(encrypt, ivBytes) { keystoreController.retrieveOrGenerateBiometricSecretKey() }

    override fun getWalletInstancePrivateKeyCipher(encrypt: Boolean, ivBytes: ByteArray?) =
        getCipher(encrypt, ivBytes) {
            keystoreController.retrieveOrGenerateWalletInstancePrivateKeySecretKey()
        }

    override fun getWalletRegistrationCipher(encrypt: Boolean, ivBytes: ByteArray?) =
        getCipher(encrypt, ivBytes) {
            keystoreController.retrieveOrGenerateWalletRegistrationSecretKey()
        }

    override fun getWalletInstanceAttestationPrivateKeyCipher(
        encrypt: Boolean,
        ivBytes: ByteArray?
    ) =
        getCipher(encrypt, ivBytes) {
            keystoreController.retrieveOrGenerateWalletInstanceAttestationPrivateKeySecretKey()
        }

    override fun encryptDecryptData(cipher: Cipher, byteArray: ByteArray): Result<ByteArray> =
        doFinal(cipher, byteArray)

    override fun encryptDecryptBiometric(cipher: Cipher, byteArray: ByteArray): Result<ByteArray> =
        doFinal(cipher, byteArray)

    private fun getCipher(
        encrypt: Boolean,
        ivBytes: ByteArray?,
        secretKey: () -> SecretKey?,
    ): Result<Cipher> = cryptoResult {
        val key = secretKey() ?: throw CryptoError.KeyNotFound()
        cipherInstanceProvider.getNewInstance(AES_EXTERNAL_TRANSFORMATION).apply {
            if (encrypt) {
                init(Cipher.ENCRYPT_MODE, key)
            } else {
                if (ivBytes == null || ivBytes.isEmpty()) throw CryptoError.InvalidParameters()
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(IV_SIZE, ivBytes))
            }
        }
    }

    private fun doFinal(cipher: Cipher, byteArray: ByteArray): Result<ByteArray> =
        cryptoResult { cipher.doFinal(byteArray) }

    private inline fun <T> cryptoResult(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            val error = e.toCryptoError()
            logController.w(logTag) { "Cipher operation failed: ${error::class.simpleName}" }
            Result.failure(error)
        }

    override fun removeWalletRegistrationData() {
        keystoreController.removeWalletKeysAndAliases()
    }
}
