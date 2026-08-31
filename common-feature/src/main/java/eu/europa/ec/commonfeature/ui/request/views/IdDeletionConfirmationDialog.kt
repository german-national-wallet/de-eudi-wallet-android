@file:OptIn(ExperimentalMaterial3Api::class)

package eu.europa.ec.commonfeature.ui.request.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.ELEVATION_8
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

@Composable
fun IdDeletionConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
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
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WrapIcon(
                    iconData = AppIcons.InfoFilled,
                    customTint = MaterialTheme.colorScheme.secondary
                )

                WrapText(
                    text = stringResource(R.string.pid_presentation_retry_counter_reset_overlay_title),
                    textConfig = TextConfig(
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                )

                WrapText(
                    text = stringResource(R.string.pid_presentation_retry_counter_reset_overlay_paragraph),
                    textConfig = TextConfig(
                        maxLines = Int.MAX_VALUE,
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
                            text = stringResource(R.string.global_back_button),
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
                            onClick = onConfirm,
                            isWarning = true
                        ),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        WrapText(
                            modifier = Modifier,
                            text = stringResource(R.string.pid_inspection_dialog_delete_pid_prim_button),
                            textConfig = TextConfig(
                                style = ThemeTextStyles.onPrimaryButton,
                                color = ThemeColors.onPrimaryButton
                            )
                        )
                    }
                }
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun IdDeletionConfirmationDialogPreview() {
    PreviewTheme {
        IdDeletionConfirmationDialog(
            onDismiss = {},
            onConfirm = {}
        )
    }
}