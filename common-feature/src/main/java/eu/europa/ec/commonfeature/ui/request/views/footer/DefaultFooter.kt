package eu.europa.ec.commonfeature.ui.request.views.footer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.WEIGHT_1
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapText
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles

@Composable
fun DefaultFooter(
    modifier: Modifier,
    onCancelPressed: () -> Unit,
    onContinuePressed: () -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_MEDIUM.dp),
            horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WrapButton(
                buttonConfig = ButtonConfig(
                    type = ButtonType.SECONDARY,
                    enabled = true,
                    onClick = onCancelPressed
                ),
                modifier = Modifier.weight(WEIGHT_1)
            ) {
                WrapText(
                    text = stringResource(R.string.pid_presentation_rp_info_sec_button),
                    textConfig = TextConfig(
                        style = ThemeTextStyles.onSecondaryButton,
                        color = ThemeColors.onSecondaryButton,
                    )
                )
            }

            WrapButton(
                buttonConfig = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    enabled = true,
                    onClick = onContinuePressed
                ),
                modifier = Modifier.weight(WEIGHT_1)
            ) {
                WrapText(
                    text = stringResource(R.string.pid_presentation_rp_info_prim_button),
                    textConfig = TextConfig(
                        style = ThemeTextStyles.onPrimaryButton,
                        color = ThemeColors.onPrimaryButton,
                    )
                )
                WrapIcon(
                    modifier = Modifier.padding(start = SPACING_SMALL.dp),
                    iconData = AppIcons.PlayArrow,
                    customTint = ThemeColors.onPrimaryButton,
                )
            }
        }
    }
}