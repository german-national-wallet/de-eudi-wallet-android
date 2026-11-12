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

package org.sprind.wallet.businesslogic.config

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.businesslogic.controller.storage.EncryptedPrefsUnavailableException

class PrefsDelegateTest {

    @Mock
    private lateinit var prefsController: EncryptedPrefsController

    @Mock
    private lateinit var logController: LogController

    private lateinit var closeable: AutoCloseable

    private val subject by lazy { DebugConfigOverridesControllerImpl(prefsController, logController) }

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    @After
    fun tearDown() {
        closeable.close()
    }

    @Test
    fun `returns the stored override`() {
        whenever(prefsController.contains(KEY)).thenReturn(true)
        whenever(prefsController.getString(KEY, "")).thenReturn("https://override.example")

        assertEquals("https://override.example", subject.walletHostUrl)
    }

    @Test
    fun `returns null when no override is stored`() {
        whenever(prefsController.contains(KEY)).thenReturn(false)

        assertNull(subject.walletHostUrl)
    }

    @Test
    fun `falls back to null when the encrypted preferences cannot be read`() {
        // E.g. the auth-bound master key is locked on a background (FCM) start.
        val cause = Exception("keystore")
        listOf(
            EncryptedPrefsUnavailableException.Locked(cause),
            EncryptedPrefsUnavailableException.Transient(cause),
            EncryptedPrefsUnavailableException.InsecureDevice(cause),
            EncryptedPrefsUnavailableException.KeysetLost(cause),
        ).forEach { failure ->
            doAnswer { throw failure }.whenever(prefsController).contains(KEY)
            assertNull(subject.walletHostUrl)
        }
    }

    @Test
    fun `logs each unreadable read at debug level`() {
        doAnswer { throw EncryptedPrefsUnavailableException.Locked(Exception("keystore")) }
            .whenever(prefsController).contains(KEY)

        subject.walletHostUrl
        subject.walletHostUrl

        verify(logController, times(2)).d(any(), any())
    }

    @Test
    fun `logs nothing when the override is readable`() {
        whenever(prefsController.contains(KEY)).thenReturn(false)

        subject.walletHostUrl

        verify(logController, never()).d(any(), any())
    }

    @Test
    fun `propagates unrelated failures`() {
        whenever(prefsController.contains(KEY)).thenThrow(IllegalStateException("bug"))

        assertThrows(IllegalStateException::class.java) { subject.walletHostUrl }
    }

    @Test
    fun `setting an override writes it synchronously and clearing removes it`() {
        subject.walletHostUrl = "https://override.example"
        verify(prefsController).setStringSync(KEY, "https://override.example")

        subject.walletHostUrl = null
        verify(prefsController).clear(KEY)
    }

    private companion object {
        const val KEY = DebugConfigOverridesControllerImpl.KEY_WALLET_HOST_URL
    }
}
