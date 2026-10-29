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

package org.sprind.wallet.corelogic.activity

import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTableSpec

/** The wallet's activity log, kept in the encrypted wallet database. */
interface ActivityLogRepository {

    /** All entries, newest first. */
    val entries: Flow<List<ActivityLogEntry>>

    /** Stores [entry] without making the caller wait; a presentation scope may end right after. */
    fun record(entry: ActivityLogEntry)

    /** As [record], but also runs [build] off the caller's thread, for entries that read documents. */
    fun recordFrom(build: () -> ActivityLogEntry)

    suspend fun delete(id: String)

    suspend fun clear()
}

class ActivityLogRepositoryImpl(
    private val storage: Storage,
    private val logController: LogController,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ActivityLogRepository {

    private val mutex = Mutex()
    private val cache = MutableStateFlow<List<ActivityLogEntry>?>(null)

    override val entries: Flow<List<ActivityLogEntry>> = cache.filterNotNull()

    init {
        scope.launch { mutex.withLock { cache.value = load() } }
    }

    override fun record(entry: ActivityLogEntry) = recordFrom { entry }

    override fun recordFrom(build: () -> ActivityLogEntry) {
        scope.launch {
            val entry = runCatching(build).getOrElse {
                logController.e(LOG_TAG) { "Could not build activity: ${it.message}" }
                return@launch
            }
            mutex.withLock {
                runCatching {
                    storage.getTable(STORAGE_TABLE_SPEC).insert(
                        key = entry.id,
                        data = ByteString(json.encodeToString(ActivityLogEntry.serializer(), entry).encodeToByteArray()),
                    )
                }.onSuccess {
                    cache.value = (cache.value.orEmpty() + entry).sortedByDescending { it.timestampMillis }
                }.onFailure {
                    logController.e(LOG_TAG) { "Could not record activity: ${it.message}" }
                }
            }
        }
    }

    override suspend fun delete(id: String) {
        mutex.withLock {
            storage.getTable(STORAGE_TABLE_SPEC).delete(key = id)
            cache.value = cache.value.orEmpty().filterNot { it.id == id }
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            storage.getTable(STORAGE_TABLE_SPEC).deleteAll()
            cache.value = emptyList()
        }
    }

    private suspend fun load(): List<ActivityLogEntry> = runCatching {
        storage.getTable(STORAGE_TABLE_SPEC)
            .enumerateWithData()
            .mapNotNull { (_, data) ->
                runCatching {
                    json.decodeFromString(ActivityLogEntry.serializer(), data.toByteArray().decodeToString())
                }.getOrNull()
            }
            .sortedByDescending { it.timestampMillis }
    }.getOrElse {
        logController.e(LOG_TAG) { "Could not load activity log: ${it.message}" }
        emptyList()
    }

    companion object {
        private const val LOG_TAG = "ActivityLogRepository"

        val STORAGE_TABLE_SPEC = StorageTableSpec(
            name = "activity_log",
            supportPartitions = false,
            supportExpiration = false,
        )

        private val json = Json { ignoreUnknownKeys = true }
    }
}
