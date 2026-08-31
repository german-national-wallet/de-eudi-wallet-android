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
import eu.europa.ec.uilogic.serializer.UiSerializer
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

class State(): ViewState

sealed class Event: ViewEvent {
    class EnterPin(): Event()
    class Decline(): Event()
}

sealed class Effect: ViewSideEffect {
    sealed class Navigation: Effect() {
        class EnterPin(val offerSerializedConfig: String): Navigation()
        object Decline: Navigation()
        object CloseApp: Navigation()
    }
}

@KoinViewModel
class DocumentOfferOnboardingViewModel(
    private val uiSerializer: UiSerializer,
    @InjectedParam private val offerSerializedConfig: String,
): MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State {
        return State()
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.EnterPin -> {
                setEffect {
                    Effect.Navigation.EnterPin(offerSerializedConfig)
                }
            };
            is Event.Decline -> {
                setEffect {
                    Effect.Navigation.Decline
                }
            }
        }
    }
}