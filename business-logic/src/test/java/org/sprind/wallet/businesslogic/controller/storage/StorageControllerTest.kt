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

import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.sprind.wallet.businesslogic.controller.storage.AppDataCompatibility
import org.sprind.wallet.businesslogic.controller.storage.CURRENT_APP_DATA_VERSION
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageControllerTest {
    private val testAppDataVersion = 5

    @Mock
    private lateinit var prefsController: PrefsController

    private lateinit var appDataCompatibility: AppDataCompatibility

    private val persistedAppDataVersion = IntBox(null)

    @Before
    fun before() {
        prefsController = makeFakePrefsController(persistedAppDataVersion)
        appDataCompatibility = AppDataCompatibility(prefsController, testAppDataVersion)
    }

    @Test
    fun `app data version is valid`() {
        assertTrue(AppDataCompatibility.NOT_INITIALIZED_VERSION < CURRENT_APP_DATA_VERSION)
    }

    @Test
    fun `NOT_INITIALIZED_VERSION value has not changed`() {
        assertEquals(0, AppDataCompatibility.NOT_INITIALIZED_VERSION)
    }

    @Test
    fun `on first run, we shouldn't clear app data but store the current version`() {
        assertFalse(appDataCompatibility.shouldClearAppData())
        assertEquals(testAppDataVersion, persistedAppDataVersion.value)
    }

    @Test
    fun `when app data version hasn't changed, we shouldn't clear app data`() {
        persistedAppDataVersion.value = testAppDataVersion
        assertFalse(appDataCompatibility.shouldClearAppData())
        assertEquals(testAppDataVersion, persistedAppDataVersion.value)
    }

    @Test
    fun `when app data version changes, we should clear app data but not update the stored version`() {
        persistedAppDataVersion.value = testAppDataVersion - 1
        assertTrue(appDataCompatibility.shouldClearAppData())
        assertEquals(testAppDataVersion - 1, persistedAppDataVersion.value)
    }

    class IntBox(var value: Int?)

    companion object {
        /**
         * A fake PrefsController that reads/writes currentVersion.value for
         * reads/write access to the AppDataCompatibility pref entry.
         */
        fun makeFakePrefsController(currentVersion: IntBox): PrefsController {
            val result = mock(PrefsController::class.java)
            whenever(
                result.getInt(
                    eq(AppDataCompatibility.PREF_KEY),
                    eq(AppDataCompatibility.NOT_INITIALIZED_VERSION)
                )
            )
                .thenAnswer({ currentVersion.value })
            doAnswer { invocation ->
                currentVersion.value = invocation.getArgument<Int>(1)
                null
            }.whenever(result).setInt(eq(AppDataCompatibility.PREF_KEY), any())
            return result
        }
    }

}