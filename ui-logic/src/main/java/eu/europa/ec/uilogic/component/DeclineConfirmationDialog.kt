package eu.europa.ec.uilogic.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.ELEVATION_8
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeclineConfirmationDialog(
    headLineText: String,
    contentText: String,
    cancellationText: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dismissOnBackPress: Boolean = false,
    dismissOnClickOutside: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    iconData: IconData? = null,
    onDismissRequest: () -> Unit = {} // if left empty we force the dialog not to do anything on dismiss
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside
        )
    ) {
        Surface(
            shape = RoundedCornerShape(SPACING_MEDIUM.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = ELEVATION_8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(SPACING_MEDIUM.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                WrapText(
                    text = headLineText,
                    textConfig = TextConfig(
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                WrapText(
                    text = contentText,
                    textConfig = TextConfig(
                        maxLines = maxLines,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp, Alignment.End)
                ) {
                    WrapButton(
                        buttonConfig = ButtonConfig(
                            contentPadding = PaddingValues(end = SPACING_SMALL.dp),
                            type = ButtonType.TEXT,
                            onClick = onDismiss
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        WrapText(
                            modifier = Modifier.fillMaxWidth(),
                            text = cancellationText,
                            textConfig = TextConfig(
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    WrapButton(
                        buttonConfig = ButtonConfig(
                            type = ButtonType.PRIMARY,
                            onClick = onConfirm
                        ),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        WrapText(
                            modifier = Modifier,
                            text = confirmText,
                            textConfig = TextConfig(
                                style = ThemeTextStyles.onPrimaryButton,
                                color = ThemeColors.onPrimaryButton
                            )
                        )
                        iconData?.let {
                            WrapIcon(
                                modifier = Modifier
                                    .size(SPACING_LARGE.dp)
                                    .padding(start = SPACING_EXTRA_SMALL.dp),
                                iconData = AppIcons.OpenNew
                            )
                        }
                    }
                }
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PreviewModal() {
    PreviewTheme {
        DeclineConfirmationDialog(
            headLineText = stringResource(R.string.pid_presentation_dialog_rp_rejection_title),
            contentText = stringResource(R.string.pid_presentation_dialog_rp_rejection_paragraph),
            cancellationText = stringResource(R.string.pid_presentation_dialog_rp_rejection_sec_button),
            confirmText = stringResource(R.string.pid_presentation_dialog_rp_rejection_prim_button),
            {}, {})
    }
}