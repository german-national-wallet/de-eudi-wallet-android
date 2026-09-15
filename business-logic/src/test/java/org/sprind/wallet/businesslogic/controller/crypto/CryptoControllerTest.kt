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

import eu.europa.ec.businesslogic.controller.crypto.CryptoControllerImpl
import eu.europa.ec.businesslogic.controller.crypto.KeystoreController
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoControllerTest {
    @Mock
    private lateinit var keystoreController: KeystoreController

    @Mock
    private lateinit var cipherInstanceProvider: CipherInstanceProvider

    @Mock
    private lateinit var mockSecretKey: SecretKey

    @Mock
    private lateinit var mockCipher: Cipher

    @Mock
    private lateinit var cryptoKeyGenerator: CryptoKeyGenerator

    private lateinit var cryptoController: CryptoControllerImpl

    private lateinit var closeable: AutoCloseable

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        cryptoController = CryptoControllerImpl(
            keystoreController = keystoreController,
            cipherInstanceProvider = cipherInstanceProvider,
            cryptoKeyGenerator = cryptoKeyGenerator,
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `getWalletInstancePrivateKeyCipher returns Cipher instance for encryption when encrypt=true is passed`() {
        // Given
        whenever(keystoreController.retrieveOrGenerateWalletInstancePrivateKeySecretKey()).thenReturn(
            mockSecretKey
        )
        whenever(cipherInstanceProvider.getNewInstance("AES/GCM/NoPadding"))
            .thenReturn(mockCipher)

        // Then
        val cipher =
            cryptoController.getWalletInstancePrivateKeyCipher(encrypt = true, ivBytes = null)

        assertNotNull(cipher)
        verify(mockCipher).init(Cipher.ENCRYPT_MODE, mockSecretKey)
    }

    @Test
    fun `getWalletInstancePrivateKeyCipher returns Cipher instance for decryption when encrypt=false is passed`() {
        // Given
        val ivBytes = "ivString".toByteArray()
        whenever(keystoreController.retrieveOrGenerateWalletInstancePrivateKeySecretKey()).thenReturn(
            mockSecretKey
        )
        whenever(cipherInstanceProvider.getNewInstance("AES/GCM/NoPadding"))
            .thenReturn(mockCipher)

        // Then
        val cipher =
            cryptoController.getWalletInstancePrivateKeyCipher(encrypt = false, ivBytes = ivBytes)

        assertNotNull(cipher)
        verify(mockCipher).init(eq(Cipher.DECRYPT_MODE), eq(mockSecretKey), any<GCMParameterSpec>())
    }

    @Test
    fun `getWalletInstanceAttestationPrivateKeyCipher returns Cipher instance for encryption when encrypt=true`() {
        // Given
        whenever(keystoreController.retrieveOrGenerateWalletInstanceAttestationPrivateKeySecretKey()).thenReturn(
            mockSecretKey
        )
        whenever(cipherInstanceProvider.getNewInstance("AES/GCM/NoPadding"))
            .thenReturn(mockCipher)

        // Then
        val cipher = cryptoController.getWalletInstanceAttestationPrivateKeyCipher(
            encrypt = true,
            ivBytes = null
        )

        assertNotNull(cipher)
        verify(mockCipher).init(Cipher.ENCRYPT_MODE, mockSecretKey)
    }

    @Test
    fun `getWalletInstanceAttestationPrivateKeyCipher returns Cipher instance for decryption when encrypt=false`() {
        // Given
        val ivBytes = "ivString".toByteArray()
        whenever(keystoreController.retrieveOrGenerateWalletInstanceAttestationPrivateKeySecretKey()).thenReturn(
            mockSecretKey
        )
        whenever(cipherInstanceProvider.getNewInstance("AES/GCM/NoPadding"))
            .thenReturn(mockCipher)

        // Then
        val cipher =
            cryptoController.getWalletInstanceAttestationPrivateKeyCipher(
                encrypt = false,
                ivBytes = ivBytes
            )

        assertNotNull(cipher)
        verify(mockCipher).init(eq(Cipher.DECRYPT_MODE), eq(mockSecretKey), any<GCMParameterSpec>())
    }

    @Test
    fun `getWalletRegistrationCipher returns Cipher instance for encryption when encrypt=true is passed`() {
        // Given
        whenever(keystoreController.retrieveOrGenerateWalletRegistrationSecretKey()).thenReturn(
            mockSecretKey
        )
        whenever(cipherInstanceProvider.getNewInstance("AES/GCM/NoPadding"))
            .thenReturn(mockCipher)

        // Then
        val cipher = cryptoController.getWalletRegistrationCipher(encrypt = true, ivBytes = null)

        assertNotNull(cipher)
        verify(mockCipher).init(Cipher.ENCRYPT_MODE, mockSecretKey)
    }

    @Test
    fun `getWalletRegistrationCipher returns Cipher instance for decryption when encrypt=false is passed`() {
        // Given
        val ivBytes = "ivString".toByteArray()
        whenever(keystoreController.retrieveOrGenerateWalletRegistrationSecretKey()).thenReturn(
            mockSecretKey
        )
        whenever(cipherInstanceProvider.getNewInstance("AES/GCM/NoPadding"))
            .thenReturn(mockCipher)

        // Then
        val cipher =
            cryptoController.getWalletRegistrationCipher(encrypt = false, ivBytes = ivBytes)

        assertNotNull(cipher)
        verify(mockCipher).init(eq(Cipher.DECRYPT_MODE), eq(mockSecretKey), any<GCMParameterSpec>())
    }

    @Test
    fun `encryptDecryptData successfully encrypts or decrypts data when input is correct`() {
        // Given
        val inputData = "Test Data".toByteArray()
        val encryptedData = "EncryptedData".toByteArray()
        whenever(mockCipher.doFinal(inputData)).thenReturn(encryptedData)

        // Then
        val result = cryptoController.encryptDecryptData(mockCipher, inputData)

        assertArrayEquals(encryptedData, result)
    }

    @Test
    fun `encryptDecryptData returns empty byte array if cipher is null`() {
        // Given
        val inputData = "Test Data".toByteArray()

        // Then
        val result = cryptoController.encryptDecryptData(null, inputData)

        assertArrayEquals(ByteArray(0), result)
    }

    @Test
    fun `getCipher returns null on exception when cipherInstanceProvider,getNewInstance fails`() {
        // Given
        whenever(cipherInstanceProvider.getNewInstance("AES/GCM/NoPadding"))
            .thenThrow(RuntimeException("Cipher instance error"))

        // Then
        val cipher =
            cryptoController.getWalletInstancePrivateKeyCipher(encrypt = true, ivBytes = null)

        assertNull(cipher)
    }
}