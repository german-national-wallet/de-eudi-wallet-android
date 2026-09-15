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

package org.sprind.wallet.uilogic.component

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDeepLink
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.DEFAULT_ICON_SIZE
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.Screen
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

/**
 * The wallet's primary destinations, in the order they appear in the [BottomNavigationBar].
 *
 * @property screen the destination the tab navigates to.
 * @property icon the tab's icon.
 * @property labelRes the tab's visible label.
 */
enum class BottomNavigationTab(
    val screen: Screen,
    val icon: IconData,
    @StringRes val labelRes: Int,
) {
    OVERVIEW(
        screen = DashboardScreens.Dashboard,
        icon = AppIcons.Overview,
        labelRes = R.string.tap_navigation_label_1,
    ),
    ACTIVITIES(
        screen = DashboardScreens.Activities,
        icon = AppIcons.Activities,
        labelRes = R.string.tap_navigation_label_2,
    ),
    SETTINGS(
        screen = DashboardScreens.Settings,
        icon = AppIcons.Settings,
        labelRes = R.string.tap_navigation_label_3,
    ),
}

/**
 * The bottom navigation bar carried by the wallet's primary destinations. Detail screens leave it
 * out, so the user leaves them through the back button rather than by switching tabs.
 *
 * @param selectedTab the tab of the screen showing the bar.
 * @param onTabSelected called with the tapped tab, including the already selected one.
 * @param modifier applied to the bar.
 */
@Composable
fun BottomNavigationBar(
    selectedTab: BottomNavigationTab,
    onTabSelected: (BottomNavigationTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(
        topStart = BOTTOM_NAVIGATION_CORNER_RADIUS,
        topEnd = BOTTOM_NAVIGATION_CORNER_RADIUS,
    )
    val shadow = Shadow(
        radius = BOTTOM_NAVIGATION_SHADOW_BLUR,
        color = MaterialTheme.colorScheme.onBackground,
        offset = DpOffset(x = 0.dp, y = BOTTOM_NAVIGATION_SHADOW_OFFSET_Y),
        alpha = BOTTOM_NAVIGATION_SHADOW_ALPHA,
    )

    val contentColor = MaterialTheme.colorScheme.onBackground
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = contentColor,
        selectedTextColor = contentColor,
        indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unselectedIconColor = contentColor,
        unselectedTextColor = contentColor,
    )

    NavigationBar(
        modifier = modifier
            .dropShadow(shape = shape, shadow = shadow)
            .clip(shape),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        BottomNavigationTab.entries.forEach { tab ->
            val selected = tab == selectedTab

            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    WrapIcon(
                        // The label below already names the tab, and NavigationBarItem merges the
                        // item's semantics, so the icon's own description would only repeat it.
                        modifier = Modifier
                            .size(DEFAULT_ICON_SIZE.dp)
                            .clearAndSetSemantics { },
                        iconData = tab.icon,
                    )
                },
                label = {
                    Text(
                        text = stringResource(id = tab.labelRes),
                        style = if (selected) {
                            LocalTextStyle.current
                                .merge(ThemeTextStyles.bottomNavigationLabelSelected)
                        } else {
                            LocalTextStyle.current
                        },
                        // Material centres the label slot but not the lines inside it, so a
                        // wrapped label would otherwise hang off to the start of the tab.
                        textAlign = TextAlign.Center,
                        // WCAG 1.4.4 asks that text scale to 200% without losing content, and at
                        // that scale the longest German label needs 1.7 slots of a 360dp screen.
                        // So it wraps and the bar grows; nothing wraps at the default scale.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = itemColors,
            )
        }
    }
}

/**
 * Adds a destination that carries the [BottomNavigationBar], switching between two tabs with no
 * transition.
 *
 * The bar belongs to each tab's own content, so animating between tabs animates the bar too: a
 * cross-fade makes it blink, and fading only the top screen puts the animation between the tap and
 * the tab. Moving anywhere that is not a tab returns `null`, leaving the host's own transition in
 * place.
 */
fun NavGraphBuilder.bottomNavigationTabDestination(
    route: String,
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        deepLinks = deepLinks,
        enterTransition = {
            EnterTransition.None.takeIf { initialState.destination.isBottomNavigationTab() }
        },
        exitTransition = {
            ExitTransition.None.takeIf { targetState.destination.isBottomNavigationTab() }
        },
        content = content,
    )
}

private fun NavDestination?.isBottomNavigationTab(): Boolean =
    BottomNavigationTab.entries.any { tab -> tab.screen.screenRoute == this?.route }

/**
 * Switches to [tab]'s destination.
 *
 * The back stack stays flat: everything above the overview is popped, so a tab never stacks on
 * another tab and the back button always leads from a tab back to the overview.
 */
fun NavController.navigateToBottomNavigationTab(tab: BottomNavigationTab) {
    val route = tab.screen.screenRoute
    if (currentDestination?.route == route) return

    navigate(route) {
        popUpTo(DashboardScreens.Dashboard.screenRoute) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

private val BOTTOM_NAVIGATION_CORNER_RADIUS = 36.dp

private val BOTTOM_NAVIGATION_SHADOW_BLUR = 16.dp
private val BOTTOM_NAVIGATION_SHADOW_OFFSET_Y = 2.dp
private const val BOTTOM_NAVIGATION_SHADOW_ALPHA = 0.2f

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun BottomNavigationBarOverviewSelectedPreview() {
    PreviewTheme {
        BottomNavigationBar(
            selectedTab = BottomNavigationTab.OVERVIEW,
            onTabSelected = {},
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun BottomNavigationBarSettingsSelectedPreview() {
    PreviewTheme {
        BottomNavigationBar(
            selectedTab = BottomNavigationTab.SETTINGS,
            onTabSelected = {},
        )
    }
}
