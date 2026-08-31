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

import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.util.formatInstantToDateString
import eu.europa.ec.commonfeature.ui.document_details.model.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.EaaCardData
import eu.europa.ec.corelogic.extension.localizedIssuerMetadata
import eu.europa.ec.corelogic.extension.toEaaCardData
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.sprind.wallet.commonfeature.interactor.DocumentDeletionPartialState
import java.time.LocalDate
import java.time.ZoneId
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.commonfeature.interactor.deleteDocumentWithRwscaCleanup
import java.time.Instant
import java.util.Locale


sealed class DashboardDocumentDetailInteractorGetDocumentDetail {
    data class Success(
        val documentType: String,
        val createdOn: String,
        val validTill: String,
        val physicalDocumentName: String,
        val topBarBackgroundColor: String? = null,
        val topBarBackgroundImageUri: String? = null,
        val topBarTextColor: String? = null,
        val eaaCardData: EaaCardData? = null,
    ) : DashboardDocumentDetailInteractorGetDocumentDetail()

    data class Failure(
        val error: String,
    ) : DashboardDocumentDetailInteractorGetDocumentDetail()
}

sealed class DashboardDocumentDetailDeleteDocumentPartialState {
    data object Success : DashboardDocumentDetailDeleteDocumentPartialState()
    data class Failure(val error: String) : DashboardDocumentDetailDeleteDocumentPartialState()
}

interface DashboardDocumentDetailInteractor {
    fun getDocumentDetail(docId: String): Flow<DashboardDocumentDetailInteractorGetDocumentDetail>
    fun deleteDocument(documentId: String): Flow<DashboardDocumentDetailDeleteDocumentPartialState>
}

internal class DashboardDocumentDetailInteractorImpl(
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val resourceProvider: ResourceProvider,
    private val logController: LogController,
    private val rwscaInteractor: RwscaInteractor,
) : DashboardDocumentDetailInteractor {
    private val tag = this.javaClass.simpleName

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun getDocumentDetail(docId: String): Flow<DashboardDocumentDetailInteractorGetDocumentDetail> =
        flow {
            val document = walletCoreDocumentsController.getDocumentById(docId) as? IssuedDocument

            if (document == null) {
                emit(DashboardDocumentDetailInteractorGetDocumentDetail.Failure("Document not found"))
                return@flow
            }

            when (document.format) {
                is SdJwtVcFormat -> prepareSdJwtDocumentDetail(document).collect(::emit)
                is MsoMdocFormat -> prepareMdocDocumentDetail(document).collect(::emit)
                // TODO handle other formats
            }
        }.safeAsync {
            logController.d(tag) {
                "prepareMdocDocumentDetail: ${it.localizedMessage}"
            }
            DashboardDocumentDetailInteractorGetDocumentDetail.Failure(
                error = genericErrorMsg
            )
        }

    private fun extractTopBarStyling(document: IssuedDocument): Triple<String?, String?, String?> {
        val display = document.localizedIssuerMetadata(Locale.getDefault())
        return Triple(
            display?.backgroundColor,
            display?.backgroundImageUri?.toString(),
            display?.textColor
        )
    }

    private fun prepareSdJwtDocumentDetail(document: IssuedDocument): Flow<DashboardDocumentDetailInteractorGetDocumentDetail> =
        flow {
            // For PID: use hardcoded title, for EAA: use document name
            val documentType = if (document.toDocumentIdentifier().isPid) {
                resourceProvider.getString(R.string.pid_inspection_pid_details_title)
            } else {
                // Use document name for EAA (e.g., "University Diploma", "FitLife Membership")
                document.name
            }

            val physicalDocumentName = documentType

            val createdOn =
                document.issuedAt.formatInstantToDateString(locale = resourceProvider.getLocale())
            val validTillValue =
                extractValueFromDocumentOrEmpty(document, DocumentJsonKeys.SdJwt.EXPIRY)
            val validTill = validTillValue.toLongOrNull()
                ?.let(Instant::ofEpochSecond)
                ?.formatInstantToDateString(locale = resourceProvider.getLocale())
                ?: "-"
            val (topBarBackgroundColor, topBarBackgroundImageUri, topBarTextColor) =
                extractTopBarStyling(document)
            val eaaCardData = if (document.toDocumentIdentifier().isPid) {
                null
            } else {
                document.toEaaCardData(Locale.getDefault())
            }
            emit(
                DashboardDocumentDetailInteractorGetDocumentDetail.Success(
                    documentType = documentType,
                    createdOn = createdOn,
                    validTill = validTill,
                    physicalDocumentName = physicalDocumentName,
                    topBarBackgroundColor = topBarBackgroundColor,
                    topBarBackgroundImageUri = topBarBackgroundImageUri,
                    topBarTextColor = topBarTextColor,
                    eaaCardData = eaaCardData
                )
            )
        }.safeAsync {
            logController.d(tag) {
                "prepareSdJwtDocumentDetail: ${it.localizedMessage}"
            }
            DashboardDocumentDetailInteractorGetDocumentDetail.Failure(
                error = genericErrorMsg
            )
        }

    private fun prepareMdocDocumentDetail(document: IssuedDocument): Flow<DashboardDocumentDetailInteractorGetDocumentDetail> =
        flow {
            // Detect if this is a PID or EAA document
            val documentIdentifier = document.toDocumentIdentifier()

            // For PID: use hardcoded title, for EAA: use document name
            val documentType = if (documentIdentifier.isPid) {
                resourceProvider.getString(R.string.pid_inspection_pid_details_title)
            } else {
                // Use document name for EAA (e.g., "University Diploma", "FitLife Membership")
                document.name
            }

            val physicalDocumentName = documentType

            val createdOn =
                document.issuedAt.formatInstantToDateString(locale = resourceProvider.getLocale())
            val validTillValue =
                extractValueFromDocumentOrEmpty(document, DocumentJsonKeys.EXPIRY_DATE)
            val validTill = validTillValue
                .takeIf { it.isNotBlank() }
                ?.let {
                    runCatching { Instant.parse(it) }
                        .recoverCatching { _ ->
                            LocalDate.parse(it)
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                        }
                        .getOrNull()
                        ?.formatInstantToDateString(locale = resourceProvider.getLocale())
                }
                ?: "-"

            val (topBarBackgroundColor, topBarBackgroundImageUri, topBarTextColor) =
                extractTopBarStyling(document)
            val eaaCardData = if (documentIdentifier.isPid) {
                null
            } else {
                document.toEaaCardData(Locale.getDefault())
            }
            emit(
                DashboardDocumentDetailInteractorGetDocumentDetail.Success(
                    documentType = documentType,
                    physicalDocumentName = physicalDocumentName,
                    createdOn = createdOn,
                    validTill = validTill,
                    topBarBackgroundColor = topBarBackgroundColor,
                    topBarBackgroundImageUri = topBarBackgroundImageUri,
                    topBarTextColor = topBarTextColor,
                    eaaCardData = eaaCardData
                )
            )
        }.safeAsync {
            logController.d(tag) {
                "prepareSdJwtDocumentDetail: ${it.localizedMessage}"
            }
            DashboardDocumentDetailInteractorGetDocumentDetail.Failure(
                error = genericErrorMsg
            )
        }

    override fun deleteDocument(
        documentId: String,
    ): Flow<DashboardDocumentDetailDeleteDocumentPartialState> =
        flow {
            when (
                val response = deleteDocumentWithRwscaCleanup(
                    documentId = documentId,
                    rwscaInteractor = rwscaInteractor,
                    walletCoreDocumentsController = walletCoreDocumentsController,
                    documentNotFoundError = genericErrorMsg,
                )
            ) {
                is DocumentDeletionPartialState.Failure ->
                    emit(
                        DashboardDocumentDetailDeleteDocumentPartialState.Failure(
                            error = response.error
                        )
                    )

                is DocumentDeletionPartialState.Success ->
                    emit(DashboardDocumentDetailDeleteDocumentPartialState.Success)
            }
        }.safeAsync {
            logController.d(tag) {
                "deleteDocument: ${it.localizedMessage}"
            }
            DashboardDocumentDetailDeleteDocumentPartialState.Failure(
                error = genericErrorMsg
            )
        }
}
