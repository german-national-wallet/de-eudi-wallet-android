/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.uilogic.component.wrap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BUTTON_HEIGHT
import eu.europa.ec.uilogic.component.utils.SIZE_100
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE

enum class ButtonType {
    PRIMARY,
    SECONDARY,
    TONAL,
    TEXT
}

private val buttonsShape: RoundedCornerShape = RoundedCornerShape(SIZE_100.dp)

private val buttonsContentPadding: PaddingValues = PaddingValues(
    vertical = 0.dp,
    horizontal = SPACING_LARGE.dp
)

data class ButtonConfig(
    val type: ButtonType,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val isWarning: Boolean = false,
    val shape: Shape = buttonsShape,
    val contentPadding: PaddingValues = buttonsContentPadding,
    val isWithoutContainerBackground: Boolean = false,
)

@Composable
fun WrapButton(
    modifier: Modifier = Modifier,
    buttonConfig: ButtonConfig,
    content: @Composable RowScope.() -> Unit,
) {
    val modifierWithHeight = modifier.height(BUTTON_HEIGHT.dp)

    val centerContent: @Composable RowScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(this)
        }
    }


    when (buttonConfig.type) {
        ButtonType.PRIMARY -> WrapPrimaryButton(
            modifier = modifierWithHeight,
            buttonConfig = buttonConfig,
            content = centerContent,
        )

        ButtonType.SECONDARY -> WrapSecondaryButton(
            modifier = modifierWithHeight,
            buttonConfig = buttonConfig,
            content = centerContent,
        )

        ButtonType.TONAL -> WrapTonalButton(
            modifier = modifierWithHeight,
            buttonConfig = buttonConfig,
            content = centerContent,
        )

        ButtonType.TEXT -> WrapTextButton(
            modifier = modifierWithHeight,
            buttonConfig = buttonConfig,
            content = centerContent,
        )
    }
}

@Composable
private fun WrapPrimaryButton(
    modifier: Modifier = Modifier,
    buttonConfig: ButtonConfig,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = if (buttonConfig.isWarning) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        )
    } else if (buttonConfig.isWithoutContainerBackground) {
        ButtonDefaults.filledTonalButtonColors(containerColor = Color.Transparent)
    } else {
        ThemeColors.primaryButtonColors
    }

    Button(
        modifier = modifier,
        enabled = buttonConfig.enabled,
        onClick = buttonConfig.onClick,
        shape = buttonsShape,
        colors = colors,
        border = BorderStroke(
            width = 1.dp,
            color = ThemeColors.primaryButtonOutline,
        ),
        contentPadding = buttonConfig.contentPadding,
        content = content
    )
}

@Composable
private fun WrapSecondaryButton(
    modifier: Modifier = Modifier,
    buttonConfig: ButtonConfig,
    content: @Composable RowScope.() -> Unit,
) {
    val borderColor = if (!buttonConfig.enabled) {
        MaterialTheme.colorScheme.onSurface.copy(
            alpha = 0.12f
        )
    } else {
        if (buttonConfig.isWarning) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.outline
        }
    }

    // The warning variant is the design system's destructive answer: an error-container fill behind
    // the error outline, so it reads as the dangerous option even before its label is read.
    val colors = if (buttonConfig.isWarning) {
        ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        ThemeColors.secondaryButtonColors
    }

    OutlinedButton(
        modifier = modifier,
        enabled = buttonConfig.enabled,
        onClick = buttonConfig.onClick,
        shape = buttonsShape,
        colors = colors,
        border = BorderStroke(
            width = 1.dp,
            color = borderColor,
        ),
        contentPadding = PaddingValues(0.dp),
        content = content
    )
}

@Composable
private fun WrapTextButton(
    modifier: Modifier = Modifier,
    buttonConfig: ButtonConfig,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.TextButton(
        modifier = modifier,
        enabled = buttonConfig.enabled,
        onClick = buttonConfig.onClick,
        shape = buttonConfig.shape,
        contentPadding = buttonConfig.contentPadding,
        colors = if (buttonConfig.isWarning) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.textButtonColors()
        },
        content =  content
    )
}


@Composable
private fun WrapTonalButton(
    modifier: Modifier = Modifier,
    buttonConfig: ButtonConfig,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = if (buttonConfig.isWarning) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        )
    } else if (buttonConfig.isWithoutContainerBackground) {
        ButtonDefaults.filledTonalButtonColors(containerColor = Color.Transparent)
    } else {
        ButtonDefaults.buttonColors()
    }

    FilledTonalButton(
        modifier = modifier,
        enabled = buttonConfig.enabled,
        onClick = buttonConfig.onClick,
        shape = buttonConfig.shape,
        colors = colors,
        contentPadding = buttonConfig.contentPadding,
        content = content
    )
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapPrimaryButtonEnabledPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                enabled = true,
                onClick = { }
            ),
        ) {
            Text("Enabled Primary Button")
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapPrimaryButtonDisabledPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                enabled = false,
                onClick = { }
            ),
        ) {
            Text("Disabled Primary Button")
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapPrimaryButtonEnabledWarningPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                enabled = true,
                isWarning = true,
                onClick = { }
            )
        ) {
            Text("Enabled Warning Primary Button")
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapPrimaryButtonDisabledWarningPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                enabled = false,
                isWarning = true,
                onClick = { }
            )
        ) {
            Text("Disabled Warning Primary Button")
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapSecondaryButtonEnabledPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                enabled = true,
                onClick = { }
            )
        ) {
            Text("Enabled Secondary Button")
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapSecondaryButtonDisabledPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                enabled = false,
                onClick = { }
            )
        ) {
            Text("Disabled Secondary Button")
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapSecondaryButtonEnabledWarningPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                enabled = true,
                isWarning = true,
                onClick = { }
            )
        ) {
            Text("Enabled Warning Secondary Button")
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapSecondaryButtonDisabledWarningPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                enabled = false,
                isWarning = true,
                onClick = { }
            )
        ) {
            Text("Disabled Warning Secondary Button")
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapTonalButtonDisabledWarningPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.TONAL,
                enabled = false,
                isWarning = false,
                onClick = { }
            )
        ) {
            Text("Disabled Tonal Button")
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapTonalButtonEnabledWarningPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.TONAL,
                enabled = true,
                isWarning = false,
                onClick = { }
            )
        ) {
            Text("Enabled Tonal Button")
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun WrapTextButtonPreview() {
    PreviewTheme {
        WrapButton(
            buttonConfig = ButtonConfig(
                contentPadding = PaddingValues(0.dp),
                type = ButtonType.TEXT,
                enabled = true,
                onClick = { }
            )
        ) {
            Text("Text Button")
        }
    }
}
