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

package org.sprind.wallet.cardreaderfeature.ui.document.read

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.businesslogic.extension.toUri
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.navigation.DashboardScreens
import org.sprind.wallet.cardreaderfeature.ui.document.privacy.PrivacyPolicyRoute
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.cardreaderfeature.nfc.CardReaderNfcDispatcher
import org.sprind.wallet.cardreaderfeature.nfc.NfcStateProvider

@Composable
internal fun ReadCardScreen(
    navController: NavController,
    viewModel: ReadCardViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // The feature entry point owns NFC lifecycle management so route composables
    // stay focused on rendering and user interaction only. The dispatcher hands
    // detected tags to the ViewModel and can be re-armed (see Effect.RestartNfcReader)
    // so a card left on the sensor is re-discovered and read without a re-tap.
    val nfcDispatcher = remember(context) {
        CardReaderNfcDispatcher(context, viewModel::onNfcTagDetected)
    }

    // NFC can only be switched on from the system settings, which always takes the
    // app out of the foreground, so reading the state on resume keeps the flow in
    // sync with the device without observing the adapter.
    val nfcStateProvider = remember(context) { NfcStateProvider(context) }

    CardReaderRouteHost(
        state = state,
        onEventSend = viewModel::setEvent
    )

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> handleNavigationEffect(context, effect, navController)
                Effect.HideKeyboard -> keyboardController?.hide()
                Effect.ShowKeyboard -> keyboardController?.show()
                Effect.RestartNfcReader -> nfcDispatcher.restart()
                Effect.OpenNfcSettings -> context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                is Effect.StartCall -> {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = "tel:${effect.phoneNumber}".toUri()
                    }
                    context.startActivity(intent)
                }
            }
        }.collect()
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_PAUSE
    ) {
        // Reader mode must not be (re-)enabled while the activity is not resumed,
        // and the dispatcher may have delayed re-arms queued, so tear it down here.
        nfcDispatcher.stop()
        viewModel.setEvent(Event.OnPause)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        nfcDispatcher.start()
        viewModel.setEvent(
            Event.OnResume(
                isNfcEnabled = nfcStateProvider.isNfcEnabled(),
                nfcAntennaPosition = nfcStateProvider.antennaPosition(),
            )
        )
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Init)
    }

    DisposableEffect(Unit) {
        onDispose {
            nfcDispatcher.stop()
        }
    }
}

/**
 * Applies feature-level effects that leave the reader sub-flow.
 *
 * Route-to-route transitions stay inside [CardReaderRouteHost]; this helper is
 * reserved for module navigation, external intents, and issuer details.
 */
private fun handleNavigationEffect(
    context: Context,
    navigationEffect: Effect.Navigation,
    navController: NavController,
) {
    when (navigationEffect) {
        is Effect.Navigation.Pop -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(DashboardScreens.Dashboard.screenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }

        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(DashboardScreens.Dashboard.screenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }

        is Effect.Navigation.Finish -> context.finish()
        is Effect.Navigation.OpenLink -> {
            val intent = Intent(Intent.ACTION_VIEW, navigationEffect.uri)
            context.startActivity(intent)
        }

        is Effect.Navigation.NavigateToIssuerDetails ->
            navController.navigate(navigationEffect.details)

        is Effect.Navigation.NavigateToPrivacyPolicy ->
            navController.navigate(PrivacyPolicyRoute(navigationEffect.url))
    }
}
