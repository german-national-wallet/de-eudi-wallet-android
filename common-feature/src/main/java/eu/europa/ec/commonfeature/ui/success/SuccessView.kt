package eu.europa.ec.commonfeature.ui.success

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_100
import eu.europa.ec.uilogic.component.wrap.TextConfig

@Composable
fun SuccessView(
    title: String = "",
    modifier: Modifier,
) {
    Column(
        modifier = modifier
    ) {
        ContentHeader(
            modifier = Modifier.fillMaxWidth(),
            config = ContentHeaderConfig(
                title = title,
                titleTextConfig = TextConfig(MaterialTheme.typography.titleLarge),
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            SuccessImage(
                modifier = Modifier
                    .size(SIZE_100.dp)
                    .align(Alignment.Center)
            )
        }
    }
}


@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CanSuccessPreview() {
    PreviewTheme {
        SuccessView(title = stringResource(id = R.string.nfc_scanning_success_error_success_card_pin_title_android), modifier = Modifier)
    }
}

