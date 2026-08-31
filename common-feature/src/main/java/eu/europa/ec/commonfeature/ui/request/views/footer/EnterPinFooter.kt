package eu.europa.ec.commonfeature.ui.request.views.footer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

@Composable
fun EnterPinFooter(
    modifier: Modifier,
    isPinValid: Boolean,
    securityInfoClicked: () -> Unit,
    onContinuePressed: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
    ) {
        // Continue button (enabled if pin valid)
        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                enabled = isPinValid,
                onClick = onContinuePressed
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_MEDIUM.dp)

        ) {
            WrapText(
                text = stringResource(R.string.pid_presentation_wallet_pin_entry_prim_button),
                textConfig = TextConfig(
                    style = ThemeTextStyles.onPrimaryButton,
                    // TODO: The button colors should automatically adjust to disabled state so we shouldn't need this logic and the WrapText here.
                    color = if (isPinValid) ThemeColors.onPrimaryButton else ThemeColors.onDisabledPrimaryButton,
                )
            )
        }

        WrapButton(
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                onClick = securityInfoClicked
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_MEDIUM.dp)
        ) {
            WrapText(
                text = stringResource(R.string.pid_presentation_sheet_wallet_pin_entry_info_security_pin_button),
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
private fun EnterPinFooterValidPinPreview() {
    PreviewTheme {
        EnterPinFooter(
            modifier = Modifier,
            isPinValid = true,
            onContinuePressed = {},
            securityInfoClicked = {},
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun EnterPinFooterInvalidPinPreview() {
    PreviewTheme {
        EnterPinFooter(
            modifier = Modifier,
            isPinValid = false,
            onContinuePressed = {},
            securityInfoClicked = {},
        )
    }
}