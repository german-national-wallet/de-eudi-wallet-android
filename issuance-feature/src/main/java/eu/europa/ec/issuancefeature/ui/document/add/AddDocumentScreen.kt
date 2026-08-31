@file:OptIn(ExperimentalMaterial3Api::class)

package eu.europa.ec.issuancefeature.ui.document.add

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.uilogic.component.SystemBroadcastReceiver
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.getPendingDeepLink
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun AddDocumentScreen(
    navController: NavController,
    viewModel: AddDocumentViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    MainContent(
        state = state,
        onEventSend = viewModel::setEvent,
    )

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_PAUSE
    ) {
        viewModel.setEvent(Event.OnPause)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.Init.DeepLinkReceived(context.getPendingDeepLink()))
    }

    SystemBroadcastReceiver(
        actions = listOf(
            CoreActions.VCI_RESUME_ACTION,
            CoreActions.VCI_DYNAMIC_PRESENTATION
        )
    ) {
        when (it?.action) {
            CoreActions.VCI_RESUME_ACTION -> it.extras?.getString("uri")?.let { link ->
                viewModel.setEvent(Event.OnResumeIssuance(link))
            }

            CoreActions.VCI_DYNAMIC_PRESENTATION -> it.extras?.getString("uri")?.let { link ->
                viewModel.setEvent(Event.OnDynamicPresentation(link))
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(
                    navController = navController,
                    navigationEffect = effect,
                    context = context
                )
            }
        }.collect()
    }
}

private fun onNavigationRequested(
    navController: NavController,
    navigationEffect: Effect.Navigation,
    context: Context,
) {
    when (navigationEffect) {
        is Effect.Navigation.Pop -> navController.popBackStack()
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                navigationEffect.popUpRoute?.let { popUpToRoute ->
                    popUpTo(popUpToRoute) {
                        inclusive = true
                    }
                } ?: popUpTo(IssuanceScreens.AddDocument.screenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }

        is Effect.Navigation.Finish -> context.finish()
        is Effect.Navigation.OpenDeepLinkAction -> handleDeepLinkAction(
            navController,
            navigationEffect.deepLinkUri,
            navigationEffect.arguments
        )
    }
}

@Composable
private fun MainContent(
    state: State,
    onEventSend: (Event) -> Unit,
) {
    ContentScreen(
        genericErrorDialogConfig = state.errorDialog,
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = SPACING_EXTRA_MEDIUM.dp),
                title = { },
                colors = TopAppBarDefaults.topAppBarColors()
                    .copy(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { }
}