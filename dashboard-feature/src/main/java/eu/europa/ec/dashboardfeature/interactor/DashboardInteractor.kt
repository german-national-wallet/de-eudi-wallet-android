/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.dashboardfeature.interactor

import android.net.Uri
import eu.europa.ec.authenticationlogic.controller.storage.HardwareKeyStorageController
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.corelogic.controller.DeleteAllDocumentsPartialState
import eu.europa.ec.corelogic.controller.ResolvePreferredPidConfigurationsPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.corelogic.extension.toEaaCardData
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.isOther
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

sealed class DashboardInteractorGetIssuedDocumentsPartialState {
    data class Success(
        val pidDocument: IssuedDocument?,
        val eaaDocuments: List<EaaCardData>,
    ) : DashboardInteractorGetIssuedDocumentsPartialState()

    data class Failure(
        val error: String,
    ) : DashboardInteractorGetIssuedDocumentsPartialState()

    data object Restart : DashboardInteractorGetIssuedDocumentsPartialState()
}

interface DashboardInteractor {
    fun getAppVersion(): String
    fun retrieveLogFileUris(): ArrayList<Uri>
    fun getIssuedDocuments(): Flow<DashboardInteractorGetIssuedDocumentsPartialState>

    /**
     * Delegates to [WalletCoreDocumentsController.resolvePreferredPidConfigurations].
     * Resolves the preferred (beta-first) PID configuration IDs to issue, based
     * on what the PID issuer actually advertises.
     */
    suspend fun resolvePreferredPidConfigurations():
        ResolvePreferredPidConfigurationsPartialState
}

class DashboardInteractorImpl(
    private val configLogic: ConfigLogic,
    private val logController: LogController,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val resourceProvider: ResourceProvider,
    private val hardwareKeyStorageController: HardwareKeyStorageController,
) : DashboardInteractor {
    private val logTag = javaClass.simpleName
    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun getAppVersion(): String = configLogic.appVersion

    override fun retrieveLogFileUris(): ArrayList<Uri> {
        return ArrayList(logController.retrieveLogFileUris())
    }

    override fun getIssuedDocuments(): Flow<DashboardInteractorGetIssuedDocumentsPartialState> =
        flow {
            val allDocuments = walletCoreDocumentsController.getAllIssuedDocuments()

            if (allDocuments.isEmpty()) {
                emit(
                    DashboardInteractorGetIssuedDocumentsPartialState.Success(
                        pidDocument = null,
                        eaaDocuments = emptyList()
                    )
                )
            } else {
                val (pidDocuments, eaaDocuments) = allDocuments
                    .partition { it.toDocumentIdentifier().isPid }
                val eaaCardData = eaaDocuments
                    .sortedBy { it.issuedAt }
                    .map { it.toEaaCardData(Locale.getDefault()) }
                emit(
                    DashboardInteractorGetIssuedDocumentsPartialState.Success(
                        pidDocument = pidDocuments.firstOrNull(),
                        eaaDocuments = eaaCardData
                    )
                )
            }
        }.safeAsync {
            logController.e(logTag) { "Error retrieving issued documents: $it" }
            DashboardInteractorGetIssuedDocumentsPartialState.Failure(
                error = "Error retrieving documents"
            )
        }

    override suspend fun resolvePreferredPidConfigurations():
        ResolvePreferredPidConfigurationsPartialState =
        walletCoreDocumentsController.resolvePreferredPidConfigurations()
}
