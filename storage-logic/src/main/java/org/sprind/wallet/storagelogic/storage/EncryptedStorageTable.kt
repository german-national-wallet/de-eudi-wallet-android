/*
 * Copyright 2023 The Open Wallet Foundation
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

package org.sprind.wallet.storagelogic.storage

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import android.os.Build
import org.multipaz.storage.KeyExistsStorageException
import org.multipaz.storage.NoRecordStorageException
import org.multipaz.storage.base.BaseStorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.storage.base.SqlStatementMaker
import org.multipaz.util.toBase64Url
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import kotlin.random.Random

private const val MAX_INSERTION_RETRIES = 100

internal class EncryptedStorageTable(
    override val storage: EncryptedStorage,
    spec: StorageTableSpec
): BaseStorageTable(spec) {
    private val sql = SqlStatementMaker(
        spec,
        textType = "TEXT",
        blobType = "BLOB",
        longType = "INTEGER",
        useReturningClause = false,
        collationCharset = null
    )

    suspend fun init() {
        storage.withDatabase { database ->
            database.execSQL(sql.createTableStatement)
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    override suspend fun get(key: String, partitionId: String?): ByteString? {
        checkPartition(partitionId)
        return storage.withDatabase { database ->
            val cursor = database.query(
                sql.tableName,
                arrayOf("data"),
                sql.conditionWithExpiration(storage.clock.now().epochSeconds),
                whereArgs(key, partitionId),
                null,
                null,
                null
            )
            cursor.use {
                it.enlargeWindow()
                if (!it.moveToFirst()) {
                    return@use null
                }
                ByteString(it.getBlob(0))
            }
        }
    }

    override suspend fun insert(
        key: String?,
        data: ByteString,
        partitionId: String?,
        expiration: Instant
    ): String {
        key?.let { checkKey(it) }
        checkPartition(partitionId)
        checkExpiration(expiration)
        return storage.withDatabase { database ->
            if (key != null && spec.supportExpiration) {
                // if there is an entry with this key, but it is expired, it needs to be purged.
                // Purging expired keys does not interfere with operation atomicity
                database.delete(
                    sql.tableName,
                    sql.purgeExpiredWithIdCondition(storage.clock.now().epochSeconds),
                    whereArgs(key, partitionId)
                )
            }
            val values = ContentValues().apply {
                if (spec.supportPartitions) {
                    put("partitionId", partitionId)
                }
                if (spec.supportExpiration) {
                    put("expiration", expiration.epochSeconds)
                }
                put("data", data.toByteArray())
            }
            repeat(MAX_INSERTION_RETRIES) {
                val newKey = key ?: Random.nextBytes(storage.keySize).toBase64Url()
                values.put("id", newKey)
                val rowId = database.insert(sql.tableName, null, values)
                when {
                    rowId >= 0 -> return@withDatabase newKey
                    key != null -> throw KeyExistsStorageException(
                        "Record with ${recordDescription(key, partitionId)} already exists")
                }
            }
            throw KeyCollisionStorageException(
                "Exceeded $MAX_INSERTION_RETRIES retries inserting into ${sql.tableName} " +
                    "(keySize=${storage.keySize})"
            )
        }
    }

    override suspend fun update(
        key: String,
        data: ByteString,
        partitionId: String?,
        expiration: Instant?
    ) {
        checkPartition(partitionId)
        if (expiration != null) {
            checkExpiration(expiration)
        }
        storage.withDatabase { database ->
            val nowSeconds = storage.clock.now().epochSeconds
            val values = ContentValues().apply {
                if (expiration != null) {
                    put("expiration", expiration.epochSeconds)
                }
                put("data", data.toByteArray())
            }
            val count = database.update(
                sql.tableName,
                values,
                sql.conditionWithExpiration(nowSeconds),
                whereArgs(key, partitionId)
            )
            if (count != 1) {
                throw NoRecordStorageException(
                    "No record with ${recordDescription(key, partitionId)}")
            }
        }
    }

    override suspend fun delete(key: String, partitionId: String?): Boolean {
        checkPartition(partitionId)
        return storage.withDatabase { database ->
            val nowSeconds = storage.clock.now().epochSeconds
            val count = database.delete(
                sql.tableName,
                sql.conditionWithExpiration(nowSeconds),
                whereArgs(key, partitionId)
            )
            count > 0
        }
    }

    override suspend fun deleteAll() {
        storage.withDatabase { database ->
            database.execSQL(sql.deleteAllStatement)
        }
    }

    override suspend fun deletePartition(partitionId: String) {
        checkPartition(partitionId)
        storage.withDatabase { database ->
            database.delete(
                sql.tableName,
                "partitionId = ?",
                arrayOf(partitionId)
            )
        }
    }

    override suspend fun enumerate(
        partitionId: String?,
        afterKey: String?,
        limit: Int
    ): List<String> {
        checkPartition(partitionId)
        checkLimit(limit)
        if (limit == 0) {
            return listOf()
        }
        return storage.withDatabase { database ->
            val cursor = database.query(
                sql.tableName,
                arrayOf("id"),
                sql.enumerateConditionWithExpiration(storage.clock.now().epochSeconds),
                whereArgs(afterKey ?: "", partitionId),
                null,
                null,
                "id",
                if (limit < Int.MAX_VALUE) "0, $limit" else null
            )
            cursor.use {
                it.map { cursor -> cursor.getString(0) }
            }
        }
    }

    override suspend fun enumerateWithData(
        partitionId: String?,
        afterKey: String?,
        limit: Int
    ): List<Pair<String, ByteString>> {
        checkPartition(partitionId)
        checkLimit(limit)
        if (limit == 0) {
            return listOf()
        }
        return storage.withDatabase { database ->
            val cursor = database.query(
                sql.tableName,
                arrayOf("id", "data"),
                sql.enumerateConditionWithExpiration(storage.clock.now().epochSeconds),
                whereArgs(afterKey ?: "", partitionId),
                null,
                null,
                "id",
                if (limit < Int.MAX_VALUE) "0, $limit" else null
            )
            cursor.use {
                it.map { cursor -> Pair(cursor.getString(0), ByteString(cursor.getBlob(1))) }
            }
        }
    }

    override suspend fun purgeExpired() {
        val nowSeconds = storage.clock.now().epochSeconds
        storage.withDatabase { database ->
            database.execSQL(
                sql.purgeExpiredStatement,
                arrayOf<Any>(nowSeconds)
            )
        }
    }

    private fun whereArgs(key: String, partitionId: String?): Array<String> {
        return if (spec.supportPartitions) {
            arrayOf(key, partitionId!!)
        } else {
            arrayOf(key)
        }
    }

    companion object {
        const val CURSOR_WINDOW_SIZE = 5 * 1024 * 1024L

        @SuppressLint("ObsoleteSdkInt")
        fun Cursor.enlargeWindow() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // The default window size of 2MB which is not the limit we want to be
                // constrained by.
                (this as? AbstractWindowedCursor)?.window = CursorWindow(
                    "Larger Window", CURSOR_WINDOW_SIZE)
            }
        }

        fun <T> Cursor.map(f: (Cursor) -> T): List<T> {
            val list = mutableListOf<T>()
            while (this.moveToNext()) {
                list.add(f(this))
            }
            return list.toList()
        }
    }
}

/**
 * Thrown by [EncryptedStorageTable.insert] when an auto-generated key collides with an
 * existing row on every attempt up to [MAX_INSERTION_RETRIES]. Indicates the keyspace is
 * exhausted for this table or the random source is broken.
 */
class KeyCollisionStorageException(
    message: String
) : RuntimeException(message)
