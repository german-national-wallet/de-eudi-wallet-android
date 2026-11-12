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

import android.app.ActivityManager
import android.content.Context
import eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StorageControllerImplTest {

    private val activityManager: ActivityManager = mock(ActivityManager::class.java)
    private val context: Context = mock(Context::class.java).also {
        whenever(it.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(activityManager)
    }
    private val prefsController: EncryptedPrefsController = mock(EncryptedPrefsController::class.java)
    private val onBeforeWipe: () -> Unit = mock()

    private fun subject() = StorageControllerImpl(context, prefsController)

    @Test
    fun `onInitializeStorage checks the app data version only once per instance`() {
        whenever(prefsController.getInt(eq(AppDataCompatibility.PREF_KEY), any()))
            .thenReturn(CURRENT_APP_DATA_VERSION)
        val subject = subject()

        subject.onInitializeStorage()
        subject.onInitializeStorage()
        subject.onInitializeStorage()

        verify(prefsController, times(1)).getInt(eq(AppDataCompatibility.PREF_KEY), any())
    }

    @Test
    fun `onInitializeStorage runs the check again after it failed`() {
        // A failure that is not a lost keyset (e.g. the user has not authenticated recently)
        // propagates to the caller, and the check must not count as done for this process.
        val failure = IllegalStateException("master key not usable yet")
        whenever(prefsController.getInt(eq(AppDataCompatibility.PREF_KEY), any()))
            .thenThrow(failure)
            .thenReturn(CURRENT_APP_DATA_VERSION)
        val subject = subject()

        assertThrows(IllegalStateException::class.java) { subject.onInitializeStorage(onBeforeWipe) }
        subject.onInitializeStorage(onBeforeWipe)

        verify(prefsController, times(2)).getInt(eq(AppDataCompatibility.PREF_KEY), any())
        verify(onBeforeWipe, never()).invoke()
        verify(activityManager, never()).clearApplicationUserData()
    }

    @Test
    fun `onInitializeStorage calls onBeforeWipe and then wipes on an incompatible app data version`() {
        whenever(prefsController.getInt(eq(AppDataCompatibility.PREF_KEY), any()))
            .thenReturn(CURRENT_APP_DATA_VERSION - 1)
        val subject = subject()

        subject.onInitializeStorage(onBeforeWipe)

        inOrder(onBeforeWipe, activityManager) {
            verify(onBeforeWipe).invoke()
            verify(activityManager).clearApplicationUserData()
        }
    }

    @Test
    fun `onInitializeStorage neither calls onBeforeWipe nor wipes on a compatible app data version`() {
        whenever(prefsController.getInt(eq(AppDataCompatibility.PREF_KEY), any()))
            .thenReturn(CURRENT_APP_DATA_VERSION)
        val subject = subject()

        subject.onInitializeStorage(onBeforeWipe)

        verify(onBeforeWipe, never()).invoke()
        verify(activityManager, never()).clearApplicationUserData()
    }
}
