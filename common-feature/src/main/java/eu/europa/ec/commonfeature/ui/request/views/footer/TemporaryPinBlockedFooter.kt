package eu.europa.ec.commonfeature.ui.request.views.footer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapText

@Composable
fun TemporaryPinBlockFooter(
    modifier: Modifier,
    isLastTry: Boolean,
    bottomSheetButtonPressed: () -> Unit,
) {
    Column(modifier = modifier) {
        if (isLastTry) {

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SPACING_MEDIUM.dp),
                shape = RoundedCornerShape(SPACING_EXTRA_MEDIUM.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SPACING_SMALL.dp,
                            vertical = SPACING_MEDIUM.dp
                        )
                ) {
                    WrapText(
                        text = stringResource(R.string.pid_presentation_retry_counter_warning_paragraph),
                        textConfig = TextConfig(
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        }

        WrapButton(
            modifier = Modifier
                .padding(
                    horizontal = SPACING_MEDIUM.dp,
                ),
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                onClick = bottomSheetButtonPressed
            )
        ) {

            TextAndIcon(
                textConfig = TextConfig(
                    style = MaterialTheme.typography.labelLarge,
                    color = ThemeColors.onSecondaryButton,
                ),
                leftIconData = IconData(
                    resourceId = R.drawable.ic_info,
                    contentDescriptionId = R.string.pid_presentation_retry_counter_counter_sec_button,
                ),
                modifier = Modifier
                    .fillMaxWidth(),
                textValue = stringResource(R.string.pid_presentation_retry_counter_counter_sec_button),
                horizontalArrangement = Arrangement.Center,
                customTint = ThemeColors.onSecondaryButton,
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun TemporaryPinBlockFooterPreview() {
    PreviewTheme {
        TemporaryPinBlockFooter(
            modifier = Modifier,
            isLastTry = false,
            bottomSheetButtonPressed = {}
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun TemporaryPinBlockLastTryFooterPreview() {
    PreviewTheme {
        TemporaryPinBlockFooter(
            modifier = Modifier,
            isLastTry = true,
            bottomSheetButtonPressed = {}
        )
    }
}

