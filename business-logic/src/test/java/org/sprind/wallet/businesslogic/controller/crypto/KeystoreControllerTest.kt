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

import eu.europa.ec.businesslogic.controller.crypto.KeystoreControllerImpl
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import org.sprind.wallet.businesslogic.util.RandomUUIDGenerator
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyStore
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeystoreControllerTest {

    @Mock
    private lateinit var prefKeys: PrefKeys

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var keyStoreInstanceProvider: KeyStoreInstanceProvider

    @Mock
    private lateinit var mockKeyStore: KeyStore

    @Mock
    private lateinit var mockSecretKey: SecretKey

    @Mock
    private lateinit var cryptoKeyGenerator: CryptoKeyGenerator

    private lateinit var closeable: AutoCloseable

    private lateinit var subject: KeystoreControllerImpl

    @Before
    fun setup() {
        closeable = MockitoAnnotations.openMocks(this)

        whenever(keyStoreInstanceProvider.createInstanceAndLoad()).thenReturn(
            mockKeyStore
        )

        doNothing().whenever(mockKeyStore).load(null)

        subject = KeystoreControllerImpl(
            prefKeys = prefKeys,
            logController = logController,
            keyStoreInstanceProvider = keyStoreInstanceProvider,
            cryptoKeyGenerator,
        )
    }

    @After
    fun teardown() {
        closeable.close()
    }

    @Test
    fun `key store should be instantiated and loaded initially`() {
        verify(keyStoreInstanceProvider).createInstanceAndLoad()
    }

    @Test
    fun `retrieveOrGenerateWalletInstancePrivateKeySecretKey should generate new key if alias is empty`() =
        runTest {
            // Given
            val testAlias = "wi_priv_key"

            whenever(prefKeys.getAlias(PrefKeys.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS)).thenReturn("")
            whenever(mockKeyStore.getKey(testAlias, null)).thenReturn(mockSecretKey)
            doNothing().whenever(prefKeys)
                .saveAlias(PrefKeys.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS, testAlias)
            doNothing().whenever(cryptoKeyGenerator).generateEncryptDecryptAesKey(
                provider = "AndroidKeyStore",
                keyStoreAlias = testAlias
            )

            // Then
            val result = subject.retrieveOrGenerateWalletInstancePrivateKeySecretKey()

            assertNotNull(result)
            assertEquals(mockSecretKey, result)
            verify(prefKeys).getAlias(PrefKeys.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS)
            verify(prefKeys).saveAlias(PrefKeys.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS, testAlias)
            verify(cryptoKeyGenerator).generateEncryptDecryptAesKey(
                provider = "AndroidKeyStore",
                keyStoreAlias = testAlias
            )
        }


    @Test
    fun `retrieveOrGenerateWalletInstancePrivateKeySecretKey should return existing key if alias is found`() =
        runTest {
            // Given
            val testAlias = "wi_priv_key"

            whenever(prefKeys.getAlias(PrefKeys.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS)).thenReturn(
                testAlias
            )
            whenever(mockKeyStore.getKey(testAlias, null)).thenReturn(mockSecretKey)

            // Then
            val result = subject.retrieveOrGenerateWalletInstancePrivateKeySecretKey()

            assertNotNull(result)
            assertEquals(mockSecretKey, result)
            verify(prefKeys).getAlias(PrefKeys.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS)
            verify(prefKeys, never()).saveAlias(any(), any())
            verify(cryptoKeyGenerator, never()).generateEncryptDecryptAesKey(any(), any())
        }

    @Test
    fun `retrieveOrGenerateWalletInstanceAttestationPrivateKeySecretKey should generate new key if alias is empty`() =
        runTest {
            // Given
            val testAlias = "wia_priv_key"

            whenever(prefKeys.getAlias(PrefKeys.KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS)).thenReturn(
                ""
            )
            whenever(mockKeyStore.getKey(testAlias, null)).thenReturn(mockSecretKey)
            doNothing().whenever(prefKeys)
                .saveAlias(PrefKeys.KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS, testAlias)
            doNothing().whenever(cryptoKeyGenerator).generateEncryptDecryptAesKey(
                provider = "AndroidKeyStore",
                keyStoreAlias = testAlias
            )

            // Then
            val result = subject.retrieveOrGenerateWalletInstanceAttestationPrivateKeySecretKey()

            assertNotNull(result)
            assertEquals(mockSecretKey, result)
            verify(prefKeys).getAlias(PrefKeys.KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS)
            verify(prefKeys).saveAlias(
                PrefKeys.KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS,
                testAlias
            )
            verify(cryptoKeyGenerator).generateEncryptDecryptAesKey(
                keyStoreAlias = testAlias,
                provider = "AndroidKeyStore"
            )
        }

    @Test
    fun `retrieveOrGenerateWalletInstanceAttestationPrivateKeySecretKey should return existing key if alias is found`() =
        runTest {
            // Given
            val testAlias = "wia_priv_key"

            whenever(prefKeys.getAlias(PrefKeys.KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS)).thenReturn(
                testAlias
            )
            whenever(mockKeyStore.getKey(testAlias, null)).thenReturn(mockSecretKey)

            // Then
            val result = subject.retrieveOrGenerateWalletInstanceAttestationPrivateKeySecretKey()

            assertNotNull(result)
            assertEquals(mockSecretKey, result)
            verify(prefKeys).getAlias(PrefKeys.KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS)
            verify(prefKeys, never()).saveAlias(any(), any())
            verify(cryptoKeyGenerator, never()).generateEncryptDecryptAesKey(any(), any())
        }

}