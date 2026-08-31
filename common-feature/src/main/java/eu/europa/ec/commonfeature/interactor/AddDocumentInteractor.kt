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

package eu.europa.ec.commonfeature.interactor

import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import eu.europa.ec.commonfeature.model.DocumentOptionItemUi
import eu.europa.ec.corelogic.controller.FetchScopedDocumentsPartialState
import eu.europa.ec.corelogic.controller.IssuanceMethod
import eu.europa.ec.corelogic.controller.IssueDocumentPartialState
import eu.europa.ec.corelogic.controller.IssueDocumentsPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.handler.AusweisSdkAuthorizationHandler
import eu.europa.ec.eudi.openid4vci.CredentialConfigurationIdentifier
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.ListItemMainContentData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed class AddDocumentInteractorPartialState {
    data class Success(val options: List<DocumentOptionItemUi>) :
        AddDocumentInteractorPartialState()

    data class Failure(val error: String) : AddDocumentInteractorPartialState()
}

interface AddDocumentInteractor {
    fun getAddDocumentOption(flowType: IssuanceFlowUiConfig): Flow<AddDocumentInteractorPartialState>

    /** Start issuance with attestation Specification */
    fun startIssueDocumentAttested(
        issuanceMethod: IssuanceMethod,
        configIds: Set<CredentialConfigurationIdentifier>,
        issuerId: String,
        walletInstanceAttestationSpec: WalletInstanceAttestationSpec,
    )

    @Deprecated("Use the List<String> version for batch issuance")
    fun startIssueDocumentAttested(
        issuanceMethod: IssuanceMethod,
        configId: CredentialConfigurationIdentifier,
        issuerId: String,
        walletInstanceAttestationSpec: WalletInstanceAttestationSpec,
    )

    /** Hot: latest state (late subscribers can read it) */
    val issuanceState: StateFlow<IssueDocumentPartialState>

    /** Optional: one-shot terminal events for navigation */
    val issuanceEvents: SharedFlow<IssuanceEvent>

    val authorizationHandler : AusweisSdkAuthorizationHandler

    fun cancelIssuance()

    fun resumeOpenId4VciWithAuthorization(uri: String)
}

sealed interface IssuanceEvent {
    data object Completed : IssuanceEvent
    data class Failed(val error: String) : IssuanceEvent
}

class AddDocumentInteractorImpl(
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val ausweisSdkAuthorizationHandler: AusweisSdkAuthorizationHandler,
    private val resourceProvider: ResourceProvider,
    private val interactorScope: CoroutineScope,
) : AddDocumentInteractor {

    private val genericErrorMsg get() = resourceProvider.genericErrorMessage()

    private val _issuanceState =
        MutableStateFlow<IssueDocumentPartialState>(
            IssueDocumentPartialState.InProgress
        )
    override val issuanceState = _issuanceState.asStateFlow()

    override val authorizationHandler: AusweisSdkAuthorizationHandler
        get() = ausweisSdkAuthorizationHandler

    private val _issuanceEvents =
        MutableSharedFlow<IssuanceEvent>(
            replay = 0,
            extraBufferCapacity = 16
        )
    override val issuanceEvents = _issuanceEvents.asSharedFlow()

    private var issuanceJob: Job? = null

    override fun getAddDocumentOption(flowType: IssuanceFlowUiConfig): Flow<AddDocumentInteractorPartialState> =
        flow {
            when (val state =
                walletCoreDocumentsController.getScopedDocuments(resourceProvider.getLocale())) {
                is FetchScopedDocumentsPartialState.Failure -> emit(
                    AddDocumentInteractorPartialState.Failure(
                        error = state.errorMessage
                    )
                )

                is FetchScopedDocumentsPartialState.Success -> emit(
                    AddDocumentInteractorPartialState.Success(
                        options = state.documents
                            .sortedBy { it.name.lowercase() }
                            .mapNotNull {
                                if (flowType != IssuanceFlowUiConfig.NO_DOCUMENT || it.isPid) {
                                    DocumentOptionItemUi(
                                        itemData = ListItemData(
                                            itemId = it.configurationId,
                                            mainContentData = ListItemMainContentData.Text(text = it.name),
                                        )
                                    )
                                } else {
                                    null
                                }
                            }
                    )
                )
            }
        }.safeAsync {
            AddDocumentInteractorPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }
    override fun startIssueDocumentAttested(
        issuanceMethod: IssuanceMethod,
        configIds: Set<CredentialConfigurationIdentifier>,
        issuerId: String,
        walletInstanceAttestationSpec: WalletInstanceAttestationSpec,
    ) {
        issuanceJob?.cancel()

        issuanceJob =
            walletCoreDocumentsController.issueDocumentAttested(
                issuanceMethod = issuanceMethod,
                configIds = configIds.map { it.value },
                issuerId = issuerId,
                walletInstanceAttestationSpec = walletInstanceAttestationSpec
            ).onEach { state ->
                    _issuanceState.value = state

                    when (state) {
                        is IssueDocumentPartialState.Success ->
                            _issuanceEvents.tryEmit(IssuanceEvent.Completed)

                        is IssueDocumentPartialState.Failure ->
                            _issuanceEvents.tryEmit(
                                IssuanceEvent.Failed(state.errorMessage)
                            )

                        else -> Unit
                    }
                }.catch { t ->
                    val msg = t.localizedMessage ?: genericErrorMsg
                    _issuanceState.value = IssueDocumentPartialState.Failure(errorMessage = msg)
                    _issuanceEvents.emit(IssuanceEvent.Failed(msg))
                }.launchIn(interactorScope)
    }

    @Deprecated("Use the List<String> version for batch issuance")
    override fun startIssueDocumentAttested(
        issuanceMethod: IssuanceMethod,
        configId: CredentialConfigurationIdentifier,
        issuerId: String,
        walletInstanceAttestationSpec: WalletInstanceAttestationSpec,
    ) {
        startIssueDocumentAttested(
            issuanceMethod = issuanceMethod,
            configIds = setOf(configId),
            issuerId = issuerId,
            walletInstanceAttestationSpec = walletInstanceAttestationSpec
        )
    }

    override fun cancelIssuance() {
        issuanceJob?.cancel()
        issuanceJob = null
    }

    override fun resumeOpenId4VciWithAuthorization(uri: String) {
        walletCoreDocumentsController.resumeOpenId4VciWithAuthorization(uri)
        issuanceJob?.cancel()
        issuanceJob = interactorScope.launch {
            walletCoreDocumentsController.issuanceState.first { response ->
                // Map the controller's plural state to the interactor's singular
                // state, mirroring the normal issuance path (see
                // WalletCoreDocumentsController.issueDocumentWithOpenId4VCI).
                // Terminal states are forwarded; RefreshTokenReceived is an
                // intermediate state, so we keep collecting.
                val mapped: IssueDocumentPartialState? = when (response) {
                    is IssueDocumentsPartialState.Success ->
                        IssueDocumentPartialState.Success(response.documentIds)

                    is IssueDocumentsPartialState.PartialSuccess ->
                        IssueDocumentPartialState.Success(response.documentIds)

                    is IssueDocumentsPartialState.Failure ->
                        IssueDocumentPartialState.Failure(response.errorMessage)

                    is IssueDocumentsPartialState.DeferredSuccess ->
                        IssueDocumentPartialState.DeferredSuccess(response.deferredDocuments)

                    is IssueDocumentsPartialState.UserAuthRequired ->
                        IssueDocumentPartialState.UserAuthRequired(
                            crypto = response.crypto,
                            resultHandler = response.resultHandler,
                        )

                    is IssueDocumentsPartialState.RefreshTokenReceived -> null
                }
                if (mapped == null) {
                    return@first false
                }
                _issuanceState.value = mapped
                when (mapped) {
                    is IssueDocumentPartialState.Success ->
                        _issuanceEvents.tryEmit(IssuanceEvent.Completed)

                    is IssueDocumentPartialState.Failure ->
                        _issuanceEvents.tryEmit(IssuanceEvent.Failed(mapped.errorMessage))

                    else -> Unit
                }
                true
            }
        }
    }
}
