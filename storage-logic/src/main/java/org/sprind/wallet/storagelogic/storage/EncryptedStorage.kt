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

import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.multipaz.storage.Storage
import org.multipaz.storage.base.BaseStorage
import org.multipaz.storage.base.BaseStorageTable
import org.multipaz.storage.StorageTableSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.coroutines.CoroutineContext

/**
 * [Storage] implementation backed by an encrypted SQLCipher for Android database.
 *
 * The [passphrase] used to derive the SQLCipher key is supplied by the caller and is
 * expected to be persisted outside of this class (e.g. wrapped by Android Keystore).
 */
class EncryptedStorage: BaseStorage {
    private val coroutineContext: CoroutineContext
    private val databaseFactory: () -> SQLiteDatabase
    internal val keySize: Int
    @Volatile
    private var database: SQLiteDatabase? = null

    constructor(
        database: SQLiteDatabase,
        clock: Clock = Clock.System,
        coroutineContext: CoroutineContext = Dispatchers.IO,
        keySize: Int = 9
    ): super(clock) {
        this.database = database
        databaseFactory = { throw IllegalStateException("unexpected call") }
        this.coroutineContext = coroutineContext
        this.keySize = keySize
    }

    constructor(
        databasePath: String?,
        passphrase: ByteArray,
        clock: Clock = Clock.System,
        coroutineContext: CoroutineContext = Dispatchers.IO,
        keySize: Int = 9
    ): super(clock) {
        check(passphrase.isNotEmpty()) { "Invalid passphrase size: ${passphrase.size}" }
        databaseFactory = {
            loadSqlCipherOnce()
            SQLiteDatabase.openOrCreateDatabase(
                databasePath ?: ":memory:",
                passphrase,
                null,
                null,
                null
            )
        }
        this.coroutineContext = coroutineContext
        this.keySize = keySize
    }

    override suspend fun createTable(tableSpec: StorageTableSpec): BaseStorageTable {
        getOrCreateDatabase()
        val table = EncryptedStorageTable(this, tableSpec)
        table.init()
        return table
    }

    internal suspend fun<T> withDatabase(
        block: suspend (database: SQLiteDatabase) -> T
    ): T {
        val db = getOrCreateDatabase()
        return withContext(coroutineContext) {
            block(db)
        }
    }

    internal fun getOrCreateDatabase(): SQLiteDatabase {
        database?.let { return it }
        synchronized(this) {
            return database ?: databaseFactory().also { database = it }
        }
    }

    companion object {
        @Volatile
        private var sqlCipherLoaded: Boolean = false

        private fun loadSqlCipherOnce() {
            if (sqlCipherLoaded) return
            synchronized(this) {
                if (sqlCipherLoaded) return
                System.loadLibrary("sqlcipher")
                sqlCipherLoaded = true
            }
        }
    }
}