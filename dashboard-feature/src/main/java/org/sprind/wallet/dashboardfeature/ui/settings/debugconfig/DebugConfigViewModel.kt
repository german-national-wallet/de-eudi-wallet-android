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

package org.sprind.wallet.dashboardfeature.ui.settings.debugconfig

import androidx.lifecycle.viewModelScope
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.sprind.wallet.businesslogic.config.DebugConfigOverrides
import org.sprind.wallet.corelogic.revocation.WalletRevocationHandler
import org.sprind.wallet.dashboardfeature.interactor.DebugConfigInteractor

data class DebugConfigOverridesState(
    override val walletHostUrl: String,
    override val walletApiKey: String,
    override val otlpHostUrl: String,
    override val otlpAuthToken: String,
    override val pidProviderUrl: String,
): DebugConfigOverrides

fun DebugConfigOverrides.toState() = DebugConfigOverridesState(
    walletHostUrl = walletHostUrl.orEmpty(),
    walletApiKey = walletApiKey.orEmpty(),
    otlpHostUrl = otlpHostUrl.orEmpty(),
    otlpAuthToken = otlpAuthToken.orEmpty(),
    pidProviderUrl = pidProviderUrl.orEmpty(),
)

data class State(val overrides: DebugConfigOverridesState, val showApplyingDialog: Boolean) :
    ViewState

sealed class Event : ViewEvent {
    data class OnWalletHostUrlChanged(val walletHostUrl: String) : Event()
    data class OnWalletApiKeyChanged(val walletApiKey: String) : Event()
    data class OnOtlpHostUrlChanged(val otlpHostUrl: String) : Event()
    data class OnOtlpAuthTokenChanged(val otlpAuthToken: String) : Event()
    data class OnPidProviderUrlChanged(val pidProviderUrl: String) : Event()
    data object OnReset : Event()
    data object OnApply : Event()
    data object OnApplyDeleteWalletData : Event()
    data object OnApplyKeepWalletData : Event()
    data object OnDismissApplyingDialog : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Restart : Navigation()
    }
}

@KoinViewModel
class DebugConfigViewModel(
    private val interactor: DebugConfigInteractor,
    private val walletRevocationHandler: WalletRevocationHandler,
) : MviViewModel<Event, State, Effect>() {

    private var resetJob: Job? = null

    override fun setInitialState(): State =
        State(overrides = interactor.toState(), showApplyingDialog = false)

    override fun handleEvents(event: Event) = when (event) {
        is Event.OnWalletHostUrlChanged -> setState {
            copy(overrides = overrides.copy(walletHostUrl = event.walletHostUrl))
        }
        is Event.OnWalletApiKeyChanged -> setState {
            copy(overrides = overrides.copy(walletApiKey = event.walletApiKey))
        }
        is Event.OnOtlpHostUrlChanged -> setState {
            copy(overrides = overrides.copy(otlpHostUrl = event.otlpHostUrl))
        }
        is Event.OnOtlpAuthTokenChanged -> setState {
            copy(overrides = overrides.copy(otlpAuthToken = event.otlpAuthToken))
        }
        is Event.OnPidProviderUrlChanged -> setState {
            copy(overrides = overrides.copy(pidProviderUrl = event.pidProviderUrl))
        }

        Event.OnApply -> setState {
            copy(showApplyingDialog = true)
        }
        Event.OnApplyDeleteWalletData -> {
            applyDeleteWalletData()
        }
        Event.OnApplyKeepWalletData -> {
            applyState()
        }
        Event.OnReset -> {
            reset()
        }

        Event.OnDismissApplyingDialog -> setState {
            copy(showApplyingDialog = false)
        }
    }

    private fun applyDeleteWalletData() {
        if (resetJob?.isActive == true) return
        resetJob = viewModelScope.launch {
            walletRevocationHandler.resetAfterRevocation {
                applyState()
            }
        }
    }

    private fun applyState() {
        val overrides = viewState.value.overrides
        interactor.run {
            walletHostUrl = overrides.walletHostUrl.takeUnless { it.isBlank() }
            walletApiKey = overrides.walletApiKey.takeUnless { it.isBlank() }
            otlpHostUrl = overrides.otlpHostUrl.takeUnless { it.isBlank() }
            otlpAuthToken = overrides.otlpAuthToken.takeUnless { it.isBlank() }
            pidProviderUrl = overrides.pidProviderUrl.takeUnless { it.isBlank() }
        }
        setEffect { Effect.Navigation.Restart }
    }

    private fun reset() {
        setState {
            copy(
                overrides = DebugConfigOverridesState(
                    walletHostUrl = "",
                    walletApiKey = "",
                    otlpHostUrl = "",
                    otlpAuthToken = "",
                    pidProviderUrl = ""
                ),
                showApplyingDialog = true
            )
        }
    }
}
