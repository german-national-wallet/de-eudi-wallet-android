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

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.multipaz.storage.KeyExistsStorageException
import org.multipaz.storage.NoRecordStorageException
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.fromBase64Url
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class EncryptedStorageTableTest {

    private class FakeClock(
        var nowSeconds: Long = NOW_SECONDS
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(nowSeconds)
    }

    private lateinit var database: SQLiteDatabase
    private lateinit var storage: EncryptedStorage
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        database = mock<SQLiteDatabase>()
        clock = FakeClock()
        storage = EncryptedStorage(database, clock)
        // Pre-create the default empty cursor so we never call a mock stubbing from
        // inside another stubbing's argument list (Mockito's UnfinishedStubbingException).
        val empty = stubEmptyCursor()
        // 7-arg query(): tableName, columns, selection, whereArgs, groupBy, having, orderBy
        // The trailing arguments are passed as null by some callers (e.g. get), so we
        // use nullable String matchers for them.
        whenever(database.query(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Array<String>::class.java),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Array<String>::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java)
        )).thenReturn(empty)
        // 8-arg query(): ..., limit
        whenever(database.query(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Array<String>::class.java),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Array<String>::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(String::class.java)
        )).thenReturn(empty)
        whenever(database.rawQuery(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(Array<String>::class.java)
        )).thenReturn(empty)
        // Default insert returns a positive row id so that the implicit schema-table
        // bookkeeping in BaseStorage.getTable does not throw.
        whenever(database.insert(
            org.mockito.ArgumentMatchers.anyString(),
            isNull(),
            org.mockito.ArgumentMatchers.any(ContentValues::class.java)
        )).thenReturn(1L)
        // Default update returns 1 so that table.update does not throw NoRecordStorageException.
        whenever(database.update(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(ContentValues::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(Array<String>::class.java)
        )).thenReturn(1)
        // Default delete returns 0 so tests can verify a true/false path.
        whenever(database.delete(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(Array<String>::class.java)
        )).thenReturn(0)
    }

    private fun bytes(vararg ints: Int): ByteString {
        val arr = ByteArray(ints.size) { ints[it].toByte() }
        return ByteString(arr)
    }

    private suspend fun newTable(
        spec: StorageTableSpec = testSpec(),
    ): EncryptedStorageTable =
        storage.getTable(spec) as EncryptedStorageTable

    private fun testSpec(
        name: String = "T",
        supportPartitions: Boolean = false,
        supportExpiration: Boolean = false,
    ): StorageTableSpec = StorageTableSpec(
        name = name,
        supportPartitions = supportPartitions,
        supportExpiration = supportExpiration
    )

    private fun assertBytesEquals(expected: ByteString, actual: ByteString?) {
        requireNotNull(actual)
        assertArrayEquals(expected.toByteArray(), actual.toByteArray())
    }

    private fun stubEmptyCursor(vararg columnNames: String): Cursor {
        val cursor = mock<Cursor>()
        whenever(cursor.moveToFirst()).doReturn(false)
        whenever(cursor.moveToNext()).doReturn(false)
        whenever(cursor.close()).then { Unit }
        if (columnNames.isNotEmpty()) {
            whenever(cursor.getColumnIndex(columnNames[0])).doReturn(0)
        }
        return cursor
    }

    private fun stubSingleRowCursor(blob: ByteArray?): Cursor {
        val cursor = mock<Cursor>()
        whenever(cursor.moveToFirst()).doReturn(true)
        whenever(cursor.moveToNext()).doReturn(false)
        if (blob != null) {
            whenever(cursor.getBlob(0)).doReturn(blob)
        } else {
            whenever(cursor.getBlob(0)).doReturn(null)
        }
        whenever(cursor.close()).then { Unit }
        return cursor
    }

    private fun stubRowsCursor(rows: List<Pair<String, ByteArray?>>): Cursor {
        val cursor = mock<Cursor>()
        var position = -1
        whenever(cursor.moveToFirst()).thenAnswer {
            position = 0
            rows.isNotEmpty()
        }
        whenever(cursor.moveToNext()).thenAnswer {
            if (position < 0) {
                position = 0
                rows.isNotEmpty()
            } else if (position < rows.size) {
                position += 1
                position < rows.size
            } else {
                false
            }
        }
        whenever(cursor.close()).then { Unit }
        doAnswer { invocation ->
            val col = invocation.arguments[0] as Int
            val row = rows.getOrNull(position) ?: rows.last()
            if (col == 0) row.first else null
        }.whenever(cursor).getString(org.mockito.ArgumentMatchers.anyInt())
        doAnswer { invocation ->
            val row = rows.getOrNull(position) ?: rows.last()
            row.second
        }.whenever(cursor).getBlob(org.mockito.ArgumentMatchers.anyInt())
        return cursor
    }

    // region init

    @Test
    fun `init executes the create table statement for the spec`() = runTest {
        newTable(testSpec(name = "T"))
        verify(database, times(1)).execSQL(org.mockito.kotlin.argThat { sql ->
            sql.contains("CREATE TABLE") && sql.contains("MzT")
        })
    }

    @Test
    fun `supportPartitions true produces a create table statement that includes partitionId`() = runTest {
        newTable(testSpec(name = "PT", supportPartitions = true))
        verify(database).execSQL(org.mockito.kotlin.argThat { sql ->
            sql.contains("partitionId") && sql.contains("PRIMARY KEY(partitionId, id)")
        })
    }

    @Test
    fun `supportExpiration true produces a create table statement that includes expiration`() = runTest {
        newTable(testSpec(name = "ET", supportExpiration = true))
        verify(database).execSQL(org.mockito.kotlin.argThat { sql ->
            sql.contains("expiration")
        })
    }

    @Test
    fun `supportPartitions false and supportExpiration false omits partition and expiration columns from the create table statement`() = runTest {
        newTable(testSpec(name = "BARE"))
        verify(database, org.mockito.kotlin.atLeastOnce()).execSQL(
            org.mockito.kotlin.argThat { sql ->
                sql.contains("MzBARE") && !sql.contains("partitionId") && !sql.contains("expiration")
            }
        )
    }

    // endregion

    // region get

    @Test
    fun `get returns null for a non-existent key`() = runTest {
        val table = newTable()
        assertNull(table.get("missing"))
    }

    @Test
    fun `get returns the stored ByteString for an existing key`() = runTest {
        val cursor = stubSingleRowCursor(byteArrayOf(1, 2, 3))
        whenever(database.query(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(cursor)
        val table = newTable()
        assertBytesEquals(bytes(1, 2, 3), table.get("k1"))
    }

    @Test
    fun `get closes the cursor after the read`() = runTest {
        val cursor = stubEmptyCursor()
        whenever(database.query(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(cursor)
        val table = newTable()
        table.get("missing")
        verify(cursor).close()
    }

    @Test
    fun `get returns null for an expired record`() = runTest {
        val table = newTable(testSpec(supportExpiration = true))
        clock.nowSeconds = NOW_SECONDS + 10_000
        assertNull(table.get("k1"))
    }

    @Test
    fun `get throws when partitioning is supported and partitionId is null`() = runTest {
        val table = newTable(testSpec(supportPartitions = true))
        assertFailsWith<IllegalArgumentException> { table.get("k") }
    }

    @Test
    fun `get throws when partitioning is not supported and partitionId is non-null`() = runTest {
        val table = newTable()
        assertFailsWith<IllegalArgumentException> { table.get("k", "p") }
    }

    @Test
    fun `get builds a where clause that includes the expiration condition when supported`() = runTest {
        val capturedSelection = arrayOfNulls<String>(1)
        whenever(database.query(
            any<String>(), any<Array<String>>(), any<String>(), any<Array<String>>(),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java)
        )).thenAnswer { invocation ->
            capturedSelection[0] = invocation.arguments[2] as String
            stubEmptyCursor()
        }
        val table = newTable(testSpec(supportExpiration = true))
        table.get("k1")
        assertTrue(
            "Expected get() WHERE to contain expiration check, got: ${capturedSelection[0]}",
            capturedSelection[0]!!.contains("expiration >=")
        )
    }

    @Test
    fun `get builds a where clause that includes the partition condition when supported`() = runTest {
        val capturedSelection = arrayOfNulls<String>(1)
        whenever(database.query(
            any<String>(), any<Array<String>>(), any<String>(), any<Array<String>>(),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java)
        )).thenAnswer { invocation ->
            capturedSelection[0] = invocation.arguments[2] as String
            stubEmptyCursor()
        }
        val table = newTable(testSpec(supportPartitions = true))
        table.get("k1", "p1")
        assertTrue(
            "Expected get() WHERE to contain partition condition, got: ${capturedSelection[0]}",
            capturedSelection[0]!!.contains("partitionId = ?")
        )
    }

    // endregion

    // region insert

    @Test
    fun `insert with explicit key returns the key and stores the data`() = runTest {
        whenever(database.insert(any<String>(), isNull(), any())).doReturn(1L)
        val table = newTable()
        val returned = table.insert("k1", bytes(1, 2, 3))
        assertEquals("k1", returned)
    }

    @Test
    fun `insert with null key generates a base64url key whose decoded length equals keySize`() = runTest {
        whenever(database.insert(any<String>(), isNull(), any())).doReturn(1L)
        val table = newTable()
        val returned = table.insert(null, bytes(1, 2, 3))
        val decoded = returned.fromBase64Url()
        assertEquals(9, decoded.size)
    }

    @Test
    fun `insert with explicit key throws KeyExistsStorageException when database insert returns a negative row id`() = runTest {
        // Override the default insert stub so that inserts with the "k1" key return
        // -1L (causing the SUT to throw), but the implicit schema-table insert still
        // returns 1L so the test setup completes.
        whenever(database.insert(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.argThat { values -> values.getAsString("id") == "k1" }
        )).thenReturn(-1L)
        val table = newTable()
        val ex = assertFailsWith<KeyExistsStorageException> {
            table.insert("k1", bytes(2))
        }
        assertTrue(ex.message?.contains("k1") == true)
    }

    @Test
    fun `insert with null key throws KeyCollisionStorageException when MAX_INSERTION_RETRIES collisions occur`() = runTest {
        // Allow the implicit schema-table insert (which uses a non-null key) to
        // succeed; fail every other insert with -1L. With key = null on the SUT
        // table, the SUT generates a fresh random key each attempt and retries;
        // after the retry budget is exhausted it must throw
        // KeyCollisionStorageException (not KeyExistsStorageException, which only
        // fires when a caller-supplied key collides).
        whenever(database.insert(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.kotlin.isNull(),
            org.mockito.ArgumentMatchers.any(ContentValues::class.java)
        )).thenAnswer { invocation ->
            val table = invocation.arguments[0] as String
            if (table.contains("_SCHEMA")) 1L else -1L
        }
        val table = newTable()
        val ex = assertFailsWith<KeyCollisionStorageException> {
            table.insert(null, bytes(1))
        }
        assertTrue(
            "Expected message to mention the retry limit, got: ${ex.message}",
            ex.message?.contains("retries") == true
        )
    }

    @Test
    fun `insert with supportExpiration true purges an expired record at the same key before inserting`() = runTest {
        val table = newTable(testSpec(supportExpiration = true))
        table.insert(
            "k1",
            bytes(1),
            partitionId = null,
            expiration = Instant.fromEpochSeconds(NOW_SECONDS + 10)
        )
        clock.nowSeconds = NOW_SECONDS + 20
        table.insert(
            "k1",
            bytes(2),
            partitionId = null,
            expiration = Instant.fromEpochSeconds(NOW_SECONDS + 100)
        )
        // Verify that the pre-purge delete was issued with the MzT table name and
        // a "expiration <" WHERE clause.
        verify(database, org.mockito.kotlin.atLeastOnce()).delete(
            org.mockito.kotlin.argThat { table -> table.contains("MzT") },
            org.mockito.kotlin.argThat { sel -> sel.contains("expiration <") },
            any()
        )
    }

    @Test
    fun `insert with supportExpiration false and a finite expiration throws`() = runTest {
        val table = newTable()
        assertFailsWith<IllegalArgumentException> {
            table.insert(
                "k1",
                bytes(1),
                partitionId = null,
                expiration = Instant.fromEpochSeconds(NOW_SECONDS + 1)
            )
        }
    }

    @Test
    fun `insert with empty key throws IllegalArgumentException`() = runTest {
        val table = newTable()
        assertFailsWith<IllegalArgumentException> { table.insert("", bytes(1)) }
    }

    @Test
    fun `insert with key longer than MAX_KEY_SIZE throws IllegalArgumentException`() = runTest {
        val table = newTable()
        val tooLong = "x".repeat(1025)
        assertFailsWith<IllegalArgumentException> { table.insert(tooLong, bytes(1)) }
    }

    @Test
    fun `insert throws when partitioning supported and partitionId is null`() = runTest {
        val table = newTable(testSpec(supportPartitions = true))
        assertFailsWith<IllegalArgumentException> {
            table.insert("k1", bytes(1), partitionId = null)
        }
    }

    @Test
    fun `insert throws when partitioning not supported and partitionId is non-null`() = runTest {
        val table = newTable()
        assertFailsWith<IllegalArgumentException> {
            table.insert("k1", bytes(1), partitionId = "p")
        }
    }

    // endregion

    // region update

    @Test
    fun `update replaces data and a subsequent get returns the new value`() = runTest {
        whenever(database.update(any(), any(), any(), any())).doReturn(1)
        val cursor = stubSingleRowCursor(byteArrayOf(2))
        whenever(database.query(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(cursor)
        val table = newTable()
        table.update("k1", bytes(2))
        assertBytesEquals(bytes(2), table.get("k1"))
    }

    @Test
    fun `update with new expiration writes the new expiration to the values`() = runTest {
        val table = newTable(testSpec(supportExpiration = true))
        table.update(
            "k1",
            bytes(2),
            partitionId = null,
            expiration = Instant.fromEpochSeconds(NOW_SECONDS + 1000)
        )
        verify(database).update(
            org.mockito.kotlin.argThat { sql -> sql.contains("MzT") },
            org.mockito.kotlin.argThat { values -> values.containsKey("expiration") && values.containsKey("data") },
            any<String>(),
            any<Array<String>>()
        )
    }

    @Test
    fun `update without expiration does not include the expiration column in the values`() = runTest {
        val table = newTable(testSpec(supportExpiration = true))
        table.update("k1", bytes(2), expiration = null)
        verify(database).update(
            any<String>(),
            org.mockito.kotlin.argThat { values -> !values.containsKey("expiration") && values.containsKey("data") },
            any<String>(),
            any<Array<String>>()
        )
    }

    @Test
    fun `update throws NoRecordStorageException when the update affects zero rows`() = runTest {
        whenever(database.update(any(), any(), any(), any())).doReturn(0)
        val table = newTable()
        val ex = assertFailsWith<NoRecordStorageException> {
            table.update("missing", bytes(1))
        }
        assertTrue(ex.message?.contains("missing") == true)
    }

    @Test
    fun `update throws NoRecordStorageException for an expired key`() = runTest {
        whenever(database.update(any(), any(), any(), any())).doReturn(0)
        val table = newTable(testSpec(supportExpiration = true))
        clock.nowSeconds = NOW_SECONDS + 1_000_000
        assertFailsWith<NoRecordStorageException> {
            table.update("k1", bytes(2))
        }
    }

    @Test
    fun `update with finite expiration and supportExpiration false throws`() = runTest {
        val table = newTable()
        assertFailsWith<IllegalArgumentException> {
            table.update(
                "k1",
                bytes(2),
                expiration = Instant.fromEpochSeconds(NOW_SECONDS + 1)
            )
        }
    }

    // endregion

    // region delete

    @Test
    fun `delete returns true when the database delete affects one row`() = runTest {
        whenever(database.delete(any(), any(), any())).doReturn(1)
        val table = newTable()
        assertTrue(table.delete("k1"))
    }

    @Test
    fun `delete returns false for a non-existent key`() = runTest {
        whenever(database.delete(any(), any(), any())).doReturn(0)
        val table = newTable()
        assertFalse(table.delete("missing"))
    }

    @Test
    fun `delete returns false for an expired record`() = runTest {
        whenever(database.delete(any(), any(), any())).doReturn(0)
        val table = newTable(testSpec(supportExpiration = true))
        clock.nowSeconds = NOW_SECONDS + 1_000_000
        assertFalse(table.delete("k1"))
    }

    @Test
    fun `delete with partitioning passes the partitionId in the where args`() = runTest {
        val capturedArgs = arrayOfNulls<Array<String>>(1)
        whenever(database.delete(any(), any(), any())).doAnswer { invocation ->
            capturedArgs[0] = invocation.arguments[2] as Array<String>
            1
        }
        val table = newTable(testSpec(supportPartitions = true))
        table.delete("k1", "p1")
        assertArrayEquals(arrayOf("k1", "p1"), capturedArgs[0])
    }

    // endregion

    // region deleteAll

    @Test
    fun `deleteAll executes the deleteAllStatement on the database`() = runTest {
        val table = newTable()
        table.deleteAll()
        verify(database).execSQL(org.mockito.kotlin.argThat { sql ->
            sql.contains("DELETE") && sql.contains("MzT") && !sql.contains("WHERE")
        })
    }

    @Test
    fun `deleteAll on a fresh table does not throw even though the table is empty`() = runTest {
        val table = newTable()
        table.deleteAll()
    }

    // endregion

    // region deletePartition

    @Test
    fun `deletePartition removes all rows for that partition only`() = runTest {
        val table = newTable(testSpec(supportPartitions = true))
        table.deletePartition("p1")
        verify(database).delete(
            org.mockito.kotlin.argThat { sql -> sql.contains("MzT") },
            eq("partitionId = ?"),
            eq(arrayOf("p1"))
        )
    }

    @Test
    fun `deletePartition throws when partitioning is not supported`() = runTest {
        val table = newTable()
        assertFailsWith<IllegalArgumentException> { table.deletePartition("p") }
    }

    // endregion

    // region enumerate

    @Test
    fun `enumerate returns keys in the order yielded by the cursor`() = runTest {
        val rows = stubRowsCursor(
            listOf("a" to null, "b" to null, "c" to null)
        )
        val empty = stubEmptyCursor()
        // The schema-table's enumerate (called first, during getTable) should see
        // no pre-existing tables. The test's own table.enumerate() should see
        // the rows cursor.
        whenever(database.query(
            any<String>(), any<Array<String>>(), any<String>(), any<Array<String>>(),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java)
        )).thenReturn(empty)
        whenever(database.query(
            any<String>(), any<Array<String>>(), any<String>(), any<Array<String>>(),
            any<String>(), any<String>(), any<String>(), any<String>()
        )).thenReturn(empty, rows)
        val table = newTable()
        assertEquals(listOf("a", "b", "c"), table.enumerate())
    }

    @Test
    fun `enumerate with limit 0 returns an empty list and does not query the database`() = runTest {
        val table = newTable()
        assertEquals(emptyList<String>(), table.enumerate(limit = 0))
        verify(database, never()).query(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `enumerate with limit N passes a SQL limit clause`() = runTest {
        val table = newTable()
        table.enumerate(limit = 5)
        verify(database).query(
            any<String>(),
            any<Array<String>>(),
            any<String>(),
            any<Array<String>>(),
            any<String>(),
            any<String>(),
            org.mockito.kotlin.argThat { orderBy -> orderBy == "id" },
            eq("0, 5")
        )
    }

    @Test
    fun `enumerate excludes expired records via the cursor returning no rows`() = runTest {
        val table = newTable(testSpec(supportExpiration = true))
        clock.nowSeconds = NOW_SECONDS + 1_000_000
        assertEquals(emptyList<String>(), table.enumerate())
    }

    @Test
    fun `enumerate throws when limit is negative`() = runTest {
        val table = newTable()
        assertFailsWith<IllegalArgumentException> { table.enumerate(limit = -1) }
    }

    @Test
    fun `enumerate throws when partitioning required and partitionId is null`() = runTest {
        val table = newTable(testSpec(supportPartitions = true))
        assertFailsWith<IllegalArgumentException> { table.enumerate(partitionId = null) }
    }

    @Test
    fun `enumerate with partitioning only includes the partitionId in the where args`() = runTest {
        val capturedArgs = arrayOfNulls<Array<String>>(1)
        whenever(database.query(
            any<String>(), any<Array<String>>(), any<String>(), any<Array<String>>(),
            any<String>(), any<String>(), any<String>(), any<String>()
        )).thenAnswer { invocation ->
            capturedArgs[0] = invocation.arguments[3] as Array<String>
            stubEmptyCursor()
        }
        val table = newTable(testSpec(supportPartitions = true))
        table.enumerate(partitionId = "p1")
        assertArrayEquals(arrayOf("", "p1"), capturedArgs[0])
    }

    // endregion

    // region enumerateWithData

    @Test
    fun `enumerateWithData returns key data pairs from the cursor`() = runTest {
        val rows = stubRowsCursor(
            listOf("a" to byteArrayOf(1), "b" to byteArrayOf(2, 2))
        )
        val empty = stubEmptyCursor()
        whenever(database.query(
            any<String>(), any<Array<String>>(), any<String>(), any<Array<String>>(),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java)
        )).thenReturn(empty)
        // First 8-arg call (from the schema-table's enumerate) sees the empty cursor;
        // the test's own enumerateWithData sees the rows cursor.
        whenever(database.query(
            any<String>(), any<Array<String>>(), any<String>(), any<Array<String>>(),
            any<String>(), any<String>(), any<String>(), any<String>()
        )).thenReturn(empty, rows)
        val table = newTable()
        val result = table.enumerateWithData()
        assertEquals(2, result.size)
        assertEquals("a", result[0].first)
        assertArrayEquals(byteArrayOf(1), result[0].second.toByteArray())
        assertEquals("b", result[1].first)
        assertArrayEquals(byteArrayOf(2, 2), result[1].second.toByteArray())
    }

    @Test
    fun `enumerateWithData with limit 0 returns an empty list and does not query the database`() = runTest {
        val table = newTable()
        assertEquals(emptyList<Pair<String, ByteString>>(), table.enumerateWithData(limit = 0))
        verify(database, never()).query(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `enumerateWithData with limit N passes a SQL limit clause`() = runTest {
        val table = newTable()
        table.enumerateWithData(limit = 5)
        verify(database).query(
            any<String>(),
            any<Array<String>>(),
            any<String>(),
            any<Array<String>>(),
            any<String>(),
            any<String>(),
            org.mockito.kotlin.argThat { orderBy -> orderBy == "id" },
            eq("0, 5")
        )
    }

    @Test
    fun `enumerateWithData excludes expired records via the cursor returning no rows`() = runTest {
        val table = newTable(testSpec(supportExpiration = true))
        clock.nowSeconds = NOW_SECONDS + 1_000_000
        assertEquals(emptyList<Pair<String, ByteString>>(), table.enumerateWithData())
    }

    @Test
    fun `enumerateWithData throws when partitioning required and partitionId is null`() = runTest {
        val table = newTable(testSpec(supportPartitions = true))
        assertFailsWith<IllegalArgumentException> { table.enumerateWithData(partitionId = null) }
    }

    // endregion

    // region purgeExpired

    @Test
    fun `purgeExpired executes a delete statement with the current epoch seconds`() = runTest {
        clock.nowSeconds = NOW_SECONDS + 1234
        val table = newTable(testSpec(supportExpiration = true))
        table.purgeExpired()
        val captor = argumentCaptor<Array<Any>>()
        verify(database).execSQL(
            org.mockito.kotlin.argThat { sql ->
                sql.contains("DELETE") && sql.contains("MzT") &&
                    sql.contains("expiration < ?")
            },
            captor.capture()
        )
        assertEquals(1, captor.firstValue.size)
        assertEquals(NOW_SECONDS + 1234L, captor.firstValue[0])
    }

    @Test
    fun `purgeExpired on a supportExpiration false table still issues a delete statement and does not throw`() = runTest {
        val table = newTable()
        table.purgeExpired()
        verify(database).execSQL(
            org.mockito.kotlin.argThat { sql ->
                sql.contains("DELETE") && sql.contains("MzT") &&
                    sql.contains("expiration < ?")
            },
            any<Array<Any>>()
        )
    }

    // endregion

    // region whereArgs and SDK cursor window branch coverage

    @Test
    fun `whereArgs contains only the key when partitions are not supported`() = runTest {
        val capturedArgs = arrayOfNulls<Array<String>>(1)
        // 7-arg query(): trailing groupBy/having/orderBy args may be null, so use nullable.
        whenever(database.query(
            any<String>(), any<Array<String>>(), any<String>(), any<Array<String>>(),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java),
            org.mockito.ArgumentMatchers.nullable(String::class.java)
        )).thenAnswer { invocation ->
            capturedArgs[0] = invocation.arguments[3] as Array<String>
            stubEmptyCursor()
        }
        val table = newTable()
        table.get("k1")
        assertArrayEquals(arrayOf("k1"), capturedArgs[0])
    }

    @Test
    fun `enumerate afterKey null becomes the empty string in the where args`() = runTest {
        val capturedArgs = arrayOfNulls<Array<String>>(1)
        whenever(database.query(
            any<String>(), any<Array<String>>(), any<String>(), any<Array<String>>(),
            any<String>(), any<String>(), any<String>(), any<String>()
        )).thenAnswer { invocation ->
            capturedArgs[0] = invocation.arguments[3] as Array<String>
            stubEmptyCursor()
        }
        val table = newTable()
        table.enumerate()
        assertArrayEquals(arrayOf(""), capturedArgs[0])
    }

    @Test
    fun `enumerate with no limit passes a null SQL limit clause`() = runTest {
        val table = newTable()
        table.enumerate(limit = Int.MAX_VALUE)
        verify(database, org.mockito.kotlin.atLeastOnce()).query(
            any<String>(),
            any<Array<String>>(),
            any<String>(),
            any<Array<String>>(),
            any<String>(),
            any<String>(),
            org.mockito.kotlin.argThat { orderBy -> orderBy == "id" },
            org.mockito.kotlin.isNull()
        )
    }

    // endregion

    companion object {
        private const val NOW_SECONDS = 1_700_000_000L
    }
}
