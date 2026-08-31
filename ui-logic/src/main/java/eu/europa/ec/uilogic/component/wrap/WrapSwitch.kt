package eu.europa.ec.uilogic.component.wrap

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.europa.ec.resourceslogic.theme.values.ThemeColors

data class SwitchData(
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val onCheckedChange: ((Boolean) -> Unit)? = null,
)
@Composable
fun WrapSwitch(
    switchData: SwitchData,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = switchData.checked,
        onCheckedChange = switchData.onCheckedChange,
        modifier = modifier,
        enabled = switchData.enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = ThemeColors.primaryButton,
        ),
    )
}
