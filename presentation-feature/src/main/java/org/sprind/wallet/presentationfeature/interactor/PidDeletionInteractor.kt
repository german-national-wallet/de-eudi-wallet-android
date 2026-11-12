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

package org.sprind.wallet.presentationfeature.interactor

import eu.europa.ec.authenticationlogic.controller.storage.WalletPinUnBlockTimeStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Factory
import org.sprind.wallet.commonfeature.interactor.DocumentDeletionPartialState
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.commonfeature.interactor.deletePidDocumentsWithRwscaCleanup

/**
 * Deletes the PID when the user gives up on its Digital ID code during a presentation: from the
 * temporary-block sheet ("Delete Digital ID now") or the locked Digital ID screen (WD-4980).
 *
 * Not presentation-scoped, so the blocking error screen can use it after the presentation scope
 * is closed.
 */
interface PidDeletionInteractor {

    /**
     * Deletes the PID (RWSCA account and both PID formats, see
     * [deletePidDocumentsWithRwscaCleanup]) and, on success, the stored wallet PIN unblock time.
     * An unexpected exception is emitted as [DocumentDeletionPartialState.Failure].
     */
    fun deletePid(): Flow<DocumentDeletionPartialState>
}

@Factory(binds = [PidDeletionInteractor::class])
class PidDeletionInteractorImpl(
    private val rwscaInteractor: RwscaInteractor,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val walletPinUnBlockTimeStorageController: WalletPinUnBlockTimeStorageController,
    private val resourceProvider: ResourceProvider,
    private val logController: LogController,
) : PidDeletionInteractor {

    private val logTag = this::class.java.simpleName

    override fun deletePid(): Flow<DocumentDeletionPartialState> = flow {
        val result = deletePidDocumentsWithRwscaCleanup(
            rwscaInteractor = rwscaInteractor,
            walletCoreDocumentsController = walletCoreDocumentsController,
        )
        if (result is DocumentDeletionPartialState.Success) {
            walletPinUnBlockTimeStorageController.clear()
        }
        emit(result)
    }.safeAsync {
        logController.d(logTag) { "deletePid: ${it.localizedMessage}" }
        DocumentDeletionPartialState.Failure(
            error = it.localizedMessage ?: resourceProvider.genericErrorMessage()
        )
    }
}
