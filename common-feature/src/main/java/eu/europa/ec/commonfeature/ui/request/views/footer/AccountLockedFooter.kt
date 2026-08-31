package eu.europa.ec.commonfeature.ui.request.views.footer

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
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

@Composable
fun AccountLockedFooter(
    modifier: Modifier,
    onPidDeletionButtonClicked: () -> Unit,
    onGoToDashboardButtonPressed: () -> Unit,
) {
    Column(modifier = modifier) {
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
                    .padding(horizontal = SPACING_SMALL.dp, vertical = SPACING_MEDIUM.dp)
            ) {
                WrapText(
                    text = stringResource(R.string.pid_presentation_retry_counter_blocked_paragraph),
                    textConfig = TextConfig(
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = Int.MAX_VALUE
                    )
                )
            }
        }

        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                onClick = onPidDeletionButtonClicked
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_MEDIUM.dp)
                .padding(bottom = SPACING_MEDIUM.dp),
        ) {
            WrapText(text = stringResource(R.string.pid_presentation_retry_counter_counter_overlay_sec_button),
                textConfig = TextConfig(
                    style = ThemeTextStyles.onPrimaryButton,
                    color = ThemeColors.onPrimaryButton,
                ))
        }

        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                onClick = onGoToDashboardButtonPressed
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_MEDIUM.dp)
        ) {
            WrapText(text = stringResource(R.string.pid_presentation_retry_counter_locked_prim_button),
                textConfig = TextConfig(
                    style = ThemeTextStyles.onSecondaryButton,
                    color = ThemeColors.onSecondaryButton,
                )
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun AccountLockedFooterPreview() {
    PreviewTheme {
        AccountLockedFooter(
            modifier = Modifier,
            onPidDeletionButtonClicked = {},
            onGoToDashboardButtonPressed = {}
        )
    }
}