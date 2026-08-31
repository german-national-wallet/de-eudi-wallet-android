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

package eu.europa.ec.issuancefeature.interactor.document

import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.DocumentFormat
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.ui.document_details.domain.DocumentDetailsDomain
import eu.europa.ec.commonfeature.ui.document_details.transformer.DocumentDetailsTransformer
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.localizedIssuerMetadata
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.sprind.wallet.commonfeature.interactor.DocumentDeletionPartialState
import org.sprind.wallet.commonfeature.interactor.RwscaInteractor
import org.sprind.wallet.commonfeature.interactor.deleteDocumentWithRwscaCleanup
import java.net.URI

sealed class DocumentDetailsInteractorPartialState {
    data class Success(
        val issuerName: String?,
        val issuerLogo: URI?,
        val documentDetailsDomain: DocumentDetailsDomain,
        val documentIsBookmarked: Boolean,
        val documentFormat: DocumentFormat,
        val topBarBackgroundColor: String? = null,
        val topBarBackgroundImageUri: String? = null,
        val topBarTextColor: String? = null,
    ) : DocumentDetailsInteractorPartialState()

    data class Failure(val error: String) : DocumentDetailsInteractorPartialState()
}

sealed class DocumentDetailsInteractorDeleteDocumentPartialState {
    data object SingleDocumentDeleted : DocumentDetailsInteractorDeleteDocumentPartialState()
    data class Failure(
        val errorMessage: String
    ) : DocumentDetailsInteractorDeleteDocumentPartialState()
}

sealed class DocumentDetailsInteractorStoreBookmarkPartialState {
    data class Success(
        val bookmarkId: String
    ) : DocumentDetailsInteractorStoreBookmarkPartialState()

    data object Failure : DocumentDetailsInteractorStoreBookmarkPartialState()
}

sealed class DocumentDetailsInteractorDeleteBookmarkPartialState {
    data object Success : DocumentDetailsInteractorDeleteBookmarkPartialState()
    data object Failure : DocumentDetailsInteractorDeleteBookmarkPartialState()
}

interface DocumentDetailsInteractor {
    fun getDocumentDetails(
        documentId: DocumentId,
    ): Flow<DocumentDetailsInteractorPartialState>

    fun deleteDocument(
        documentId: DocumentId
    ): Flow<DocumentDetailsInteractorDeleteDocumentPartialState>

    @Deprecated(" do not use we are removing realm")
    fun storeBookmark(
        bookmarkId: String
    ): Flow<DocumentDetailsInteractorStoreBookmarkPartialState>
    @Deprecated(" do not use we are removing realm")
    fun deleteBookmark(
        bookmarkId: String
    ): Flow<DocumentDetailsInteractorDeleteBookmarkPartialState>
}

class DocumentDetailsInteractorImpl(
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val resourceProvider: ResourceProvider,
    private val rwscaInteractor: RwscaInteractor,
) : DocumentDetailsInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun getDocumentDetails(
        documentId: DocumentId,
    ): Flow<DocumentDetailsInteractorPartialState> =
        flow {
            val issuedDocument =
                walletCoreDocumentsController.getDocumentById(documentId = documentId)
                        as? IssuedDocument

            issuedDocument?.let { safeIssuedDocument ->
                val documentDetailsDomainResult =
                    DocumentDetailsTransformer.transformToDocumentDetailsDomain(
                        document = safeIssuedDocument,
                        resourceProvider = resourceProvider
                    )

                val documentDetailsDomain = documentDetailsDomainResult.getOrThrow()

                val issuerName =
                    safeIssuedDocument.localizedIssuerMetadata(resourceProvider.getLocale())?.name

                val issuerLogo =
                    safeIssuedDocument.localizedIssuerMetadata(resourceProvider.getLocale())?.logo

                val issuerDisplay =
                    safeIssuedDocument.localizedIssuerMetadata(resourceProvider.getLocale())


                emit(
                    DocumentDetailsInteractorPartialState.Success(
                        issuerName = issuerName,
                        documentDetailsDomain = documentDetailsDomain,
                        documentIsBookmarked = false,
                        issuerLogo = issuerLogo?.uri,
                        documentFormat = safeIssuedDocument.format,
                        topBarBackgroundColor = issuerDisplay?.backgroundColor,
                        topBarBackgroundImageUri = issuerDisplay?.backgroundImageUri?.toString(),
                        topBarTextColor = issuerDisplay?.textColor
                    )
                )
            } ?: emit(DocumentDetailsInteractorPartialState.Failure(error = genericErrorMsg))
        }.safeAsync {
            DocumentDetailsInteractorPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun deleteDocument(
        documentId: DocumentId
    ): Flow<DocumentDetailsInteractorDeleteDocumentPartialState> =
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
                        DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                            errorMessage = response.error
                        )
                    )

                is DocumentDeletionPartialState.Success ->
                    emit(DocumentDetailsInteractorDeleteDocumentPartialState.SingleDocumentDeleted)
            }
        }.safeAsync {
            DocumentDetailsInteractorDeleteDocumentPartialState.Failure(
                errorMessage = it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun storeBookmark(bookmarkId: DocumentId): Flow<DocumentDetailsInteractorStoreBookmarkPartialState> =
        flow {
            //bookmarkStorageController.store(Bookmark(identifier = bookmarkId))
            emit(DocumentDetailsInteractorStoreBookmarkPartialState.Failure)
        }.safeAsync {
            DocumentDetailsInteractorStoreBookmarkPartialState.Failure
        }

    override fun deleteBookmark(bookmarkId: DocumentId): Flow<DocumentDetailsInteractorDeleteBookmarkPartialState> =
        flow {
            //bookmarkStorageController.delete(bookmarkId)
            emit(DocumentDetailsInteractorDeleteBookmarkPartialState.Failure)
        }.safeAsync {
            DocumentDetailsInteractorDeleteBookmarkPartialState.Failure
        }
}
