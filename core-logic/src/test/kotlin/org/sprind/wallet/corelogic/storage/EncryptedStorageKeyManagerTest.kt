/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.corelogic.storage

import android.util.Base64
import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class EncryptedStorageKeyManagerTest {

    @Test
    fun `cached key is returned across calls without hitting prefs again`() {
        val prefsController = mock<PrefsController>()
        val existingKey = ByteArray(32) { 7 }
        val existingBase64 = Base64.encodeToString(existingKey, Base64.DEFAULT)
        whenever(prefsController.getString(ENCRYPTED_STORAGE_KEY, ""))
            .thenReturn(existingBase64)

        val manager = EncryptedStorageKeyManagerImpl(prefsController)

        val first = manager.getOrGenerateEncryptedStorageKey()
        val second = manager.getOrGenerateEncryptedStorageKey()
        val third = manager.getOrGenerateEncryptedStorageKey()

        assertArrayEquals(existingKey, first)
        assertArrayEquals(first, second)
        assertArrayEquals(first, third)

        // Once the cache is populated, getString/setString must not be hit again.
        verify(prefsController, times(1)).getString(ENCRYPTED_STORAGE_KEY, "")
        verify(prefsController, times(0)).setString(
            eq(ENCRYPTED_STORAGE_KEY),
            org.mockito.kotlin.any()
        )
    }

    @Test
    fun `parallel callers observe the same key and only one write happens`() {
        val prefsController = mock<PrefsController>()
        whenever(prefsController.getString(ENCRYPTED_STORAGE_KEY, ""))
            .thenReturn("")

        val manager = EncryptedStorageKeyManagerImpl(prefsController)
        val threadCount = 32
        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val go = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val mismatches = AtomicInteger(0)
        val writes = AtomicInteger(0)
        val firstResult = arrayOfNulls<ByteArray>(1)
        val firstResultLock = Any()

        whenever(prefsController.setStringSync(
            eq(ENCRYPTED_STORAGE_KEY),
            org.mockito.kotlin.any()
        )).then {
            writes.incrementAndGet()
            Unit
        }

        repeat(threadCount) {
            pool.execute {
                ready.countDown()
                go.await()
                val key = manager.getOrGenerateEncryptedStorageKey()
                synchronized(firstResultLock) {
                    if (firstResult[0] == null) {
                        firstResult[0] = key
                    } else if (!firstResult[0]!!.contentEquals(key)) {
                        mismatches.incrementAndGet()
                    }
                }
                done.countDown()
            }
        }

        ready.await(5, TimeUnit.SECONDS)
        go.countDown()
        done.await(10, TimeUnit.SECONDS)
        pool.shutdownNow()

        assertEquals("All threads must observe the same key", 0, mismatches.get())
        assertEquals(
            "Key must be persisted at most once under contention",
            1,
            writes.get()
        )
        assertEquals(32, firstResult[0]?.size ?: -1)
        verify(prefsController, atLeastOnce()).getString(ENCRYPTED_STORAGE_KEY, "")
    }
}
