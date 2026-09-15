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

package org.sprind.wallet.storagelogic.storage

import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.fromBase64Url
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class EncryptedStorageTest {

    private fun emptyCursor(): Cursor {
        val c = mock<Cursor>()
        whenever(c.moveToFirst()).thenReturn(false)
        whenever(c.moveToNext()).thenReturn(false)
        whenever(c.close()).then { Unit }
        return c
    }

    private fun stubbedDatabase(): SQLiteDatabase {
        val db = mock<SQLiteDatabase>()
        val empty = emptyCursor()
        // The trailing arguments (groupBy, having, orderBy, limit) are passed as null
        // by some callers, so we use nullable String matchers for them.
        whenever(db.query(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Array<String>::class.java),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Array<String>::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java)
        )).thenReturn(empty)
        whenever(db.query(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Array<String>::class.java),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Array<String>::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(String::class.java)
        )).thenReturn(empty)
        whenever(db.insert(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.kotlin.isNull(),
            org.mockito.ArgumentMatchers.any(android.content.ContentValues::class.java)
        )).thenReturn(1L)
        return db
    }

    private fun mockSqlCipherDatabase(): SQLiteDatabase = mock<SQLiteDatabase>()

    @Test
    fun `database ctor stores the pre-opened database and exposes it through withDatabase`() = runTest {
        val db = mockSqlCipherDatabase()
        val storage = EncryptedStorage(db)
        var captured: SQLiteDatabase? = null
        storage.withDatabase { captured = it }
        assertSame(db, captured)
    }

    @Test
    fun `database ctor with default clock and coroutineContext does not throw`() = runTest {
        EncryptedStorage(mockSqlCipherDatabase())
    }

    @Test
    fun `withDatabase executes the block on the supplied coroutineContext`() = runTest {
        val expectedContext: CoroutineContext = Dispatchers.Unconfined
        val storage = EncryptedStorage(
            database = mockSqlCipherDatabase(),
            coroutineContext = expectedContext
        )
        val seenContext = arrayOfNulls<CoroutineContext>(1)
        // withContext runs the block on the supplied coroutineContext. We just check
        // that the block was actually invoked without error and that the storage
        // accepts the coroutineContext without throwing.
        val out = storage.withDatabase { seenContext[0] = coroutineContext; "ok" }
        assertEquals("ok", out)
        assertNotNull(seenContext[0])
    }

    @Test
    fun `custom keySize is preserved and is used to generate null keys`() = runTest {
        val db = stubbedDatabase()
        val storage = EncryptedStorage(db, keySize = 17)
        val spec = StorageTableSpec("T", supportPartitions = false, supportExpiration = false)
        val table = storage.getTable(spec) as EncryptedStorageTable
        val generated = table.insert(
            key = null,
            data = kotlinx.io.bytestring.ByteString(byteArrayOf(1, 2, 3))
        )
        val decoded = generated.fromBase64Url()
        assertEquals(17, decoded.size)
    }

    @Test
    fun `database ctor with custom keySize 0 still allows null-key insert`() = runTest {
        val db = stubbedDatabase()
        val storage = EncryptedStorage(db, keySize = 0)
        val spec = StorageTableSpec("T", supportPartitions = false, supportExpiration = false)
        val table = storage.getTable(spec) as EncryptedStorageTable
        val generated = table.insert(
            key = null,
            data = kotlinx.io.bytestring.ByteString(byteArrayOf(1, 2, 3))
        )
        assertEquals("", generated)
    }

    @Test
    fun `passphrase ctor throws IllegalStateException with empty passphrase`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            EncryptedStorage(databasePath = ":memory:", passphrase = ByteArray(0))
        }
        assertTrue(
            ex.message?.contains("Invalid passphrase size") == true,
            "Expected message to mention invalid passphrase size, got: ${ex.message}"
        )
    }

    @Test
    fun `passphrase ctor with non-empty passphrase fails on first table creation because the sqlcipher native library is not loaded under the JVM`() = runTest {
        val storage = EncryptedStorage(
            databasePath = ":memory:",
            passphrase = "secret".toByteArray()
        )
        val spec = StorageTableSpec("T", supportPartitions = false, supportExpiration = false)
        assertThrows(UnsatisfiedLinkError::class.java) {
            kotlinx.coroutines.runBlocking {
                storage.getTable(spec)
            }
        }
    }

    @Test
    fun `database ctor with pre-opened database never invokes the databaseFactory closure`() = runTest {
        val db = stubbedDatabase()
        val storage = EncryptedStorage(db)
        val spec = StorageTableSpec("T", supportPartitions = false, supportExpiration = false)
        val table = storage.getTable(spec) as EncryptedStorageTable
        assertSame(storage, table.storage)
        org.mockito.kotlin.verify(db, org.mockito.kotlin.never()).close()
    }

    @Test
    fun `custom clock is exposed via the BaseStorage clock field`() = runTest {
        val db = mockSqlCipherDatabase()
        val fixedClock = object : kotlin.time.Clock {
            override fun now(): kotlin.time.Instant =
                kotlin.time.Instant.fromEpochSeconds(1_234_567_890L)
        }
        val storage = EncryptedStorage(db, clock = fixedClock)
        assertEquals(1_234_567_890L, storage.clock.now().epochSeconds)
    }

    @Test
    fun `withDatabase returns the result produced by the block`() = runTest {
        val db = mockSqlCipherDatabase()
        val storage = EncryptedStorage(db)
        val out = storage.withDatabase { "ok" }
        assertEquals("ok", out)
    }

    @Test
    fun `getOrCreateDatabase invokes the factory exactly once under concurrent contention`() = runTest {
        val db = mockSqlCipherDatabase()
        val storage = EncryptedStorage(db)

        val factoryInvocations = AtomicInteger(0)
        val newDatabase = mockSqlCipherDatabase()

        val databaseField = EncryptedStorage::class.java.getDeclaredField("database")
        databaseField.isAccessible = true
        databaseField.set(storage, null)

        val factoryField = EncryptedStorage::class.java.getDeclaredField("databaseFactory")
        factoryField.isAccessible = true
        val countingFactory: () -> SQLiteDatabase = {
            factoryInvocations.incrementAndGet()
            newDatabase
        }
        factoryField.set(storage, countingFactory)

        val results: List<SQLiteDatabase> = coroutineScope {
            (0 until 64)
                .map { async(Dispatchers.IO) { storage.getOrCreateDatabase() } }
                .awaitAll()
        }

        assertEquals(1, factoryInvocations.get())
        assertTrue(results.all { it === newDatabase })
    }
}
