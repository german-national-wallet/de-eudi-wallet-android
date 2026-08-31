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

package eu.europa.ec.presentationfeature.interactor

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.corelogic.controller.SendRequestedDocumentsPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePartialState
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.corelogic.model.isPid
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import eu.europa.ec.uilogic.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import org.sprind.wallet.corelogic.controller.ReissueDocumentPartialState

import kotlinx.coroutines.runBlocking
import java.net.URI

sealed class PresentationLoadingObserveResponsePartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : PresentationLoadingObserveResponsePartialState()

    data class Failure(val error: String) : PresentationLoadingObserveResponsePartialState()
    data object Success : PresentationLoadingObserveResponsePartialState()
    data class Redirect(val uri: URI) : PresentationLoadingObserveResponsePartialState()
    data object RequestReadyToBeSent : PresentationLoadingObserveResponsePartialState()
}

sealed class PresentationLoadingSendRequestedDocumentPartialState {
    data class Failure(val error: String) : PresentationLoadingSendRequestedDocumentPartialState()
    data object Success : PresentationLoadingSendRequestedDocumentPartialState()
}

sealed class PresentationLoadingReissuePartialState {
    data object NotNeeded : PresentationLoadingReissuePartialState()
    data object Success : PresentationLoadingReissuePartialState()
    data class Failure(val error: String) : PresentationLoadingReissuePartialState()
    data class UserAuthenticationRequired(
        val crypto: BiometricCrypto,
        val resultHandler: DeviceAuthenticationResult,
    ) : PresentationLoadingReissuePartialState()
}

interface PresentationLoadingInteractor {
    val verifierName: String?
    val initiatorRoute: String
    val issuanceState: Flow<eu.europa.ec.corelogic.controller.IssueDocumentsPartialState>
    fun stopPresentation()
    fun observeResponse(): Flow<PresentationLoadingObserveResponsePartialState>
    fun sendRequestedDocuments(): PresentationLoadingSendRequestedDocumentPartialState
    fun reissueLowBatchDocuments(): Flow<PresentationLoadingReissuePartialState>
    fun resumeOpenId4VciWithAuthorization(uri: String)
    fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult,
    )
}

class PresentationLoadingInteractorImpl(
    private val walletCorePresentationController: WalletCorePresentationController,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
    private val logController: LogController,
) : PresentationLoadingInteractor {

    override val verifierName: String? = walletCorePresentationController.verifierName

    override val initiatorRoute: String = walletCorePresentationController.initiatorRoute

    override val issuanceState: Flow<eu.europa.ec.corelogic.controller.IssueDocumentsPartialState> =
        walletCoreDocumentsController.issuanceState

    override fun observeResponse(): Flow<PresentationLoadingObserveResponsePartialState> =
        walletCorePresentationController.observeSentDocumentsRequest().mapNotNull { response ->
            when (response) {
                is WalletCorePartialState.Failure -> PresentationLoadingObserveResponsePartialState.Failure(
                    error = response.error
                )

                is WalletCorePartialState.Redirect -> PresentationLoadingObserveResponsePartialState.Redirect(
                    uri = response.uri
                )

                is WalletCorePartialState.Success -> {
                    PresentationLoadingObserveResponsePartialState.Success
                }

                is WalletCorePartialState.UserAuthenticationRequired -> {
                    PresentationLoadingObserveResponsePartialState.UserAuthenticationRequired(
                        response.authenticationData
                    )
                }

                is WalletCorePartialState.RequestIsReadyToBeSent -> PresentationLoadingObserveResponsePartialState.RequestReadyToBeSent
            }
        }

    override fun sendRequestedDocuments(): PresentationLoadingSendRequestedDocumentPartialState {
        return when (val result = walletCorePresentationController.sendRequestedDocuments()) {
            is SendRequestedDocumentsPartialState.RequestSent -> PresentationLoadingSendRequestedDocumentPartialState.Success
            is SendRequestedDocumentsPartialState.Failure -> PresentationLoadingSendRequestedDocumentPartialState.Failure(
                "SendRequestedDocuments failure[exception: ${result.exception}]"
            )
        }
    }

    override fun reissueLowBatchDocuments(): Flow<PresentationLoadingReissuePartialState> =
        reissueLowBatchDocumentsFlow(
            walletCorePresentationController = walletCorePresentationController,
            walletCoreDocumentsController = walletCoreDocumentsController,
            logController = logController,
        )

    override fun resumeOpenId4VciWithAuthorization(uri: String) {
        walletCoreDocumentsController.resumeOpenId4VciWithAuthorization(uri)
    }

    override fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult,
    ) {
        @Suppress("KotlinConstantConditions")
        if (!BuildConfig.ENABLE_EAA_BIOMETRICS) {
            runBlocking {
                resultHandler.onAuthenticationSuccess()
            }
            return
        }

        deviceAuthenticationInteractor.getBiometricsAvailability {
            when (it) {
                is BiometricsAvailability.CanAuthenticate -> {
                    deviceAuthenticationInteractor.authenticateWithBiometrics(
                        context = context,
                        crypto = crypto,
                        notifyOnAuthenticationFailure = notifyOnAuthenticationFailure,
                        resultHandler = resultHandler
                    )
                }

                is BiometricsAvailability.NonEnrolled -> {
                    deviceAuthenticationInteractor.launchBiometricSystemScreen()
                }

                is BiometricsAvailability.Failure -> {
                    resultHandler.onAuthenticationFailure()
                }
            }
        }
    }

    override fun stopPresentation() {
        walletCorePresentationController.stopPresentation()
    }
}

/**
 * Evaluates the just-presented documents and reissues any whose one-time-use batch has fallen to
 * [eu.europa.ec.corelogic.model.DocumentIdentifier.minAvailableCredentials] or fewer.
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
        val disclosedDocumentIds = walletCorePresentationController.disclosedDocuments
            .orEmpty()
            .map { it.documentId }
            .distinct()

        // early return in case we haven't presented anything
        if (disclosedDocumentIds.isEmpty()) {
            emit(PresentationLoadingReissuePartialState.NotNeeded)
            return@flow
        }

        val issuedDocumentsById = walletCoreDocumentsController.getAllIssuedDocuments()
            .associateBy { it.id }

        val disclosedDocuments = disclosedDocumentIds
            .mapNotNull { documentId -> issuedDocumentsById[documentId] }

        // A PID is stored as two separate documents (mdoc + SD-JWT VC) that form a single batch,
        // but a presentation only discloses one of the two formats. Since the batch must be
        // refreshed when EITHER format runs low, evaluate both PID formats whenever a PID was
        // presented; otherwise a depleted sibling-format batch would be missed.
        val documentsToEvaluate = if (disclosedDocuments.any { it.toDocumentIdentifier().isPid }) {
            (disclosedDocuments + issuedDocumentsById.values.filter { it.toDocumentIdentifier().isPid })
                .distinctBy { it.id }
        } else {
            disclosedDocuments
        }

        // This will cover the case of presenting multiple credentials and reissue them
        val depletedDocumentIds = documentsToEvaluate
            .filter { issuedDocument ->
                val documentIdentifier = issuedDocument.toDocumentIdentifier()
                documentIdentifier.eligibleForReissue && issuedDocument.credentialsCount() <= documentIdentifier.minAvailableCredentials
            }
            .map { it.id }
            .distinct()

        if (depletedDocumentIds.isEmpty()) {
            emit(PresentationLoadingReissuePartialState.NotNeeded)
            return@flow
        }

        depletedDocumentIds.forEach { documentId ->
            var failureMessage: String? = null
            walletCoreDocumentsController.reissueDocument(
                documentId = documentId,
            ).collect { reissueState ->
                when (reissueState) {
                    is ReissueDocumentPartialState.InProgress,
                    is ReissueDocumentPartialState.Success -> {
                        logController.d("Document re-issued: ") { documentId }
                    }

                    is ReissueDocumentPartialState.UserAuthRequired -> emit(
                        PresentationLoadingReissuePartialState.UserAuthenticationRequired(
                            crypto = reissueState.crypto,
                            resultHandler = reissueState.resultHandler
                        )
                    )

                    is ReissueDocumentPartialState.Failure -> {
                        failureMessage = reissueState.errorMessage
                    }
                }
            }

            if (failureMessage != null) {
                emit(PresentationLoadingReissuePartialState.Failure(failureMessage))
                return@flow
            }
        }

        emit(PresentationLoadingReissuePartialState.Success)
    }