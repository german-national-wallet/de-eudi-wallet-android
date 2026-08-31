package eu.europa.ec.commonfeature.ui.success

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.utils.SIZE_100
import eu.europa.ec.uilogic.component.wrap.WrapIcon

@Composable
fun SuccessImage(modifier: Modifier) {
    Box(
        modifier = modifier
            .background(
                color = ThemeColors.success,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        WrapIcon(
            iconData = AppIcons.Check,
            customTint = ThemeColors.onSuccess,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )
    }
}

@PreviewLightDark
@Composable
private fun SuccessImagePreview() {
    PreviewTheme {
        SuccessImage(modifier = Modifier.size(SIZE_100.dp))
    }
}