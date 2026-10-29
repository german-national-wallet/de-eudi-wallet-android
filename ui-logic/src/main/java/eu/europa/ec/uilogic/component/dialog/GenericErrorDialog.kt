@file:OptIn(ExperimentalMaterial3Api::class)

package eu.europa.ec.uilogic.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconData
import eu.europa.ec.uilogic.component.TextAndIcon
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.DEFAULT_ICON_SIZE
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
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
import org.sprind.wallet.uilogic.component.ScrollFadeColumn

data class GenericErrorDialogConfig(
    val titleRes: Int,
    val bodyTextRes: Int,
    val errorCode: String? = null,
    val traceId: String? = null,
    val dismissable: Boolean = true,
    val primaryButtonTextRes: Int? = null,
    val onPrimaryButtonClick: () -> Unit = {},
    val primaryButtonOpensExternalLink: Boolean = false,
    val secondaryButtonTextRes: Int? = null,
    val onSecondaryButtonClick: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val iconData: IconData = AppIcons.Warning,
    val bodyTextAlign: TextAlign = TextAlign.Center,
)

@Composable
fun GenericErrorDialog(
    config: GenericErrorDialogConfig,
) {
    GenericErrorDialog(
        config = config,
        title = stringResource(config.titleRes),
        bodyText = stringResource(config.bodyTextRes),
        primaryButtonText = config.primaryButtonTextRes?.let { stringResource(it) },
        secondaryButtonText = config.secondaryButtonTextRes?.let { stringResource(it) },
    )
}

@Composable
private fun GenericErrorDialog(
    config: GenericErrorDialogConfig,
    title: String,
    bodyText: String,
    primaryButtonText: String?,
    secondaryButtonText: String?,
) {
    BasicAlertDialog(
        onDismissRequest = config.onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = config.dismissable,
            dismissOnClickOutside = config.dismissable
        )
    ) {
        // Figma "Basic dialog" (WD-4274): surface container, 28dp corners, elevation 4.
        Surface(
            shape = RoundedCornerShape(DIALOG_CORNER_RADIUS.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = DIALOG_SHADOW_ELEVATION.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Long content scrolls above the pinned buttons, with a fade hinting at the rest.
                ScrollFadeColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentModifier = Modifier.padding(
                        start = SPACING_LARGE.dp,
                        top = SPACING_LARGE.dp,
                        end = SPACING_LARGE.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WrapIcon(
                        modifier = Modifier.size(DEFAULT_ICON_SIZE.dp),
                        iconData = config.iconData,
                        customTint = MaterialTheme.colorScheme.error
                    )

                    WrapText(
                        modifier = Modifier.fillMaxWidth(),
                        text = title,
                        textConfig = TextConfig(
                            maxLines = Int.MAX_VALUE,
                            style = ThemeTextStyles.dialogTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
                    ) {
                        WrapText(
                            modifier = Modifier.fillMaxWidth(),
                            text = bodyText,
                            textConfig = TextConfig(
                                maxLines = Int.MAX_VALUE,
                                style = MaterialTheme.typography.bodyLarge,
                                color = ThemeColors.onBackgroundVariant,
                                textAlign = config.bodyTextAlign,
                            )
                        )

                        // Selectable so support references can be copied (long press).
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)) {
                                config.errorCode?.let { errorCode ->
                                    SupportReference(
                                        label = stringResource(R.string.global_error_code_title),
                                        value = errorCode,
                                    )
                                }
                                config.traceId?.let { traceId ->
                                    SupportReference(
                                        label = stringResource(R.string.global_trace_id_title),
                                        value = traceId,
                                    )
                                }
                            }
                        }
                    }
                }

                if (primaryButtonText != null || secondaryButtonText != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SPACING_LARGE.dp),
                        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
                    ) {
                        primaryButtonText?.let {
                            DialogButton(
                                text = it,
                                type = ButtonType.PRIMARY,
                                onClick = config.onPrimaryButtonClick,
                                opensExternalLink = config.primaryButtonOpensExternalLink,
                            )
                        }
                        secondaryButtonText?.let {
                            DialogButton(
                                text = it,
                                type = ButtonType.SECONDARY,
                                onClick = config.onSecondaryButtonClick,
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(SPACING_LARGE.dp))
                }
            }
        }
    }
}

/** A "Label:" / value pair such as the error code or trace ID, as in the Figma sheets. */
@Composable
private fun SupportReference(label: String, value: String) {
    WrapText(
        modifier = Modifier.fillMaxWidth(),
        text = "$label:\n$value",
        textConfig = TextConfig(
            maxLines = Int.MAX_VALUE,
            style = MaterialTheme.typography.bodyMedium,
            color = ThemeColors.onBackgroundVariant,
        )
    )
}

@Composable
private fun DialogButton(
    text: String,
    type: ButtonType,
    onClick: () -> Unit,
    opensExternalLink: Boolean = false,
) {
    val color = if (type == ButtonType.PRIMARY) {
        ThemeColors.onPrimaryButton
    } else {
        ThemeColors.onSecondaryButton
    }
    val textConfig = TextConfig(
        style = if (type == ButtonType.PRIMARY) {
            ThemeTextStyles.onPrimaryButton
        } else {
            ThemeTextStyles.onSecondaryButton
        },
        color = color,
    )
    WrapButton(
        modifier = Modifier.fillMaxWidth(),
        buttonConfig = ButtonConfig(type = type, onClick = onClick),
    ) {
        if (opensExternalLink) {
            TextAndIcon(
                iconModifier = Modifier.size(SIZE_MEDIUM_LARGE.dp),
                textModifier = Modifier.padding(end = SPACING_SMALL.dp),
                textValue = text,
                textConfig = textConfig,
                rightIconData = AppIcons.Launch,
                customTint = color,
            )
        } else {
            WrapText(text = text, textConfig = textConfig)
        }
    }
}

private const val DIALOG_CORNER_RADIUS = 28
private const val DIALOG_SHADOW_ELEVATION = 6

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
                onPrimaryButtonClick = {},
                secondaryButtonTextRes = R.string.global_cancel_button,
                onSecondaryButtonClick = {},
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
                onPrimaryButtonClick = {},
                onSecondaryButtonClick = {},
            )
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun GenericErrorDialogWithoutErrorCodeAndTraceIdPreview() {
    PreviewTheme {
        GenericErrorDialog(
            config = GenericErrorDialogConfig(
                titleRes = R.string.pid_inspection_dialog_delete_pid_title,
                bodyTextRes = R.string.pid_inspection_dialog_delete_pid_paragraph,
                primaryButtonTextRes = R.string.pid_inspection_dialog_delete_pid_prim_button,
            )
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun GenericErrorDialogNoInternetPreview() {
    PreviewTheme {
        GenericErrorDialog(
            config = GenericErrorDialogConfig(
                titleRes = R.string.global_no_internet_error_title,
                bodyTextRes = R.string.global_no_internet_error_paragraph,
                primaryButtonTextRes = R.string.global_no_internet_error_prim_button,
                iconData = AppIcons.Warning,
                bodyTextAlign = TextAlign.Center,
            )
        )
    }
}

/** Large font scale so the content overflows: it scrolls and shows the bottom fade. */
@Preview(name = "Overflowing content", showBackground = true, fontScale = 2f, locale = "de")
@Composable
private fun GenericErrorDialogOverflowingPreview() {
    GenericErrorDialogWithErrorCodeAndTraceIdTextPreview()
}
