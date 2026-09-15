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

package org.sprind.wallet.authenticationlogic.storage

import com.google.gson.Gson
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.model.RwscaRegistration

private const val RWSCA_REGISTRATION_KEY = "RwscaRegistration"
private const val RWSCA_PIN_INITIALIZED_KEY = "RwscaPinInitialized"
private const val RWSCA_PIN_SALT_KEY = "RwscaPinSalt"

class RwscaStorageControllerTest {

    @Mock
    private lateinit var prefsController: PrefsController

    private val subject: RwscaStorageControllerImpl by lazy {
        RwscaStorageControllerImpl(prefsController, Gson())
    }

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `getRwscaRegistration returns null when nothing is stored`() {
        whenever(prefsController.getString(RWSCA_REGISTRATION_KEY, "")).thenReturn("")

        assertNull(subject.getRwscaRegistration())
    }

    @Test
    fun `saveRwscaRegistration stores JSON under the correct key`() {
        val registration = RwscaRegistration(rwsca_account_id = "test-account-id")

        subject.saveRwscaRegistration(registration)

        val captor = argumentCaptor<String>()
        verify(prefsController).setString(eq(RWSCA_REGISTRATION_KEY), captor.capture())
        whenever(prefsController.getString(RWSCA_REGISTRATION_KEY, "")).thenReturn(captor.firstValue)
        assertEquals(registration, subject.getRwscaRegistration())
    }

    @Test
    fun `getRwscaRegistration returns null when stored data is corrupt`() {
        whenever(prefsController.getString(RWSCA_REGISTRATION_KEY, ""))
            .thenReturn("not valid json{")

        assertNull(subject.getRwscaRegistration())
    }

    @Test
    fun `clearRwscaRegistration calls prefsController clear with the correct key`() {
        subject.clearRwscaRegistration()

        verify(prefsController).clear(RWSCA_REGISTRATION_KEY)
    }

    @Test
    fun `isPinInitialized returns false when nothing is stored`() {
        whenever(prefsController.getBool(RWSCA_PIN_INITIALIZED_KEY, false)).thenReturn(false)

        assertEquals(false, subject.isPinInitialized())
    }

    @Test
    fun `savePinInitialized stores true under the correct key`() {
        subject.savePinInitialized()

        verify(prefsController).setBool(RWSCA_PIN_INITIALIZED_KEY, true)
    }

    @Test
    fun `clearPinInitialized calls prefsController clear with the correct key`() {
        subject.clearPinInitialized()

        verify(prefsController).clear(RWSCA_PIN_INITIALIZED_KEY)
    }

    @Test
    fun `getPinSalt returns null when nothing is stored`() {
        whenever(prefsController.getString(RWSCA_PIN_SALT_KEY, "")).thenReturn("")

        assertNull(subject.getPinSalt())
    }

    @Test
    fun `savePinSalt stores base64 encoding of salt bytes under the correct key`() {
        val salt = "some-salt-bytes".encodeUtf8()

        subject.savePinSalt(salt)

        verify(prefsController).setString(RWSCA_PIN_SALT_KEY, salt.base64())
    }

    @Test
    fun `getPinSalt returns the base64-decoding of the saved value`() {
        val salt = "some-salt-bytes".encodeUtf8()
        whenever(prefsController.getString(RWSCA_PIN_SALT_KEY, "")).thenReturn(salt.base64())

        assertEquals(salt, subject.getPinSalt())
    }

    @Test
    fun `savePinSalt and getPinSalt round-trip preserves the original bytes`() {
        val salt = "some-salt-bytes".encodeUtf8()

        subject.savePinSalt(salt)

        val captor = argumentCaptor<String>()
        verify(prefsController).setString(eq(RWSCA_PIN_SALT_KEY), captor.capture())
        whenever(prefsController.getString(RWSCA_PIN_SALT_KEY, "")).thenReturn(captor.firstValue)
        assertEquals(salt, subject.getPinSalt())
    }

    @Test
    fun `clearPinSalt calls prefsController clear with the correct key`() {
        subject.clearPinSalt()

        verify(prefsController).clear(RWSCA_PIN_SALT_KEY)
    }

}
