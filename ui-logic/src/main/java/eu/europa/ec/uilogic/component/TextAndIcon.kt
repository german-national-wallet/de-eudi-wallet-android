package eu.europa.ec.uilogic.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.extension.clickableNoRipple

@Composable
fun TextAndIcon(
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    textValue: String,
    leftIconData: IconData? = null,
    rightIconData: IconData? = null,
    textConfig: TextConfig? = null,
    customTint: Color = MaterialTheme.colorScheme.onSurface,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Absolute.Center,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement
    ) {
        leftIconData?.let {
            WrapIcon(
                modifier = iconModifier.minimumInteractiveComponentSize(),
                iconData = it,
                enabled = true,
                customTint = customTint,
            )
        }
        WrapText(
            modifier = textModifier,
            text = textValue,
            textConfig = textConfig ?: TextConfig(
                style = MaterialTheme.typography.bodyLarge,
            )
        )
        rightIconData?.let {
            WrapIcon(
                modifier = iconModifier.minimumInteractiveComponentSize(),
                iconData = it,
                enabled = true,
                customTint = customTint,
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun TextAndIconPreview() {
    PreviewTheme {
        Column {
            TextAndIcon(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = SPACING_MEDIUM.dp),
                textValue = stringResource(R.string.pid_presentation_data_consent_title),
                textConfig = TextConfig(
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                ),
                leftIconData = IconData(
                    resourceId = R.drawable.ic_info,
                    contentDescriptionId = R.string.content_description_info_icon
                ),
                customTint = MaterialTheme.colorScheme.onSurface,
                horizontalArrangement = Arrangement.Absolute.Left
            )
            TextAndIcon(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = SPACING_MEDIUM.dp),
                textValue = stringResource(R.string.pid_presentation_data_consent_title),
                textConfig = TextConfig(
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                ),
                rightIconData = IconData(
                    resourceId = R.drawable.ic_info,
                    contentDescriptionId = R.string.content_description_info_icon
                ),
                customTint = MaterialTheme.colorScheme.onSurface,
                horizontalArrangement = Arrangement.Absolute.Left
            )
        }
    }
}