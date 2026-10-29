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

package org.sprind.wallet.onboardingfeature.setup.platformauth

import androidx.lifecycle.viewModelScope
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

/**
 * @property isChecking the screen still shows the "checking the screen lock" phase.
 * @property isSheetVisible the "removing the screen lock deletes your data" sheet is shown.
 */
data class PlatformAuthSetupState(
    val isChecking: Boolean = true,
    val isSheetVisible: Boolean = false,
) : ViewState

sealed class PlatformAuthSetupEvent : ViewEvent {
    /** The primary action: asks for the acknowledgement in the sheet. */
    data object Continue : PlatformAuthSetupEvent()

    /** The sheet was dismissed (back, tap outside, swipe) without acknowledging it. */
    data object DismissSheet : PlatformAuthSetupEvent()

    /** The sheet was acknowledged. */
    data object ConfirmSheet : PlatformAuthSetupEvent()
}

sealed class PlatformAuthSetupEffect : ViewSideEffect {
    /** The step is done; the host completes it and moves on. */
    data object Complete : PlatformAuthSetupEffect()
}

/**
 * The platform authentication (screen lock) setup step.
 *
 * The step opens in a short checking phase before it confirms that the screen lock protects the
 * app. The screen lock itself is enforced app-wide by the platform authentication blocking screen,
 * which covers every screen, this one included, while no lock is set; this step only informs and
 * asks the user to acknowledge that removing the lock deletes their data. Only that
 * acknowledgement completes the step: dismissing the sheet keeps the user on the step.
 */
@KoinViewModel
class PlatformAuthSetupViewModel : MviViewModel<PlatformAuthSetupEvent, PlatformAuthSetupState, PlatformAuthSetupEffect>() {

    init {
        viewModelScope.launch {
            delay(CHECKING_DURATION_MILLIS)
            setState { copy(isChecking = false) }
        }
    }

    override fun setInitialState(): PlatformAuthSetupState = PlatformAuthSetupState()

    override fun handleEvents(event: PlatformAuthSetupEvent) {
        when (event) {
            PlatformAuthSetupEvent.Continue -> {
                if (!viewState.value.isChecking) setState { copy(isSheetVisible = true) }
            }

            PlatformAuthSetupEvent.DismissSheet -> setState { copy(isSheetVisible = false) }

            PlatformAuthSetupEvent.ConfirmSheet -> {
                if (viewState.value.isSheetVisible) {
                    setState { copy(isSheetVisible = false) }
                    setEffect { PlatformAuthSetupEffect.Complete }
                }
            }
        }
    }

    companion object {
        /** How long the checking phase is shown, long enough to be perceived and announced. */
        const val CHECKING_DURATION_MILLIS = 1_500L
    }
}
