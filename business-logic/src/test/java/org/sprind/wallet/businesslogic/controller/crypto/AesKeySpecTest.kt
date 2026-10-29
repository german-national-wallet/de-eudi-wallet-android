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
import eu.europa.ec.businesslogic.controller.crypto.KeystoreControllerImpl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AesKeySpecTest {

    @Test
    fun `encrypt decrypt key spec requests a 256 bit AES-GCM key`() {
        val spec = CryptoKeyGeneratorImpl.encryptDecryptAesKeySpec(ALIAS)

        assertAesGcm256(spec)
        assertFalse(spec.isUserAuthenticationRequired)
    }

    @Test
    fun `biometric key spec requests a 256 bit AES-GCM key bound to the user`() {
        val spec = KeystoreControllerImpl.biometricSecretKeySpec(ALIAS)

        assertAesGcm256(spec)
        assertTrue(spec.isUserAuthenticationRequired)
        assertTrue(spec.isInvalidatedByBiometricEnrollment)
    }

    @Test
    fun `only TEE and StrongBox security levels count as secure hardware`() {
        assertTrue(isSecureHardwareLevel(KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT))
        assertTrue(isSecureHardwareLevel(KeyProperties.SECURITY_LEVEL_STRONGBOX))
        assertTrue(isSecureHardwareLevel(KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE))
        assertFalse(isSecureHardwareLevel(KeyProperties.SECURITY_LEVEL_SOFTWARE))
        assertFalse(isSecureHardwareLevel(KeyProperties.SECURITY_LEVEL_UNKNOWN))
    }

    private fun assertAesGcm256(spec: KeyGenParameterSpec) {
        assertEquals(256, spec.keySize)
        assertEquals(ALIAS, spec.keystoreAlias)
        assertEquals(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT, spec.purposes)
        assertArrayEquals(arrayOf(KeyProperties.BLOCK_MODE_GCM), spec.blockModes)
        assertArrayEquals(arrayOf(KeyProperties.ENCRYPTION_PADDING_NONE), spec.encryptionPaddings)
    }

    private companion object {
        const val ALIAS = "test_alias"
    }
}
