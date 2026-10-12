package eu.europa.ec.uilogic.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.wrap.WrapIconButton


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancellableTopAppBar(
    onBackClick: (() -> Unit)? = null,
    onHelpClick: (() -> Unit)? = null,
    onCloseClick: (() -> Unit)? = null,
) {
    TopAppBar(
        title = {/* parameter required by the TopAppBar */ },
        navigationIcon = {
            onBackClick?.let { action ->
                WrapIconButton(iconData = AppIcons.ArrowBack, onClick = action)
            }
        },
        actions = {
            onHelpClick?.let { action ->
                WrapIconButton(iconData = AppIcons.Help, onClick = action)
            }
            onCloseClick?.let { action ->
                WrapIconButton(iconData = AppIcons.Close, onClick = action)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    )
}


@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun TopAppBarCloseButtonOnlyPreview() {
    Column {
        CancellableTopAppBar(onCloseClick = {})
        CancellableTopAppBar(onBackClick = {}, onHelpClick = {})
        CancellableTopAppBar(onCloseClick = {}, onHelpClick = {}, onBackClick = {})
    }
}
