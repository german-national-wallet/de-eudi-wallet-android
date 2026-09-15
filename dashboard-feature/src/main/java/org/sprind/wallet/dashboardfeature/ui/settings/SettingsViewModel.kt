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

package org.sprind.wallet.dashboardfeature.ui.settings

import android.content.Context
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.uilogic.extension.shareLogs
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel

/**
 * @property isDebugMenuEnabled whether the flavor carries a debug menu.
 * @property isLogWriterEnabled whether the flavor leaves log files behind for the log export.
 */
data class State(
    val isDebugMenuEnabled: Boolean = false,
    val isLogWriterEnabled: Boolean = false,
) : ViewState

sealed class Event : ViewEvent {
    data class ExportLogs(val context: Context) : Event()
}

/** The screen has nothing to navigate to yet; the settings themselves arrive with WD-3959. */
sealed class Effect : ViewSideEffect

@KoinViewModel
class SettingsViewModel(
    private val dashboardInteractor: DashboardInteractor,
    private val configLogic: ConfigLogic,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
        return State(
            isDebugMenuEnabled = configLogic.isDebugMenuEnabled,
            isLogWriterEnabled = configLogic.isLogWriterEnabled,
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.ExportLogs -> {
                if (configLogic.isLogWriterEnabled) {
                    event.context.shareLogs(dashboardInteractor.retrieveLogFileUris())
                }
            }
        }
    }
}
