package eu.europa.ec.commonfeature.ui.document_success

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.ui.success.SuccessImage
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ClickableArea
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SIZE_100
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.Banner
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.ExpandableListItemData
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapExpandableListItem
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.extension.cacheDeepLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.EaaDocumentCard

@Composable
fun DocumentSuccessScreen(
    navController: NavController,
    viewModel: DocumentSuccessViewModel,
) {
    val context = LocalContext.current
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    ContentScreen(
        isLoading = false,
        stickyBottom = { padding ->
            state.stickyButtonText?.let { text ->
                WrapStickyBottomContent(
                    stickyBottomModifier = Modifier
                        .fillMaxWidth()
                        .padding(padding),
                    stickyBottomConfig = StickyBottomConfig(
                        showDivider = false,
                        type = StickyBottomType.OneButton(
                            config = ButtonConfig(
                                type = ButtonType.PRIMARY,
                                enabled = !state.isLoading,
                                onClick = { viewModel.setEvent(Event.StickyButtonPressed) }
                            )
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            SPACING_SMALL.dp,
                            Alignment.CenterHorizontally
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WrapText(
                            text = text,
                            textConfig = TextConfig(
                                style = ThemeTextStyles.onPrimaryButton,
                                color = ThemeColors.onPrimaryButton,
                            )
                        )
                        if (state.redirectUrlAvailable) {
                            WrapIcon(
                                iconData = AppIcons.OpenNew,
                                customTint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        },
        navigatableAction = ScreenNavigateAction.NONE,
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { event -> viewModel.setEvent(event) },
            onNavigationRequested = { navigationEffect ->
                when (navigationEffect) {
                    is Effect.Navigation.SwitchScreen -> {
                        navController.navigate(navigationEffect.screenRoute) {
                            navigationEffect.popUpRoute?.let { popUpToRoute ->
                                popUpTo(popUpToRoute) {
                                    inclusive = true
                                }
                            }
                        }
                    }

                    is Effect.Navigation.PopBackStackUpTo -> {
                        navController.popBackStack(
                            route = navigationEffect.screenRoute,
                            inclusive = navigationEffect.inclusive
                        )
                    }

                    is Effect.Navigation.DeepLink -> {
                        context.cacheDeepLink(navigationEffect.link)
                        navigationEffect.routeToPop?.let {
                            navController.popBackStack(
                                route = it,
                                inclusive = false
                            )
                        } ?: navController.popBackStack()
                    }

                    is Effect.Navigation.Pop -> navController.popBackStack()
                }
            },
            paddingValues = paddingValues
        )
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.DoWork)
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues,
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Always show header
        ContentHeader(
            modifier = Modifier
                .fillMaxWidth(),
            config = state.headerConfig,
        )

        state.eaaCardData?.let { cardData ->
            EaaDocumentCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SPACING_MEDIUM.dp),
                data = cardData,
            )
        }

        if (state.items.isNotEmpty()) {
            SuccessContentWithItems(state, onEventSend)
        }
    }

    // Shown when no items
    if (state.items.isEmpty()) {
        SuccessContentNoItems()
    }

    // Effect handler
    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            if (effect is Effect.Navigation) {
                onNavigationRequested(effect)
            }
        }.collect()
    }
}


@Composable
private fun SuccessContentWithItems(
    state: State,
    onEventSend: (Event) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = SPACING_SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        state.items.forEach { successItem ->
            WrapExpandableListItem(
                data = ExpandableListItemData(
                    collapsed = successItem.collapsedUiItem.uiItem,
                    expanded = successItem.expandedUiItems
                ),
                onItemClick = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(SPACING_SMALL.dp)
                    )
                    .padding(horizontal = SPACING_MEDIUM.dp),
                hideSensitiveContent = false,
                isExpanded = successItem.collapsedUiItem.isExpanded,
                onExpandedChange = {
                    onEventSend(
                        Event.ExpandOrCollapseSuccessDocumentItem(
                            itemId = successItem.collapsedUiItem.uiItem.itemId
                        )
                    )
                },
                throttleClicks = false,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                collapsedClickableAreas = listOf(
                    ClickableArea.ENTIRE_ROW,
                    ClickableArea.TRAILING_CONTENT
                )
            )
        }
    }
}

@Composable
private fun SuccessContentNoItems() {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {

        SuccessImage(
            modifier = Modifier
                .size(SIZE_100.dp)
                .align(Alignment.Center)
        )

        Banner(
            body = stringResource(R.string.pid_presentation_success_paragraph),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_MEDIUM.dp)
                .align(Alignment.BottomCenter)
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ContentScreenPreview() {
    PreviewTheme {
        ContentScreen(
            isLoading = false,
            stickyBottom = { _ ->
                WrapStickyBottomContent(
                    stickyBottomModifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SPACING_MEDIUM.dp, bottom = SPACING_LARGE.dp)
                        .padding(horizontal = SPACING_MEDIUM.dp),
                    stickyBottomConfig = StickyBottomConfig(
                        showDivider = false,
                        type = StickyBottomType.OneButton(
                            config = ButtonConfig(
                                type = ButtonType.PRIMARY,
                                enabled = true,
                                onClick = {}
                            )
                        )
                    )
                ) {
                    Text(text = stringResource(R.string.pid_presentation_success_prim_button))
                }
            },
            navigatableAction = ScreenNavigateAction.NONE,
        ) {
            Content(
                effectFlow = flowOf(),
                state = State(
                    items = emptyList(),
                    headerConfig = ContentHeaderConfig(
                        title = stringResource(R.string.pid_presentation_success_paragraph),
                    ),
                    isLoading = true
                ),
                onEventSend = {},
                onNavigationRequested = {},
                paddingValues = PaddingValues(SPACING_MEDIUM.dp),
            )
        }
    }
}