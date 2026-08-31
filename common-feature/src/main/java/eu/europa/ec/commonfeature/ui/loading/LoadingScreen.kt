package eu.europa.ec.commonfeature.ui.loading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.extension.openDeepLink
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun LoadingScreen(
    navController: NavController,
    viewModel: LoadingViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ContentScreen(
        isLoading = state.error == null,
        navigatableAction = if (state.isCancellable) {
            ScreenNavigateAction.CANCELABLE
        } else {
            ScreenNavigateAction.NONE
        },
        onBack = if (state.isCancellable) {
            { viewModel.setEvent(Event.GoBack) }
        } else {
            null
        },
        contentErrorConfig = state.error
    ) { paddingValues ->
        Content(
            state = state,
            paddingValues = paddingValues
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation.SwitchScreen -> {
                    navController.navigate(effect.screenRoute) {
                        popUpTo(viewModel.getCallerScreen().screenRoute) {
                            inclusive = true
                        }
                    }
                }

                is Effect.Navigation.PopBackStackUpTo -> {
                    navController.popBackStack(
                        route = effect.screenRoute,
                        inclusive = effect.inclusive
                    )
                }

                is Effect.Navigation.DeepLink -> {
                    context.openDeepLink(effect.link)
                    effect.routeToPop?.let { route ->
                        navController.navigate(route) {
                            popUpTo(viewModel.getCallerScreen().screenRoute) {
                                inclusive = true
                            }
                        }
                    }
                }
            }
        }.collect()
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Initialize)
        viewModel.setEvent(Event.DoWork(context))
    }
}

@Composable
private fun Content(
    state: State,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.Top
    ) {
        ContentHeader(
            modifier = Modifier.fillMaxWidth(),
            config = state.headerConfig,
        )
    }
}