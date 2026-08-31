package eu.europa.ec.commonfeature.ui.request.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapText

@Composable
fun TemporaryPinBlockView(modifier: Modifier, remainingTime: String) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WrapImage(
            iconData = AppIcons.Blocked,
        )

        Box(
            modifier = Modifier
                .padding(top = SPACING_EXTRA_LARGE.dp)
                .fillMaxWidth()
                .background(Color.Transparent)
                .border(
                    BORDER_STROKE_1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(
                        SPACING_EXTRA_MEDIUM.dp
                    )
                )
                .padding(vertical = SPACING_MEDIUM.dp), // Adjust the vertical padding after border
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(horizontal = SPACING_MEDIUM.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WrapText(
                    modifier = Modifier.weight(2f),
                    text = stringResource(R.string.pid_presentation_retry_counter_counter_paragraph),
                    textConfig = TextConfig(
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                WrapText(
                    modifier = Modifier.weight(1f),
                    text = remainingTime,
                    textConfig = TextConfig(
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun TemporaryPinBlockPreview() {
    PreviewTheme {
        TemporaryPinBlockView(modifier = Modifier
            .fillMaxSize()
            .padding(SPACING_MEDIUM.dp), "55:55")
    }
}