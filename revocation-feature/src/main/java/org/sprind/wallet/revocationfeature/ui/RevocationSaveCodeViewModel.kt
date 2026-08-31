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

package org.sprind.wallet.revocationfeature.ui

import androidx.lifecycle.viewModelScope
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.sprind.wallet.revocationfeature.interactor.RevocationInteractor

data class State(
    val revocationCode: String?,
    val savedCodeConfirmation: Boolean,
) : ViewState

sealed class Event : ViewEvent {
    data class OnUserHasSavedCodeChanged(val value: Boolean) : Event()
}

sealed class Effect : ViewSideEffect

@KoinViewModel
class RevocationSaveCodeViewModel(
    private val revocationInteractor: RevocationInteractor,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State =
        State(revocationCode = null, savedCodeConfirmation = false)

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.OnUserHasSavedCodeChanged -> onUserHasSavedCodeChanged(event.value)
        }
    }

    init {
        viewModelScope.launch {
            val revocationCode = revocationInteractor.getRevocationCode()
                ?.chunked(4)
                ?.joinToString(" ")
            val savedCodeConfirmation = revocationInteractor.hasUserConfirmedSavingCode()
            setState {
                copy(revocationCode = revocationCode, savedCodeConfirmation = savedCodeConfirmation)
            }
        }
    }

    private fun onUserHasSavedCodeChanged(value: Boolean) {
        revocationInteractor.storeUserConfirmedSavingCode(value = value)
        setState {
            copy(savedCodeConfirmation = value)
        }
    }
}