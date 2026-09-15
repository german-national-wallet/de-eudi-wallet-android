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

package org.sprind.wallet.businesslogic.controller.storage

import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.controller.storage.PrefKeysImpl
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PrefKeysTest {

    @Mock
    private lateinit var prefsController: PrefsController

    private lateinit var closeable: AutoCloseable

    private val subject: PrefKeys by lazy { PrefKeysImpl(prefsController) }

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `getAlias should return stored alias for given key`() {
        // When
        val key = PrefKeys.KEY_BIOMETRIC_ALIAS
        val expectedAlias = "SavedBiometricAlias"

        whenever(prefsController.getString(key, "")).thenReturn(expectedAlias)

        val result = subject.getAlias(key)

        // Then
        assertEquals(expectedAlias, result)
        verify(prefsController).getString(key, "")
    }

    @Test
    fun `saveAlias should store the alias value for given key`() {
        // When
        val key = PrefKeys.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS
        val aliasValue = "WalletInstancePrivateKeyAlias"

        subject.saveAlias(key, aliasValue)

        // Then
        verify(prefsController).setString(key, aliasValue)
    }
}