@file:OptIn(ExperimentalMaterial3Api::class)

package eu.europa.ec.dashboardfeature.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.SystemBroadcastReceiver
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_72
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.extension.getPendingDeepLink
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.dashboardfeature.ui.dashboard.CredentialCatalogueTeaser
import org.sprind.wallet.dashboardfeature.ui.dashboard.StackedCards
import org.sprind.wallet.dashboardfeature.ui.documentdetail.restartApp
import org.sprind.wallet.uilogic.component.BottomNavigationBar
import org.sprind.wallet.uilogic.component.BottomNavigationTab
import org.sprind.wallet.uilogic.component.PidCredentialCard
import org.sprind.wallet.uilogic.component.ScrollFade
import org.sprind.wallet.uilogic.component.ScrollFadeEdge
import org.sprind.wallet.uilogic.component.ScrollFadeHeight
import org.sprind.wallet.uilogic.component.navigateToBottomNavigationTab

@Composable
fun DashboardScreen(
    navHostController: NavController,
    viewModel: DashboardViewModel,
) {
    val context = LocalContext.current
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> handleNavigationEffect(effect, navHostController)
            }
        }.collect()
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        val deepLink = context.getPendingDeepLink()
        deepLink?.let {
            viewModel.setEvent(Event.InitDeepLink(deepLinkUri = deepLink))
        } ?: viewModel.setEvent(Event.Init)
    }

    SystemBroadcastReceiver(
        actions = listOf(
            CoreActions.VCI_RESUME_ACTION,
            CoreActions.VCI_DYNAMIC_PRESENTATION
        )
    ) {
        val keyUri = "uri"

        when (it?.action) {
            CoreActions.VCI_RESUME_ACTION -> it.extras?.getString(keyUri)?.let {
                viewModel.setEvent(Event.OnInterruptedIssuance)
            }

            CoreActions.VCI_DYNAMIC_PRESENTATION -> it.extras?.getString(keyUri)?.let { link ->
                viewModel.setEvent(Event.OnDynamicPresentation(link))
            }
        }
    }

    MainScreen(
        state = state,
        onEventSend = { event -> viewModel.setEvent(event) },
        onBottomNavigationTabSelected = navHostController::navigateToBottomNavigationTab,
    )
}


private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(navigationEffect.popUpToScreenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }

        is Effect.Navigation.OpenDeepLinkAction -> {
            handleDeepLinkAction(
                navController,
                navigationEffect.deepLinkUri,
                navigationEffect.arguments
            )
        }

        is Effect.Navigation.Restart -> restartApp(navController)

        is Effect.Navigation.OpenCredentialCatalogue -> navController.navigate(navigationEffect.route)

        else -> {} // DO Nothing
    }
}

@Composable
fun MainScreen(
    state: State,
    onEventSend: (Event) -> Unit,
    onBottomNavigationTabSelected: (BottomNavigationTab) -> Unit,
) {
    val listState = rememberLazyListState()

    ContentScreen(
        genericErrorDialogConfig = state.errorDialog,
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        topBar = {
            Box {
                TopAppBar(
                    title = {
                        WrapImage(
                            painter = painterResource(R.drawable.d_you_text_mark),
                            contentDescription = stringResource(R.string.global_app_name),
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors()
                        .copy(containerColor = MaterialTheme.colorScheme.background),
                )

                // The list scrolls behind the top bar, so the fade hangs just below the bar's
                // edge, over the list, to show that there is more above.
                ScrollFade(
                    visible = listState.canScrollBackward,
                    edge = ScrollFadeEdge.TOP,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = ScrollFadeHeight),
                )
            }
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = BottomNavigationTab.OVERVIEW,
                onTabSelected = onBottomNavigationTabSelected,
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Room for the QR scanner button that floats over the bottom end, so the catalogue
            // teaser's plus button can be scrolled clear of it.
            contentPadding = PaddingValues(
                bottom = paddingValues.calculateBottomPadding() + SCANNER_BUTTON_CLEARANCE,
                top = paddingValues.calculateTopPadding(),
                start = SPACING_MEDIUM.dp,
                end = SPACING_MEDIUM.dp
            ),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
        ) {
            // Show onboarding UI when no PID exists
            if (state.pidDocument == null) {
                item {
                    PidOnboardingSection(
                        eidCardType = state.eidCardType,
                        isSimulatedEidCardEnabled = state.isSimulatedEidCardEnabled,
                        onIssueDocument = {
                            onEventSend(
                                Event.IssuePreferredPidDocument
                            )
                        },
                        onToggleEidCard = { onEventSend(Event.ToggleEidCardSimulation(it)) }
                    )
                }
            } else {
                // Current DashboardScreen behavior - only issued documents
                item {
                    PidCredentialCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onEventSend(Event.CardPressed(state.pidDocument.id)) },
                    )
                }
            }

            // EAA documents - stacked layout
            if (state.eaaDocuments.isNotEmpty()) {
                item {
                    StackedCards(
                        cards = state.eaaDocuments,
                        onClick = { docId ->
                            onEventSend(Event.CardPressed(docId))
                        },
                        overlapDp = 40.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                CredentialCatalogueTeaser(
                    onClick = { onEventSend(Event.OpenCredentialCatalogue) },
                )
            }
        }
    }
}

// The 56dp scanner button plus the gap the design keeps around it.
private val SCANNER_BUTTON_CLEARANCE = SPACING_LARGE_72.dp

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun MainScreenPreview() {
    PreviewTheme {
        MainScreen(
            state = State(
                isLoading = false,
                pidDocument = null,
            ),
            onEventSend = {},
            onBottomNavigationTabSelected = {},
        )
    }
}
