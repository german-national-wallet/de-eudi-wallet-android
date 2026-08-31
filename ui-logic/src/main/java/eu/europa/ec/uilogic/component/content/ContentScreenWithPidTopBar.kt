@file:OptIn(ExperimentalMaterial3Api::class)

package eu.europa.ec.uilogic.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import org.sprind.wallet.designsystem.typography.CustomTypography
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.NAVIGATION_ICON_BOX_SIZE
import eu.europa.ec.uilogic.component.utils.PID_DOCUMENT_TOP_BAR_HEIGHT
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.WrapAsyncImage

data class ContentPidTopBarConfig(
    val title: String,
    val onBackIconPress: () -> Unit,
    val backgroundColor: Color = ThemeColors.primaryPid,
    val backgroundImageUri: String? = null,
    val textColor: Color = ThemeColors.onPrimaryPid,
)

@Composable
fun ContentScreenWithPidTopBar(
    isLoading: Boolean = false,
    toolBarConfig: ToolbarConfig? = null,
    navigatableAction: ScreenNavigateAction = ScreenNavigateAction.BACKABLE,
    onBack: (() -> Unit)? = null,
    contentPidTopBarConfig: ContentPidTopBarConfig,
    bottomBar: @Composable (() -> Unit)? = null,
    stickyBottom: @Composable ((PaddingValues) -> Unit)? = null,
    fab: @Composable () -> Unit = {},
    fabPosition: FabPosition = FabPosition.End,
    snackbarHost: @Composable () -> Unit = {},
    contentErrorConfig: ContentErrorConfig? = null,
    bodyContent: @Composable (PaddingValues) -> Unit,
) {
    ContentScreen(
        isLoading = isLoading,
        toolBarConfig = toolBarConfig,
        navigatableAction = navigatableAction,
        onBack = onBack,
        contentErrorConfig = contentErrorConfig,
        fab = fab,
        fabPosition = fabPosition,
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
        topBar = {
            Box(modifier = Modifier.height(PID_DOCUMENT_TOP_BAR_HEIGHT.dp)) {
                LargeTopAppBar(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxSize(),
                    title = { /* for this app bar the title is handle separately because of the expanded height */ },
                    colors = TopAppBarDefaults.topAppBarColors()
                        .copy(containerColor = contentPidTopBarConfig.backgroundColor),
                    navigationIcon = { }
                )
                contentPidTopBarConfig.backgroundImageUri?.let { uri ->
                    WrapAsyncImage(
                        source = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding(),
                ) {
                    AppIcons.ArrowBack.imageVector?.let { imageV ->
                        Box(
                            modifier = Modifier
                                .size(NAVIGATION_ICON_BOX_SIZE.dp)
                                .clip(CircleShape)
                                .clickable { contentPidTopBarConfig.onBackIconPress() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                modifier = Modifier,
                                imageVector = imageV,
                                tint = contentPidTopBarConfig.textColor,
                                contentDescription = stringResource(id = AppIcons.Menu.contentDescriptionId)
                            )
                        }
                    }
                }
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = SPACING_MEDIUM.dp,
                            end = SPACING_MEDIUM.dp,
                            bottom = SPACING_LARGE.dp
                        ),
                    text = contentPidTopBarConfig.title,
                    style = CustomTypography.titleLargeBold.copy(color = contentPidTopBarConfig.textColor)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = BORDER_STROKE_1.dp
                )
                Icon(
                    modifier = Modifier.align(Alignment.TopEnd),
                    painter = painterResource(id = R.drawable.ic_eagle_right),
                    contentDescription = "Eagle",
                    tint = contentPidTopBarConfig.textColor.copy(alpha = 0.15f)
                )
            }
        },
        stickyBottom = stickyBottom,
        bodyContent = bodyContent,
    )
}