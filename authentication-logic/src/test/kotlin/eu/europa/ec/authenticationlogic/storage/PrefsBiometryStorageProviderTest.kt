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

package eu.europa.ec.authenticationlogic.storage

import com.google.gson.Gson
import eu.europa.ec.authenticationlogic.model.BiometricAuthentication
import eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class PrefsBiometryStorageProviderTest {

    @Mock
    private lateinit var prefsController: EncryptedPrefsController

    private val subject by lazy { PrefsBiometryStorageProvider(prefsController) }

    private lateinit var closeable: AutoCloseable

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `getBiometricAuthentication returns the stored data`() {
        val expected = BiometricAuthentication(randomString = "random", encryptedString = "enc", ivString = "iv")
        whenever(prefsController.getString(KEY, "")).thenReturn(Gson().toJson(expected))

        assertEquals(expected, subject.getBiometricAuthentication())
    }

    @Test
    fun `getBiometricAuthentication returns null when nothing is stored`() {
        whenever(prefsController.getString(KEY, "")).thenReturn("")

        assertNull(subject.getBiometricAuthentication())
    }

    @Test
    fun `getBiometricAuthentication returns null when stored data is corrupt`() {
        whenever(prefsController.getString(KEY, "")).thenReturn("not valid json{")

        assertNull(subject.getBiometricAuthentication())
    }

    @Test
    fun `getBiometricAuthentication propagates a failure to read the preferences`() {
        // E.g. the auth-bound master key is unavailable. Returning null here would look like
        // "no biometric data" and reset the biometric setup.
        val failure = IllegalStateException("preferences locked")
        whenever(prefsController.getString(KEY, "")).thenThrow(failure)

        val thrown = assertThrows(IllegalStateException::class.java) { subject.getBiometricAuthentication() }
        assertSame(failure, thrown)
    }

    private companion object {
        const val KEY = "BiometricAuthentication"
    }
}
