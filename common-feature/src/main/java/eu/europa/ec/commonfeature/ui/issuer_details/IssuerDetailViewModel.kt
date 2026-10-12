package eu.europa.ec.commonfeature.ui.issuer_details

import android.net.Uri
import androidx.core.net.toUri
import eu.europa.ec.commonfeature.ui.issuer_details.model.IssuerInfo
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

data class State(
    val issuerData: IssuerInfo,
) : ViewState

sealed class Event : ViewEvent {
    data object Pop : Event()
    data object Report : Event()
    data object HelpPressed : Event()
    data object PrivacyPolicyPressed : Event()
}

sealed class Effect : ViewSideEffect {

    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data class SwitchScreen(val route: String) : Navigation()
        data class OpenLink(val uri: Uri) : Navigation()
    }
}

@KoinViewModel
class IssuerDetailsViewModel(
    @InjectedParam private val issuerInfo: IssuerInfo,
) :
    MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State {
        return State(
            issuerData = issuerInfo
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            Event.Pop -> doNavigation(NavigationType.Pop)
            Event.Report -> { /* TODO: Nothing to do now */ }
            Event.HelpPressed -> { /* TODO: Nothing to do now */ }
            Event.PrivacyPolicyPressed -> openPrivacyPolicy()
        }
    }

    private fun openPrivacyPolicy() {
        val privacyPolicy = viewState.value.issuerData.privacyPolicy.trim()
        if (privacyPolicy.isEmpty()) return
        setEffect { Effect.Navigation.OpenLink(privacyPolicy.toPrivacyPolicyUri()) }
    }

    private fun doNavigation(navigationType: NavigationType) {
        when (navigationType) {
            is NavigationType.PushScreen -> {
                setEffect { Effect.Navigation.SwitchScreen(navigationType.screen.screenRoute) }
            }

            is NavigationType.Pop, NavigationType.Finish -> {
                setEffect { Effect.Navigation.Pop }
            }

            is NavigationType.PushRoute -> {
                setEffect { Effect.Navigation.SwitchScreen(navigationType.route) }
            }

            else -> {}
        }
    }
}

/** Issuers may publish the policy without a scheme (e.g. `issuer.de/privacy`); such links open over https. */
private fun String.toPrivacyPolicyUri(): Uri {
    val uri = toUri()
    return if (uri.scheme == null) "https://$this".toUri() else uri
}
