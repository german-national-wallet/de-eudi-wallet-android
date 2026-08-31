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

package eu.europa.ec.issuancefeature.ui.document.details

import androidx.lifecycle.viewModelScope
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.commonfeature.model.DocumentUi
import eu.europa.ec.commonfeature.ui.document_details.transformer.DocumentDetailsTransformer.transformToDocumentDetailsUi
import eu.europa.ec.issuancefeature.interactor.document.DocumentDetailsInteractor
import eu.europa.ec.issuancefeature.interactor.document.DocumentDetailsInteractorPartialState
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.ListItemData
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

data class State(
    val isLoading: Boolean = false,
    val error: ContentErrorConfig? = null,
    val isBottomSheetOpen: Boolean = false,
    val documentDetails: List<ListItemData> = emptyList(),
    val topBarBackgroundColor: String? = null,
    val topBarBackgroundImageUri: String? = null,
    val topBarTextColor: String? = null,
) : ViewState

sealed class Event : ViewEvent {
    data object Init : Event()
    data object Pop : Event()
    data object OnBackPressed : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String,
            val inclusive: Boolean,
        ) : Navigation()
    }
}

@KoinViewModel
class DocumentDetailsViewModel(
    private val documentDetailsInteractor: DocumentDetailsInteractor,
    private val resourceProvider: ResourceProvider,
    @InjectedParam private val documentId: DocumentId,
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> {
                getDocumentDetails(event)
            }

            is Event.Pop -> {
                setState { copy(error = null) }
                setEffect { Effect.Navigation.Pop }
            }

            Event.OnBackPressed -> setEffect { Effect.Navigation.Pop }
        }
    }

    private fun getDocumentDetails(event: Event) {
        setState {
            copy(
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            documentDetailsInteractor.getDocumentDetails(
                documentId = documentId,
            ).collect { response ->
                when (response) {
                    is DocumentDetailsInteractorPartialState.Success -> {
                        val documentUi =
                            response.documentDetailsDomain.transformToDocumentDetailsUi(
                                resourceProvider = resourceProvider,
                                documentFormat = response.documentFormat
                            )
                        updateTopBarStyling(
                            response.topBarBackgroundColor,
                            response.topBarBackgroundImageUri,
                            response.topBarTextColor
                        )
                        addDividersToDocumentDetails(documentUi)
                    }

                    is DocumentDetailsInteractorPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(event) },
                                    errorSubTitle = response.error,
                                    onCancel = { setEvent(Event.Pop) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun addDividersToDocumentDetails(document: DocumentUi) {
        val details = document.documentDetails.map { item ->
            val overlineText = item.overlineText ?: ""
            val nationalityLabel = resourceProvider.getString(R.string.pid_issuance_data_consent_label_nationality)
            val ageOverLabel = resourceProvider.getString(R.string.pid_issuance_data_consent_label_age_equal_or_over)

            item.copy(
                hasTopDivider = overlineText == nationalityLabel,
                hasBottomDivider = overlineText == nationalityLabel || overlineText == ageOverLabel
            )
        }

        setState {
            copy(
                isLoading = false,
                documentDetails = details
            )
        }
    }

    private fun updateTopBarStyling(
        backgroundColor: String?,
        backgroundImageUri: String?,
        textColor: String?,
    ) {
        setState {
            copy(
                topBarBackgroundColor = backgroundColor,
                topBarBackgroundImageUri = backgroundImageUri,
                topBarTextColor = textColor
            )
        }
    }

}