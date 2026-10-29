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

package org.sprind.wallet.dashboardfeature.ui.activities

import androidx.lifecycle.viewModelScope
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import org.sprind.wallet.dashboardfeature.interactor.ActivityInteractor
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivitySectionUi
import org.sprind.wallet.dashboardfeature.ui.settings.SettingsWebPageRoute

/**
 * @property isCredentialActivities whether this lists one credential's activities rather than
 * the whole wallet's, which is shown as a pushed screen instead of a tab.
 */
data class ActivitiesState(
    val isLoading: Boolean = true,
    val sections: List<ActivitySectionUi> = emptyList(),
    val isCredentialActivities: Boolean = false,
    val showDeleteAllDialog: Boolean = false,
) : ViewState

sealed class ActivitiesEvent : ViewEvent {
    data class ActivityClicked(val activityId: String) : ActivitiesEvent()
    data object HowToUseClicked : ActivitiesEvent()
    data object DeleteAllClicked : ActivitiesEvent()
    data object DeleteAllConfirmed : ActivitiesEvent()
    data object DeleteAllDismissed : ActivitiesEvent()
    data object BackPressed : ActivitiesEvent()
}

sealed class ActivitiesEffect : ViewSideEffect {
    data class OpenActivity(val route: ActivityDetailRoute) : ActivitiesEffect()
    data class OpenWebPage(val route: SettingsWebPageRoute) : ActivitiesEffect()
    data object Pop : ActivitiesEffect()
}

/** @param documentId the credential whose activities to list; blank for the whole wallet. */
@KoinViewModel
class ActivitiesViewModel(
    private val activityInteractor: ActivityInteractor,
    private val configLogic: ConfigLogic,
    @InjectedParam private val documentId: String,
) : MviViewModel<ActivitiesEvent, ActivitiesState, ActivitiesEffect>() {

    init {
        viewModelScope.launch {
            activityInteractor.sections(documentId.ifBlank { null }).collect { sections ->
                setState { copy(isLoading = false, sections = sections) }
            }
        }
    }

    override fun setInitialState(): ActivitiesState =
        ActivitiesState(isCredentialActivities = documentId.isNotBlank())

    override fun handleEvents(event: ActivitiesEvent) {
        when (event) {
            is ActivitiesEvent.ActivityClicked ->
                setEffect { ActivitiesEffect.OpenActivity(ActivityDetailRoute(event.activityId)) }

            ActivitiesEvent.HowToUseClicked -> setEffect {
                ActivitiesEffect.OpenWebPage(
                    SettingsWebPageRoute(
                        url = configLogic.settingsLinks.faqAndSupport,
                        titleRes = R.string.activities_overview_sec_button,
                    )
                )
            }

            ActivitiesEvent.DeleteAllClicked -> setState { copy(showDeleteAllDialog = true) }
            ActivitiesEvent.DeleteAllDismissed -> setState { copy(showDeleteAllDialog = false) }
            ActivitiesEvent.DeleteAllConfirmed -> {
                setState { copy(showDeleteAllDialog = false) }
                viewModelScope.launch { activityInteractor.deleteAll() }
            }

            ActivitiesEvent.BackPressed -> setEffect { ActivitiesEffect.Pop }
        }
    }
}
