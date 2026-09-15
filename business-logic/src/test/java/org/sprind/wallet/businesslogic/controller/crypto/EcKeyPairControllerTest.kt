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

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.businesslogic.controller.crypto.EcKeyPairControllerImpl
import org.sprind.wallet.businesslogic.controller.crypto.KeyStoreInstanceProvider
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.cert.Certificate
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.assertNull

class EcKeyPairControllerTest {

    @Mock
    private lateinit var keyStoreInstanceProvider: KeyStoreInstanceProvider

    @Mock
    private lateinit var mockKeyStore: KeyStore

    private lateinit var closeable: AutoCloseable

    private lateinit var subject: EcKeyPairControllerImpl

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)
        whenever(keyStoreInstanceProvider.createInstanceAndLoad()).thenReturn(
            mockKeyStore
        )
        subject = EcKeyPairControllerImpl(keyStoreInstanceProvider)
    }

    @After
    fun teardown() {
        closeable.close()
    }

    @Test
    fun `maybeGetEcKeyPair() return nulls for absent alias`() =
        runTest {
            // Given
            val nonExistingAlias = "non-existing alias"
            whenever(mockKeyStore.containsAlias(nonExistingAlias)).thenReturn(false)

            // When
            val result = subject.maybeGetEcKeyPair(nonExistingAlias)

            // Then
            assertNull(result)
            verify(mockKeyStore).containsAlias(nonExistingAlias)
        }

    @Test
    fun `maybeGetEcKeyPair() returns null for entry type other than PrivateKeyEntry`() =
        runTest {
            // Given
            val alias = "testAlias"

            // When
            whenever(mockKeyStore.containsAlias(alias)).thenReturn(true)
            val secretKey = KeyStore.SecretKeyEntry(
                SecretKeySpec(Random(23).nextBytes(32), "AES")
            )
            whenever(mockKeyStore.getEntry(alias, null)).thenReturn(secretKey)

            // Then
            val result = subject.maybeGetEcKeyPair(alias)
            assertNull(result)
        }

    @Test
    fun `maybeGetEcKeyPair() returns null for non-EC public keys`() =
        runTest {
            // Given
            val alias = "testAlias"
            whenever(mockKeyStore.containsAlias(alias)).thenReturn(true)
            // Generate RSA key pair (not EC)
            val rsaKeyPair = KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048)
            }.generateKeyPair()
            val mockCertificate = Mockito.mock<Certificate>()
            whenever(mockCertificate.publicKey).thenReturn(rsaKeyPair.public)
            val privateKeyEntry = PrivateKeyEntry(
                rsaKeyPair.private,
                arrayOf(mockCertificate)
            )
            whenever(mockKeyStore.getEntry(alias, null)).thenReturn(privateKeyEntry)

            // When
            val result = subject.maybeGetEcKeyPair(alias)

            // Then
            assertNull(result)
            verify(mockKeyStore, times(1)).getEntry(alias, null)
        }
}