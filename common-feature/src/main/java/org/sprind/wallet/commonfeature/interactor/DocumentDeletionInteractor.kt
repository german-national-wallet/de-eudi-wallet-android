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

package org.sprind.wallet.commonfeature.interactor

import eu.europa.ec.corelogic.controller.DeleteDocumentPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.DocumentId
import kotlinx.coroutines.flow.first
import org.sprind.wallet.networklogic.common.model.ApiResult

/**
 * Outcome of a document-deletion operation.
 */
sealed class DocumentDeletionPartialState {
    /** The document(s), and for a PID, the remote RWSCA account, were deleted. */
    data object Success : DocumentDeletionPartialState()

    /**
     * Deletion failed. [error] is the RWSCA error code (remote failure) or the local
     * delete error message.
     */
    data class Failure(val error: String) : DocumentDeletionPartialState()
}

/**
 * Deletes the document identified by [documentId], applying RWSCA account cleanup when it is a PID.
 *
 * The document is resolved first: when no document matches [documentId], returns
 * [DocumentDeletionPartialState.Failure] with [documentNotFoundError] instead of attempting a delete
 * on a missing id. A PID is deleted through [deletePidDocumentsWithRwscaCleanup] (remote account +
 * both PID formats); any other document is simply deleted from local storage.
 *
 * @param documentId the document to delete.
 * @param rwscaInteractor used for RWSCA account cleanup when the document is a PID.
 * @param walletCoreDocumentsController used to resolve and delete documents.
 * @param documentNotFoundError error to return when no document matches [documentId].
 * @return [DocumentDeletionPartialState.Success] once the document is gone, or
 *   [DocumentDeletionPartialState.Failure] on the first unrecoverable failure.
 */
suspend fun deleteDocumentWithRwscaCleanup(
    documentId: DocumentId,
    rwscaInteractor: RwscaInteractor,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    documentNotFoundError: String,
): DocumentDeletionPartialState {
    val document = walletCoreDocumentsController.getDocumentById(documentId)
        ?: return DocumentDeletionPartialState.Failure(error = documentNotFoundError)

    if (document.toDocumentIdentifier().isPid) {
        return deletePidDocumentsWithRwscaCleanup(
            rwscaInteractor = rwscaInteractor,
            walletCoreDocumentsController = walletCoreDocumentsController,
        )
    }

    return when (val response = walletCoreDocumentsController.deleteDocument(documentId).first()) {
        is DeleteDocumentPartialState.Failure ->
            DocumentDeletionPartialState.Failure(error = response.errorMessage)

        is DeleteDocumentPartialState.Success ->
            DocumentDeletionPartialState.Success
    }
}

/**
 * Deletes the PID from both the remote RWSCA account and local storage.
 *
 * The remote RWSCA account is deleted (and local RWSCA data cleared) *first*, then every local PID
 * document (mdoc + SD-JWT VC) is deleted; this ordering follows the architecture spec. A remote
 * failure is only fatal when it is *not* [isAccountNotFoundLocally]: an already-absent account means
 * a previous attempt removed it but failed before deleting the local documents, so it is treated as
 * an idempotent no-op and the local PID documents are still deleted, letting a retry finish
 * cleanly. Note this leaves a short window where local PID documents exist without a backing RWSCA
 * account; that state is recoverable via exactly this retry path.
 *
 * On success this also resets PID OpenID4VCI in-memory issuance state. Callers should use this
 * helper, instead of deleting PID documents directly, whenever PID deletion is terminal (for
 * example account-locked deletion or document-details deletion). The reset lets immediate
 * re-issuance recreate DPoP/issuer state in the same way an app restart would.
 *
 * @param rwscaInteractor deletes the remote RWSCA account.
 * @param walletCoreDocumentsController enumerates and deletes the local PID documents.
 * @return [DocumentDeletionPartialState.Success] once the account and all PID documents are gone, or
 *   [DocumentDeletionPartialState.Failure] on the first unrecoverable failure.
 */
suspend fun deletePidDocumentsWithRwscaCleanup(
    rwscaInteractor: RwscaInteractor,
    walletCoreDocumentsController: WalletCoreDocumentsController,
): DocumentDeletionPartialState {
    when (val response = rwscaInteractor.deleteAccount()) {
        is ApiResult.Failure -> {
            if (!response.error.isAccountNotFoundLocally()) {
                return DocumentDeletionPartialState.Failure(error = response.error.code)
            }
        }

        is ApiResult.Success -> Unit
    }

    val pidDocuments = walletCoreDocumentsController.getAllDocumentsByType(
        documentIdentifiers = listOf(
            DocumentIdentifier.MdocPid,
            DocumentIdentifier.SdJwtPid
        )
    )

    pidDocuments.forEach { document ->
        when (val response = walletCoreDocumentsController.deleteDocument(document.id).first()) {
            is DeleteDocumentPartialState.Failure -> {
                return DocumentDeletionPartialState.Failure(error = response.errorMessage)
            }

            is DeleteDocumentPartialState.Success -> Unit
        }
    }

    walletCoreDocumentsController.resetPidIssuanceState()

    return DocumentDeletionPartialState.Success
}
