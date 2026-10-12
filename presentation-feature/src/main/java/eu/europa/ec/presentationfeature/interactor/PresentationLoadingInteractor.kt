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
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.BuildConfig
import org.sprind.wallet.corelogic.presentationerror.PresentationError
import org.sprind.wallet.corelogic.presentationerror.mapToPresentationError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

import kotlinx.coroutines.runBlocking
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class PresentationLoadingObserveResponsePartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : PresentationLoadingObserveResponsePartialState()

    data class Failure(val error: PresentationError) : PresentationLoadingObserveResponsePartialState()
    data object Success : PresentationLoadingObserveResponsePartialState()
    data class Redirect(val uri: URI) : PresentationLoadingObserveResponsePartialState()
    data object RequestReadyToBeSent : PresentationLoadingObserveResponsePartialState()
}

sealed class PresentationLoadingSendRequestedDocumentPartialState {
    data class Failure(val error: PresentationError) : PresentationLoadingSendRequestedDocumentPartialState()
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
    suspend fun sendRequestedDocuments(): PresentationLoadingSendRequestedDocumentPartialState
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
    private val resourceProvider: ResourceProvider,
    private val logController: LogController,
) : PresentationLoadingInteractor {

    private val genericErrorMessage: String
        get() = resourceProvider.genericErrorMessage()

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

    override suspend fun sendRequestedDocuments(): PresentationLoadingSendRequestedDocumentPartialState {
        return when (val result = walletCorePresentationController.sendRequestedDocuments()) {
            is SendRequestedDocumentsPartialState.RequestSent -> PresentationLoadingSendRequestedDocumentPartialState.Success
            is SendRequestedDocumentsPartialState.Failure ->
                PresentationLoadingSendRequestedDocumentPartialState.Failure(
                    error = mapToPresentationError(
                        throwable = result.throwable,
                        traceId = result.traceId,
                        genericErrorMessage = genericErrorMessage,
                    ),
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
 * Upper bound on a post-presentation batch refresh. Both call sites await it with the user on a
 * progress indicator, and neither wallet-core's reissue scope nor its HTTP client has a timeout of
 * its own, so a stalled socket would hold the screen indefinitely.
 *
 * Abandoning a refresh is safe: the rotated tokens are persisted as soon as the token refresh
 * succeeds, so the next attempt starts from valid credentials.
 */
internal val REISSUE_TIMEOUT: Duration = 30.seconds

