package eu.europa.ec.presentationfeature.interactor

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.DisclosedDocumentDomain
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.corelogic.controller.ReissueDocumentPartialState

private const val REISSUE_FLOW_LOG_TAG = "reissueLowBatch"

// Keeps the association between a failing document and its reissuance error
private data class DocumentIdToFailure(
    val documentId: DocumentId,
    val failure: ReissueDocumentPartialState.Failure,
)

// Used to collect the attempted documents and failures during reissuance
private class ReissuanceProcessStates(
    val attempted: MutableSet<DocumentId> = mutableSetOf(),
    val failures: MutableList<DocumentIdToFailure> = mutableListOf()
)

private fun selectDocumentsToReissue(
    disclosedDocuments: List<DisclosedDocumentDomain>,
    walletCoreDocumentsController: WalletCoreDocumentsController,
): Flow<IssuedDocument> = flow {
    if (disclosedDocuments.isEmpty())
        return@flow

    val issuedDocumentsById = walletCoreDocumentsController
        .getAllIssuedDocuments().associateBy { it.id }

    val issuedDisclosedDocument = disclosedDocuments
        .mapNotNull { issuedDocumentsById[it.documentId] }

    // Emit all low batch, eligible for re-issuance, non-PID documents
    emitAll(
        issuedDisclosedDocument
            .asFlow()
            .filter {
                it.hasLowBatch() && it.isEligibleForReissue() && !it.toDocumentIdentifier().isPid
            }
    )

    // Stop here is no PID was disclosed
    if (issuedDisclosedDocument.none { it.toDocumentIdentifier().isPid }) {
        return@flow
    }

    val pids = issuedDocumentsById.values.filter { it.toDocumentIdentifier().isPid }
    // Stop if no PID has low batch
    if (pids.none { it.hasLowBatch() }) {
        return@flow
    }

    emitAll(pids.asFlow())
}

private fun WalletCoreDocumentsController.reissueZippingWithId(document: IssuedDocument) =
    this.reissueDocument(document).map { document.id to it }

/**
 * Evaluates the just-presented documents and reissues any whose one-time-use batch has fallen to
 * [eu.europa.ec.corelogic.model.DocumentIdentifier.minAvailableCredentials] or fewer.
 *
 * A PID is the exception: its two formats are refreshed as a unit, see below.
 *
 * Shared by both presentation interactors because the flow completes on different screens depending
 * on the credential's secure area: PID presentations (RWSCA/PIN, no device auth) finish on the
 * request screen, while presentations needing an Android Keystore unlock finish on the loading
 * screen. Both must be able to trigger the refresh while the rWSCA PIN session is still open.
 */
internal fun reissueLowBatchDocumentsFlow(
    walletCorePresentationController: WalletCorePresentationController,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    logController: LogController,
): Flow<PresentationLoadingReissuePartialState> =
    flow {
        val selectedDocumentsFlow = selectDocumentsToReissue(
            walletCorePresentationController.disclosedDocuments.orEmpty(),
            walletCoreDocumentsController
        )

        val states = selectedDocumentsFlow
            .flatMapConcat(walletCoreDocumentsController::reissueZippingWithId)
            .onEach { (documentId, partialState) ->
                when (partialState) {
                    is ReissueDocumentPartialState.InProgress -> {
                        logController.d(REISSUE_FLOW_LOG_TAG) { "Re-issuing ${documentId}" }
                    }

                    is ReissueDocumentPartialState.Success -> {
                        logController.d(REISSUE_FLOW_LOG_TAG) { "Re-issued ${documentId}" }
                    }

                    is ReissueDocumentPartialState.UserAuthRequired -> emit(
                        PresentationLoadingReissuePartialState.UserAuthenticationRequired(
                            crypto = partialState.crypto,
                            resultHandler = partialState.resultHandler
                        )
                    )

                    else -> Unit
                }
            }
            .fold(ReissuanceProcessStates()) { states, value ->
                val (documentId, state) = value
                // Every emission means the document was selected and attempted.
                // Success is expected unless something failed.
                states.attempted.add(documentId)
                if (state is ReissueDocumentPartialState.Failure) {
                    states.failures.add(
                        DocumentIdToFailure(
                            documentId = documentId,
                            failure = state
                        )
                    )
                }
                states
            }

        when {
            states.failures.isNotEmpty() -> {
                val error = states.failures.joinToString(separator = "; ") {
                    "${it.documentId}: ${it.failure.errorMessage}"
                }
                emit(PresentationLoadingReissuePartialState.Failure(error))
            }

            states.attempted.isNotEmpty() -> {
                emit(PresentationLoadingReissuePartialState.Success)
            }

            else -> {
                emit(PresentationLoadingReissuePartialState.NotNeeded)
            }
        }
    }

private fun IssuedDocument.isEligibleForReissue(): Boolean =
    toDocumentIdentifier().eligibleForReissue

private suspend fun IssuedDocument.hasLowBatch(): Boolean =
    credentialsCount() <= toDocumentIdentifier().minAvailableCredentials
