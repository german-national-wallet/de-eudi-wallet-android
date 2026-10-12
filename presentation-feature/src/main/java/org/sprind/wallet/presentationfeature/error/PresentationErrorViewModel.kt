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

package org.sprind.wallet.presentationfeature.error

import android.net.Uri
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.PresentationScreens
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

/**
 * The blocking error screen renders exactly the resolved [config].
 *
 * @property pendingReportUrl The report target the user is asked to confirm before
 *   leaving the app; `null` while no confirmation is shown.
 */
data class State(
    val config: PresentationErrorUiConfig,
    val pendingReportUrl: String? = null,
) : ViewState

sealed class Event : ViewEvent {
    data object PrimaryActionPressed : Event()
    data object SecondaryActionPressed : Event()
    data object ClosePressed : Event()
    data object ReportRedirectConfirmed : Event()
    data object ReportRedirectDismissed : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        /** Leave the blocking screen, back to where the presentation started. */
        data object Pop : Navigation()

        /** Leave the presentation flow and open the wallet overview. */
        data object ToDashboard : Navigation()

        /** Open [url] outside the app; the blocking screen stays in place. */
        data class OpenUrl(val url: String) : Navigation()
    }
}

/**
 * Drives [PresentationErrorScreen] (WD-4274). Navigation carries only [reason] and
 * [traceId]; the content is rebuilt as [State] via [PresentationErrorUiConfig.blocking],
 * and button presses resolve to navigation [Effect]s. Reporting asks for confirmation
 * before opening the report-issue page.
 *
 * @param traceId The trace ID to show; empty when absent (route arguments are strings).
 */
@KoinViewModel
class PresentationErrorViewModel(
    @InjectedParam private val reason: BlockingErrorReason,
    @InjectedParam private val traceId: String,
    private val configLogic: ConfigLogic,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State(
        config = PresentationErrorUiConfig.blocking(
            reason = reason,
            traceId = traceId.ifEmpty { null },
        )
    )

    override fun handleEvents(event: Event) {
        when (event) {
            Event.PrimaryActionPressed -> onAction(viewState.value.config.primaryAction.kind)
            Event.SecondaryActionPressed ->
                viewState.value.config.secondaryAction?.let { onAction(it.kind) }

            Event.ClosePressed -> setEffect { Effect.Navigation.ToDashboard }

            Event.ReportRedirectConfirmed -> viewState.value.pendingReportUrl?.let { url ->
                setState { copy(pendingReportUrl = null) }
                setEffect { Effect.Navigation.OpenUrl(url) }
            }

            Event.ReportRedirectDismissed -> setState { copy(pendingReportUrl = null) }
        }
    }

    private fun onAction(kind: PresentationErrorAction.Kind) {
        val navigation = when (kind) {
            PresentationErrorAction.Kind.TO_OVERVIEW -> Effect.Navigation.ToDashboard

            PresentationErrorAction.Kind.REPORT_ISSUE -> {
                setState {
                    copy(pendingReportUrl = configLogic.environmentConfig.reportIssueUrl)
                }
                return
            }

            PresentationErrorAction.Kind.DISMISS -> Effect.Navigation.Pop

            // Blocking screens never offer retry: the presentation session is already
            // torn down when we navigate here.
            PresentationErrorAction.Kind.RETRY -> Effect.Navigation.Pop
        }
        setEffect { navigation }
    }
}

/**
 * The route of [PresentationScreens.PresentationError] for [reason]. The trace ID is
 * URL encoded and empty when absent.
 */
fun presentationErrorRoute(reason: BlockingErrorReason, traceId: String?): String {
    val paramKey = PresentationScreens.PresentationError.ParamKey
    return PresentationScreens.PresentationError.screenRoute
        .replace("{${paramKey.REASON}}", reason.name)
        .replace("{${paramKey.TRACE_ID}}", Uri.encode(traceId.orEmpty()))
}
