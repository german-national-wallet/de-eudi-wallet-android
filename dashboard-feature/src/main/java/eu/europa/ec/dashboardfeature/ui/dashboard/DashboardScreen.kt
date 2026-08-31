@file:OptIn(ExperimentalMaterial3Api::class)

package eu.europa.ec.dashboardfeature.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.util.extractFullNameFromDocumentOrEmpty
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.SystemBroadcastReceiver
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ContentScreenWithDrawerMenu
import eu.europa.ec.uilogic.component.content.DrawerItemDataClass
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.NAVIGATION_ICON_BOX_SIZE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.extension.getPendingDeepLink
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.sprind.wallet.dashboardfeature.ui.dashboard.StackedCards
import org.sprind.wallet.dashboardfeature.ui.documentdetail.restartApp
import org.sprind.wallet.designsystem.typography.CustomTypography

private const val DASHBOARD_DRAWER_LOG_EXPORT = "dashboard_drawer_log_export"

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

        else -> {} // DO Nothing
    }
}

@Composable
fun MainScreen(
    state: State,
    onEventSend: (Event) -> Unit,
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // The drawer is the debug menu, so it is limited to the flavors carrying one; log export
    // additionally needs the log files the log writer of those flavors leaves behind.
    val drawerItems = buildList {
        if (state.isDebugMenuEnabled && state.isLogWriterEnabled) {
            add(
                DrawerItemDataClass(
                    id = DASHBOARD_DRAWER_LOG_EXPORT,
                    label = stringResource(R.string.dashboard_drawer_log_export),
                    iconData = AppIcons.Edit,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            onEventSend(Event.ExportLogs(context))
                        }
                    })
            )
        }
    }

    if (drawerItems.isEmpty()) {
        DashboardContent(
            state = state,
            onEventSend = onEventSend,
            onOpenDrawerMenu = null,
        )
    } else {
        ContentScreenWithDrawerMenu(
            drawerTitle = stringResource(R.string.dashboard_drawer_title),
            drawerItems = drawerItems,
            drawerState = drawerState,
        ) {
            DashboardContent(
                state = state,
                onEventSend = onEventSend,
                onOpenDrawerMenu = { scope.launch { drawerState.open() } },
            )
        }
    }
}

@Composable
private fun DashboardContent(
    state: State,
    onEventSend: (Event) -> Unit,
    onOpenDrawerMenu: (() -> Unit)?,
) {
    ContentScreen(
        genericErrorDialogConfig = state.errorDialog,
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = SPACING_EXTRA_MEDIUM.dp),
                title = { },
                colors = TopAppBarDefaults.topAppBarColors()
                    .copy(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (onOpenDrawerMenu != null) {
                        AppIcons.Menu.resourceId?.let { resId ->
                            Box(
                                modifier = Modifier
                                    .padding(start = SPACING_EXTRA_SMALL.dp)
                                    .size(NAVIGATION_ICON_BOX_SIZE.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        onClickLabel = stringResource(id = R.string.content_description_open_menu_action),
                                        role = Role.Button,
                                        onClick = onOpenDrawerMenu,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(id = resId),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    contentDescription = stringResource(id = AppIcons.Menu.contentDescriptionId)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
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
                    IssuedDocumentCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensionResource(R.dimen.document_card_height))
                            .clickable {
                                onEventSend(Event.CardPressed(state.pidDocument.id))
                            },
                        documentType = stringResource(R.string.global_pid_credential_name),
                        userFullName = extractFullNameFromDocumentOrEmpty(state.pidDocument)
                    )
                }
            }

            // EAA documents - stacked layout
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
    }
}

@Composable
private fun IssuedDocumentCard(modifier: Modifier, documentType: String, userFullName: String) {
    Card(
        modifier = modifier
            .border(
                width = BORDER_STROKE_1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp)
            )
            .testTag("digitalCard"),
        colors = CardDefaults.cardColors(
            containerColor = ThemeColors.primaryPid
        ),
        shape = RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Icon(
                modifier = Modifier
                    .align(Alignment.TopEnd),
                painter = painterResource(id = R.drawable.ic_eagle_right),
                contentDescription = "Eagle",
                tint = ThemeColors.onPrimaryPid.copy(alpha = 0.15f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
            ) {
                Text(
                    text = documentType,
                    style = CustomTypography.titleMediumLarge.copy(color = ThemeColors.onPrimaryPid),
                )

                Text(
                    text = userFullName,
                    style = MaterialTheme.typography.bodyMedium.copy(color = ThemeColors.onPrimaryPid)
                )
            }

            Icon(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(SPACING_MEDIUM.dp),
                painter = painterResource(id = R.drawable.ic_info),
                contentDescription = "Info",
                tint = ThemeColors.onPrimaryPid
            )
        }

    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun MainScreenPreview() {
    PreviewTheme {
        MainScreen(
            state = State(
                isLoading = false,
                isDebugMenuEnabled = true,
                isLogWriterEnabled = true,
                pidDocument = null,
            ),
            onEventSend = {},
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun MainScreenWithoutDrawerMenuPreview() {
    PreviewTheme {
        MainScreen(
            state = State(
                isLoading = false,
                isDebugMenuEnabled = false,
                isLogWriterEnabled = false,
                pidDocument = null,
            ),
            onEventSend = {},
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun MainCardPreview() {
    PreviewTheme {
        IssuedDocumentCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.document_card_height))
                .clickable {},
            documentType = stringResource(R.string.pid_inspection_pid_details_title),
            userFullName = "Anna Musterfrau"
        )
    }
}
