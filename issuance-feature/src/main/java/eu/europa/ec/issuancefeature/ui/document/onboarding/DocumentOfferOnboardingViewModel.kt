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

package eu.europa.ec.issuancefeature.ui.document.onboarding

import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

data class State(
    val showCloseConfirmationDialog: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    data object EnterPin : Event()
    data object Decline : Event()
    data object Cancel : Event()
    data object DismissCloseConfirmationDialog : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class EnterPin(val offerSerializedConfig: String) : Navigation()
        data object Decline : Navigation()
        data object CloseApp : Navigation()
    }
}

@KoinViewModel
class DocumentOfferOnboardingViewModel(
    @InjectedParam private val offerSerializedConfig: String,
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State {
        return State()
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.EnterPin -> {
                setEffect { Effect.Navigation.EnterPin(offerSerializedConfig) }
            }

            is Event.Decline -> {
                setState { copy(showCloseConfirmationDialog = false) }
                setEffect { Effect.Navigation.Decline }
            }

            is Event.Cancel -> {
                setState { copy(showCloseConfirmationDialog = true) }
            }

            is Event.DismissCloseConfirmationDialog -> {
                setState { copy(showCloseConfirmationDialog = false) }
            }
        }
    }
}