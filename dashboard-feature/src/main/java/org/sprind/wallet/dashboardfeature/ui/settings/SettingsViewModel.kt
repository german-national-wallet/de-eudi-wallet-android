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

import android.net.Uri
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.dashboardfeature.interactor.DashboardInteractor
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.RevocationScreens
import org.koin.android.annotation.KoinViewModel
import org.sprind.wallet.businesslogic.config.SettingsLinks

/**
 * @property menuGroups the menu as it is shown, already reduced to the rows this flavor carries.
 * @property appVersion the version this build was cut from.
 * @property appVersionCode the build number, which tells two builds of one version apart.
 */
data class State(
    val menuGroups: List<SettingsMenuGroup> = emptyList(),
    val appVersion: String = "",
    val appVersionCode: Int = 0,
) : ViewState

sealed class Event : ViewEvent {
    data class MenuEntryClicked(val entry: SettingsMenuEntry) : Event()
}

sealed class Effect : ViewSideEffect {
    /** Hands the user to the system's notification settings for this app. */
    data object OpenNotificationSettings : Effect()

    data class ShareLogs(val fileUris: ArrayList<Uri>) : Effect()

    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        data class OpenWebPage(val route: SettingsWebPageRoute) : Navigation()
    }
}

@KoinViewModel
class SettingsViewModel(
    private val dashboardInteractor: DashboardInteractor,
    private val configLogic: ConfigLogic,
) : MviViewModel<Event, State, Effect>() {

    /**
     * Whether the log export is part of this build. The export only carries the log files the log
     * writer leaves behind, and reaching it at all is a piece of the debug menu, so it takes both.
     */
    private val isLogExportAvailable: Boolean
        get() = configLogic.isDebugMenuEnabled && configLogic.isLogWriterEnabled

    override fun setInitialState(): State {
        return State(
            menuGroups = settingsMenuGroups(isLogExportAvailable = isLogExportAvailable),
            appVersion = configLogic.appVersion,
            appVersionCode = configLogic.appVersionCode,
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.MenuEntryClicked -> onMenuEntryClicked(event.entry)
        }
    }

    private fun onMenuEntryClicked(entry: SettingsMenuEntry) {
        val links: SettingsLinks = configLogic.settingsLinks

        when (entry) {
            SettingsMenuEntry.FAQ_AND_SUPPORT -> openWebPage(entry, links.faqAndSupport)

            SettingsMenuEntry.WALLET_REVOCATION -> setEffect {
                Effect.Navigation.SwitchScreen(RevocationScreens.KeyInfo.screenRoute)
            }

            SettingsMenuEntry.NOTIFICATIONS -> setEffect { Effect.OpenNotificationSettings }

            SettingsMenuEntry.ABOUT -> openWebPage(entry, links.about)

            SettingsMenuEntry.ACCESSIBILITY_DECLARATION ->
                openWebPage(entry, links.accessibilityDeclaration)

            SettingsMenuEntry.PRIVACY_POLICY -> openWebPage(entry, links.privacyPolicy)

            SettingsMenuEntry.TERMS_AND_CONDITIONS ->
                openWebPage(entry, links.termsAndConditions)

            SettingsMenuEntry.IMPRINT -> openWebPage(entry, links.imprint)

            SettingsMenuEntry.EXPORT_LOGS -> exportLogs()

            SettingsMenuEntry.DEBUG_CONFIG -> setEffect {
                Effect.Navigation.SwitchScreen(DashboardScreens.DebugConfig.screenRoute)
            }
        }
    }

    private fun openWebPage(entry: SettingsMenuEntry, url: String) {
        setEffect {
            Effect.Navigation.OpenWebPage(
                SettingsWebPageRoute(url = url, titleRes = entry.labelRes)
            )
        }
    }

    private fun exportLogs() {
        if (!isLogExportAvailable) return
        setEffect { Effect.ShareLogs(dashboardInteractor.retrieveLogFileUris()) }
    }
}