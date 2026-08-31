package eu.europa.ec.uilogic.component.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.dialog.GenericErrorDialogConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapText
import kotlin.uuid.Uuid

data class DrawerItemDataClass(
    val id: String,
    val label: String,
    val iconData: IconData,
    val onClick: () -> Unit,
)

@Composable
fun ContentScreenWithDrawerMenu(
    drawerTitle: String? = null,
    drawerItems: List<DrawerItemDataClass> = emptyList(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(SPACING_MEDIUM.dp)
                ) {
                    if (drawerTitle != null) {
                        item {
                            WrapText(
                                modifier =
                                    Modifier
                                        .padding(vertical = SPACING_LARGE.dp)
                                        .padding(horizontal = SPACING_MEDIUM.dp),
                                text = drawerTitle,
                                textConfig =
                                    TextConfig(
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                            )
                            HorizontalDivider()
                        }
                    }
                    items(items = drawerItems) {
                        NavigationDrawerItem(
                            label = {
                                WrapText(
                                    text = it.label,
                                    textConfig =
                                        TextConfig(
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = Int.MAX_VALUE,
                                        ),
                                )
                            },
                            selected = false,
                            icon = { WrapIcon(iconData = it.iconData) },
                            onClick = it.onClick,
                        )
                    }
                }
            }
        },
        drawerState = drawerState,
    ) {
        content()
    }
}

@Preview
@Composable
fun ContentWithDrawerMenuPreview() {
    PreviewTheme {
        ContentScreenWithDrawerMenu(
            drawerTitle = "Drawer Title",
            drawerItems =
                listOf(
                    DrawerItemDataClass(
                        id = "settings",
                        label = "Settings",
                        iconData = AppIcons.Visibility,
                        onClick = {},
                    ),
                    DrawerItemDataClass(
                        id = "log_export",
                        label = "Log export",
                        iconData = AppIcons.VisibilityOff,
                        onClick = {},
                    ),
                ),
            drawerState = rememberDrawerState(DrawerValue.Open),
        ) {
            WrapImage(
                modifier = Modifier.padding(start = SPACING_MEDIUM.dp),
                iconData = AppIcons.PhoneVector,
            )
        }
    }
}

@Preview
@Composable
fun ContentWithCollapsedDrawerMenuPreview() {
    PreviewTheme {
        ContentScreenWithDrawerMenu(
            drawerTitle = "Drawer Title",
            drawerItems =
                listOf(
                    DrawerItemDataClass(
                        id = "settings",
                        label = "Settings",
                        iconData = AppIcons.Visibility,
                        onClick = {},
                    ),
                    DrawerItemDataClass(
                        id = "log_export",
                        label = "Log export",
                        iconData = AppIcons.VisibilityOff,
                        onClick = {},
                    ),
                ),
            drawerState = rememberDrawerState(DrawerValue.Closed),
        ) {
            WrapImage(
                modifier = Modifier.padding(start = SPACING_MEDIUM.dp),
                iconData = AppIcons.PhoneVector,
            )
        }
    }
}
