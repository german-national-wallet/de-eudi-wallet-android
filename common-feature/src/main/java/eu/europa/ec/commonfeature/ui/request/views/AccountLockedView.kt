package eu.europa.ec.commonfeature.ui.request.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapIcon

@Composable
fun AccountLockedView(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        WrapIcon(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            iconData = AppIcons.WarningHex,
            customTint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
@ThemeModeWithGermanAndEnglishPreviews
private fun Preview() {
    PreviewTheme {
        AccountLockedView(modifier = Modifier.fillMaxSize())
    }
}