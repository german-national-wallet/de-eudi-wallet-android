package eu.europa.ec.uilogic.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.wrap.WrapImage


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
                IconButton(onClick = action) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(
                            R.string.content_description_arrow_back_icon
                        )
                    )
                }
            }
        },
        actions = {
            onHelpClick?.let { action ->
                IconButton(onClick = action) {
                    WrapImage(
                        painter = painterResource(AppIcons.Help.resourceId ?: R.drawable.ic_help),
                        contentDescription = stringResource(AppIcons.Help.contentDescriptionId)
                    )
                }
            }
            onCloseClick?.let { action ->
                IconButton(onClick = action) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.content_description_close_icon)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
