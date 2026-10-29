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

package org.sprind.wallet.dashboardfeature.interactor

import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.sprind.wallet.corelogic.activity.ActivityLogEntry
import org.sprind.wallet.corelogic.activity.ActivityLogRepository
import org.sprind.wallet.dashboardfeature.ui.activities.ActivityUiMapper
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityDetailUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivitySectionUi

interface ActivityInteractor {

    /** The activity log, or only the activities of [documentId]'s credential when given. */
    fun sections(documentId: String?): Flow<List<ActivitySectionUi>>

    /** Emits null once the activity is deleted. */
    fun detail(activityId: String): Flow<ActivityDetailUi?>

    suspend fun delete(activityId: String)

    suspend fun deleteAll()
}

class ActivityInteractorImpl(
    private val activityLogRepository: ActivityLogRepository,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val mapper: ActivityUiMapper,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ActivityInteractor {

    override fun sections(documentId: String?): Flow<List<ActivitySectionUi>> {
        val belongsToCredential = documentId?.let(::credentialFilter)
        return activityLogRepository.entries.map { entries ->
            mapper.toSections(belongsToCredential?.let(entries::filter) ?: entries)
        }.flowOn(dispatcher)
    }

    override fun detail(activityId: String): Flow<ActivityDetailUi?> =
        activityLogRepository.entries.map { entries ->
            entries.firstOrNull { it.id == activityId }?.let { entry ->
                mapper.toDetail(entry)
            }
        }.flowOn(dispatcher)

    override suspend fun delete(activityId: String) = activityLogRepository.delete(activityId)

    override suspend fun deleteAll() = activityLogRepository.clear()

    /** The PID is held in two formats, and its activities belong to both. */
    private fun credentialFilter(documentId: String): (ActivityLogEntry) -> Boolean {
        val isPid = walletCoreDocumentsController.getDocumentById(documentId)
            ?.toDocumentIdentifier()?.isPid == true
        return { entry ->
            entry.credentials.any { credential ->
                credential.documentId == documentId ||
                        (isPid && credential.credentialIdentifier.toDocumentIdentifier().isPid)
            }
        }
    }
}
