package eu.europa.ec.startupfeature.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialog
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.navigation.ModuleRoute
import eu.europa.ec.uilogic.navigation.StartupScreens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    Content(
        state = state,
        effectFlow = viewModel.effect,
        onNavigationRequested = {
            when (it) {
                is Effect.Navigation.SwitchModule -> {
                    navController.navigate(it.moduleRoute.route) {
                        popUpTo(ModuleRoute.StartupModule.route) { inclusive = true }
                    }
                }

                is Effect.Navigation.SwitchScreen -> {
                    navController.navigate(it.route) {
                        popUpTo(StartupScreens.Splash.screenRoute) { inclusive = true }
                    }
                }
            }
        }
    )

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Initialize)
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    visibilityState: Boolean = false,
    onNavigationRequested: (navigationEffect: Effect.Navigation) -> Unit,
) {
    val visibilityState = remember {
        MutableTransitionState(visibilityState).apply {
            targetState = true
        }
    }
    Scaffold { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.bg_splash),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(
                    visibleState = visibilityState,
                    enter = fadeIn(animationSpec = tween(state.logoAnimationDuration)),
                    exit = fadeOut(animationSpec = tween(state.logoAnimationDuration)),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_splash_icon),
                            contentDescription = null,
                            modifier = Modifier
                                .width(120.dp)
                                .height(120.dp),
                            contentScale = ContentScale.FillBounds
                        )
                    }
                }
            }
            state.errorDialog?.let { config ->
                GenericErrorDialog(config = config)
            }
        }
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
            }
        }.collect()
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
fun SplashPreview() {
    PreviewTheme {
        Content(state = State(), effectFlow = MutableSharedFlow(), visibilityState = true) { }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
fun SplashWithErrorDialogPreview() {
    PreviewTheme {
        Content(
            state = State(
            errorDialog = GenericErrorDialogConfig(
                titleRes = R.string.global_error_title,
                bodyTextRes = R.string.global_error_paragraph,
                errorCode = "UNKNOWN",
                traceId = "34iu234082034u02934j23o420394j32",
                primaryButtonTextRes = R.string.global_error_prim_button,
                onDismiss = { },
                onPrimaryButtonClick = { }
            ),
        ), effectFlow = MutableSharedFlow(), visibilityState = true) { }
    }
}