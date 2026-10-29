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

@file:OptIn(ExperimentalMaterial3Api::class)

package org.sprind.wallet.dashboardfeature.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityAvatarUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivityListItemUi
import org.sprind.wallet.dashboardfeature.ui.activities.model.ActivitySectionUi
import org.sprind.wallet.uilogic.component.BottomNavigationBar
import org.sprind.wallet.uilogic.component.BottomNavigationTab
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.NoticeBanner
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialog
import org.sprind.wallet.uilogic.component.dialog.ConfirmationDialogConfig
import org.sprind.wallet.uilogic.component.navigateToBottomNavigationTab

/** The activities tab, or one credential's activities when opened from its detail screen. */
@Composable
fun ActivitiesScreen(
    navHostController: NavController,
    viewModel: ActivitiesViewModel,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    MainScreen(
        state = state,
        onEventSend = viewModel::setEvent,
        onBottomNavigationTabSelected = navHostController::navigateToBottomNavigationTab,
    )

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ActivitiesEffect.OpenActivity -> navHostController.navigate(effect.route)
                is ActivitiesEffect.OpenWebPage -> navHostController.navigate(effect.route)
                ActivitiesEffect.Pop -> navHostController.popBackStack()
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: ActivitiesState,
    onEventSend: (ActivitiesEvent) -> Unit,
    onBottomNavigationTabSelected: (BottomNavigationTab) -> Unit,
) {
    val isTab = !state.isCredentialActivities
    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = if (isTab) ScreenNavigateAction.NONE else ScreenNavigateAction.BACKABLE,
        // A tab is never stacked on another tab, so back always leads to the overview.
        onBack = {
            if (isTab) {
                onBottomNavigationTabSelected(BottomNavigationTab.OVERVIEW)
            } else {
                onEventSend(ActivitiesEvent.BackPressed)
            }
        },
        topBar = {
            ActivitiesTopBar(
                showBack = !isTab,
                showMenu = isTab,
                menuEnabled = state.sections.isNotEmpty(),
                onEventSend = onEventSend,
            )
        },
        bottomBar = if (isTab) {
            {
                BottomNavigationBar(
                    selectedTab = BottomNavigationTab.ACTIVITIES,
                    onTabSelected = onBottomNavigationTabSelected,
                )
            }
        } else {
            null
        },
    ) { paddingValues ->
        ContentTemplateBody(
            modifier = Modifier.padding(paddingValues),
            templateConfig = ContentTemplateConfig(
                verticalSpacing = if (state.sections.isEmpty()) SPACING_LARGE.dp else SPACING_MEDIUM.dp,
            ),
            title = {
                Text(
                    text = stringResource(
                        if (isTab) R.string.activities_overview_title else R.string.eaa_inspection_eaa_activity_title
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            extraContent = {
                if (state.sections.isEmpty() && !state.isLoading) {
                    EmptyActivities(onHowToUse = { onEventSend(ActivitiesEvent.HowToUseClicked) })
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp)) {
                        state.sections.forEach { section ->
                            ActivitySection(
                                section = section,
                                onActivityClick = { onEventSend(ActivitiesEvent.ActivityClicked(it)) },
                            )
                        }
                    }
                }
            },
        )
    }

    if (state.showDeleteAllDialog) {
        ConfirmationDialog(
            config = ConfirmationDialogConfig(
                title = stringResource(R.string.activities_panel_delete_entries_dialogue),
                confirmText = stringResource(R.string.eaa_inspection_dialog_delete_eaa_prim_button),
                dismissText = stringResource(R.string.eaa_inspection_dialog_delete_eaa_sec_button),
                onConfirm = { onEventSend(ActivitiesEvent.DeleteAllConfirmed) },
                onDismiss = { onEventSend(ActivitiesEvent.DeleteAllDismissed) },
                icon = AppIcons.Warning,
                iconTint = MaterialTheme.colorScheme.error,
            )
        )
    }
}

@Composable
private fun ActivitiesTopBar(
    showBack: Boolean,
    showMenu: Boolean,
    menuEnabled: Boolean,
    onEventSend: (ActivitiesEvent) -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        title = {},
        navigationIcon = {
            if (showBack) {
                WrapIconButton(
                    iconData = AppIcons.ArrowBack,
                    customTint = MaterialTheme.colorScheme.onSurface,
                    onClick = { onEventSend(ActivitiesEvent.BackPressed) },
                )
            }
        },
        actions = {
            Row(
                modifier = Modifier.padding(end = ACTIONS_END_PADDING),
                horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
            ) {
                WrapIconButton(
                    iconData = AppIcons.Help,
                    customTint = MaterialTheme.colorScheme.onSurface,
                    onClick = { onEventSend(ActivitiesEvent.HowToUseClicked) },
                )
                if (showMenu) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        WrapIconButton(
                            iconData = AppIcons.VerticalMore,
                            customTint = MaterialTheme.colorScheme.onSurface,
                            enabled = menuEnabled,
                            throttleClicks = false,
                            onClick = { expanded = true },
                        )
                        if (expanded) {
                            DeleteAllMenu(
                                onDismiss = { expanded = false },
                                onDeleteAll = {
                                    expanded = false
                                    onEventSend(ActivitiesEvent.DeleteAllClicked)
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** The design's overflow card; a plain popup so the ripple fills the whole rounded card. */
@Composable
private fun DeleteAllMenu(
    onDismiss: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        val offsetX = with(density) { MENU_OFFSET_X.roundToPx() }
        val shadowSpace = with(density) { MENU_SHADOW_SPACE.roundToPx() }
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset(
                x = anchorBounds.right + offsetX - popupContentSize.width + shadowSpace,
                y = anchorBounds.bottom - shadowSpace,
            )
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            onClick = onDeleteAll,
            modifier = Modifier
                .padding(MENU_SHADOW_SPACE)
                .width(MENU_WIDTH),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = MENU_SHADOW_ELEVATION,
        ) {
            Box(
                modifier = Modifier
                    .heightIn(min = MENU_HEIGHT)
                    .padding(horizontal = SPACING_LARGE.dp, vertical = SPACING_SMALL.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.activities_panel_delete_entries_button_2),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EmptyActivities(onHowToUse: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp)) {
        NoticeBanner(
            title = stringResource(R.string.activities_overview_banner_headline),
            body = stringResource(R.string.activities_overview_banner_paragraph),
        )
        ExternalLinkButton(
            text = stringResource(R.string.activities_overview_sec_button),
            onClick = onHowToUse,
        )
    }
}

@Composable
private fun ActivitySection(
    section: ActivitySectionUi,
    onActivityClick: (String) -> Unit,
) {
    Column {
        Text(
            modifier = Modifier.padding(bottom = SPACING_SMALL.dp),
            text = section.header,
            style = MaterialTheme.typography.headlineSmall,
            color = ThemeColors.onBackgroundVariant,
        )
        section.items.forEach { item ->
            ActivityRow(item = item, onClick = { onActivityClick(item.id) })
        }
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun ActivitiesScreenPreview() {
    PreviewTheme {
        MainScreen(
            state = ActivitiesState(
                isLoading = false,
                sections = listOf(
                    ActivitySectionUi(
                        header = "Today",
                        items = listOf(
                            ActivityListItemUi("1", "Rewe", "Digital ID · 5:30 PM", ActivityAvatarUi.Initials("RE")),
                            ActivityListItemUi("2", "DeineBank.de", "Multiple credentials · 12:01 PM", ActivityAvatarUi.Initials("DE")),
                            ActivityListItemUi("5", "EUDI Wallet Playground", "Not completed · 7:54 AM", ActivityAvatarUi.Initials("EU"), notCompleted = true),
                        ),
                    ),
                    ActivitySectionUi(
                        header = "Last Week",
                        items = listOf(
                            ActivityListItemUi("3", "Sozial Pass created", "Stadt Köln · 02.05.2026", ActivityAvatarUi.Initials("SO", "#FF5722")),
                            ActivityListItemUi("4", "Digital ID created", "Federal Republic of Germany · 01.05.2026", ActivityAvatarUi.Initials("DI")),
                        ),
                    ),
                ),
            ),
            onEventSend = {},
            onBottomNavigationTabSelected = {},
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun ActivitiesScreenEmptyPreview() {
    PreviewTheme {
        MainScreen(
            state = ActivitiesState(isLoading = false),
            onEventSend = {},
            onBottomNavigationTabSelected = {},
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun CredentialActivitiesScreenPreview() {
    PreviewTheme {
        MainScreen(
            state = ActivitiesState(isLoading = false, isCredentialActivities = true),
            onEventSend = {},
            onBottomNavigationTabSelected = {},
        )
    }
}

private val ACTIONS_END_PADDING = 6.dp
private val MENU_WIDTH = 258.dp
private val MENU_OFFSET_X = (-16).dp
private val MENU_SHADOW_ELEVATION = 3.dp
private val MENU_SHADOW_SPACE = 8.dp
private val MENU_HEIGHT = 64.dp
