@file:OptIn(ExperimentalMaterial3Api::class)

package eu.europa.ec.uilogic.component.dialog

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
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
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapText
import kotlinx.coroutines.launch
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

data class GenericErrorDialogConfig(
    val titleRes: Int,
    val bodyTextRes: Int,
    val errorCode: String? = null,
    val traceId: String? = null,
    val dismissable: Boolean = true,
    val primaryButtonTextRes: Int? = null,
    val onPrimaryButtonClick: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

@Composable
fun GenericErrorDialog(
    config: GenericErrorDialogConfig,
) {
    GenericErrorDialog(
        title = stringResource(config.titleRes),
        bodyText = stringResource(config.bodyTextRes),
        errorCode = config.errorCode,
        traceId = config.traceId,
        primaryButtonText = config.primaryButtonTextRes?.let { stringResource(it) },
        dismissable = config.dismissable,
        onDismiss = config.onDismiss,
        onPrimaryButtonClick = config.onPrimaryButtonClick
    )
}

@Composable
private fun GenericErrorDialog(
    title: String,
    bodyText: String,
    errorCode: String?,
    traceId: String?,
    primaryButtonText: String?,
    dismissable: Boolean,
    onDismiss: () -> Unit,
    onPrimaryButtonClick: () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable
        )
    ) {
        Surface(
            shape = RoundedCornerShape(SPACING_MEDIUM.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = ELEVATION_8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(SPACING_LARGE.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                WrapIcon(
                    iconData = AppIcons.InfoFilled,
                    customTint = MaterialTheme.colorScheme.error
                )

                WrapText(
                    text = title,
                    textConfig = TextConfig(
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                )

                WrapText(
                    text = bodyText,
                    textConfig = TextConfig(
                        maxLines = Int.MAX_VALUE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                errorCode?.let {
                    Column {
                        WrapText(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.global_error_code_title),
                            textConfig = TextConfig(
                                maxLines = Int.MAX_VALUE,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        WrapText(
                            modifier = Modifier.fillMaxWidth(),
                            text = errorCode,
                            textConfig = TextConfig(
                                maxLines = Int.MAX_VALUE,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                traceId?.let {
                    val clipboard: Clipboard = LocalClipboard.current
                    val scope = rememberCoroutineScope()
                    val title = stringResource(R.string.global_trace_id_title)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            WrapText(
                                text = title,
                                textConfig = TextConfig(
                                    maxLines = Int.MAX_VALUE,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            WrapText(
                                text = traceId,
                                textConfig = TextConfig(
                                    maxLines = Int.MAX_VALUE,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        Box(
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {

                            WrapImage(
                                iconData = AppIcons.Copy,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipData.newPlainText(
                                                title,
                                                traceId
                                            ).toClipEntry()
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                primaryButtonText?.let {
                    WrapButton(
                        modifier = Modifier.fillMaxWidth(),
                        buttonConfig = ButtonConfig(
                            type = ButtonType.PRIMARY,
                            onClick = onPrimaryButtonClick,
                        ),
                    ) {
                        WrapText(
                            modifier = Modifier,
                            text = primaryButtonText,
                            textConfig = TextConfig(
                                style = ThemeTextStyles.onPrimaryButton,
                                color = ThemeColors.onPrimaryButton,
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
private fun GenericErrorDialogWithErrorCodeAndTraceIdTextPreview() {
    PreviewTheme {
        GenericErrorDialog(
            config = GenericErrorDialogConfig(
                titleRes = R.string.pid_inspection_dialog_delete_pid_title,
                bodyTextRes = R.string.pid_inspection_dialog_delete_pid_paragraph,
                errorCode = "WB_ATTESTATION_VERIFICATION_FAILED",
                traceId = "34iu234082034u02934j23o420394j32",
                primaryButtonTextRes = R.string.pid_inspection_dialog_delete_pid_prim_button,
                onDismiss = {},
                onPrimaryButtonClick = {}
            )
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun GenericErrorDialogWithoutPrimaryButtonPreview() {
    PreviewTheme {
        GenericErrorDialog(
            config = GenericErrorDialogConfig(
                titleRes = R.string.app_onboarding_wb_internal_error_title,
                bodyTextRes = R.string.app_onboarding_wb_internal_error_paragraph,
                errorCode = "WB_ATTESTATION_VERIFICATION_FAILED",
                traceId = "34iu234082034u02934j23o420394j32",
                onDismiss = {},
                onPrimaryButtonClick = {}
            )
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun GenericErrorDialogWithoutErrorCodeAndTraceIdPreview() {
    PreviewTheme {
        GenericErrorDialog(
            title = stringResource(R.string.pid_inspection_dialog_delete_pid_title),
            bodyText = stringResource(R.string.pid_inspection_dialog_delete_pid_paragraph),
            errorCode = null,
            traceId = null,
            dismissable = true,
            primaryButtonText = stringResource(R.string.pid_inspection_dialog_delete_pid_prim_button),
            onDismiss = {},
            onPrimaryButtonClick = {}
        )
    }
}