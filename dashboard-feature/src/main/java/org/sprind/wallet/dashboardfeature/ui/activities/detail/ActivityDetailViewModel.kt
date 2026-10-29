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

package org.sprind.wallet.dashboardfeature.ui.activities.detail

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
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityDetailUi
import org.sprind.wallet.dashboardfeature.ui.settings.SettingsWebPageRoute

data class ActivityDetailState(
    val isLoading: Boolean = true,
    val detail: ActivityDetailUi? = null,
    val showDeleteDialog: Boolean = false,
    val showCredentialSheet: Boolean = false,
) : ViewState

sealed class ActivityDetailEvent : ViewEvent {
    data class LinkClicked(val uri: String) : ActivityDetailEvent()
    data object ReportClicked : ActivityDetailEvent()
    data object ViewCredentialClicked : ActivityDetailEvent()
    data object CredentialSheetDismissed : ActivityDetailEvent()
    data object HelpClicked : ActivityDetailEvent()
    data object DeleteClicked : ActivityDetailEvent()
    data object DeleteConfirmed : ActivityDetailEvent()
    data object DeleteDismissed : ActivityDetailEvent()
    data object BackPressed : ActivityDetailEvent()
}

sealed class ActivityDetailEffect : ViewSideEffect {
    data class OpenUrl(val uri: String) : ActivityDetailEffect()
    data class OpenWebPage(val route: SettingsWebPageRoute) : ActivityDetailEffect()
    data object Pop : ActivityDetailEffect()
}

@KoinViewModel
class ActivityDetailViewModel(
    private val activityInteractor: ActivityInteractor,
    private val configLogic: ConfigLogic,
    @InjectedParam private val activityId: String,
) : MviViewModel<ActivityDetailEvent, ActivityDetailState, ActivityDetailEffect>() {

    init {
        viewModelScope.launch {
            activityInteractor.detail(activityId).collect { detail ->
                if (detail == null) {
                    setEffect { ActivityDetailEffect.Pop }
                } else {
                    setState { copy(isLoading = false, detail = detail) }
                }
            }
        }
    }

    override fun setInitialState(): ActivityDetailState = ActivityDetailState()

    override fun handleEvents(event: ActivityDetailEvent) {
        when (event) {
            is ActivityDetailEvent.LinkClicked -> openUrl(event.uri)
            ActivityDetailEvent.ReportClicked -> viewState.value.detail?.reportUri?.let(::openUrl)
            ActivityDetailEvent.ViewCredentialClicked -> setState { copy(showCredentialSheet = true) }
            ActivityDetailEvent.CredentialSheetDismissed -> setState { copy(showCredentialSheet = false) }
            ActivityDetailEvent.HelpClicked -> setEffect {
                ActivityDetailEffect.OpenWebPage(
                    SettingsWebPageRoute(
                        url = configLogic.settingsLinks.faqAndSupport,
                        titleRes = R.string.activities_overview_sec_button,
                    )
                )
            }
            ActivityDetailEvent.DeleteClicked -> setState { copy(showDeleteDialog = true) }
            ActivityDetailEvent.DeleteDismissed -> setState { copy(showDeleteDialog = false) }
            ActivityDetailEvent.DeleteConfirmed -> {
                setState { copy(showDeleteDialog = false) }
                viewModelScope.launch { activityInteractor.delete(activityId) }
            }

            ActivityDetailEvent.BackPressed -> setEffect { ActivityDetailEffect.Pop }
        }
    }

    private fun openUrl(uri: String) {
        val target = when (URI_SCHEME.find(uri)?.groupValues?.get(1)?.lowercase()) {
            null -> "https://$uri"
            in OPENABLE_SCHEMES -> uri
            else -> return
        }
        setEffect { ActivityDetailEffect.OpenUrl(target) }
    }
}

// A port ("host:8080") is not a scheme.
private val URI_SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(?!\\d)")
private val OPENABLE_SCHEMES = setOf("https", "http", "mailto")
