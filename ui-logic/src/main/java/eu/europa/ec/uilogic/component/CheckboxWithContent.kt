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

package eu.europa.ec.uilogic.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.wrap.CheckboxData
import eu.europa.ec.uilogic.component.wrap.WrapCheckbox

@Composable
fun CheckboxWithContent(
    checkboxData: CheckboxData,
    modifier: Modifier = Modifier,
    checkboxModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .toggleable(
                enabled = checkboxData.enabled,
                value = checkboxData.isChecked,
                onValueChange = { checkboxData.onCheckedChange?.invoke(!checkboxData.isChecked) },
                role = Role.Checkbox,
            ),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WrapCheckbox(
            // null recommended for accessibility with screenreaders
            checkboxData = checkboxData.copy(onCheckedChange = null),
            modifier = checkboxModifier
        )

        HSpacer.Medium()

        content(this)
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CheckboxWithContentPreview() {
    var isChecked by remember {
        mutableStateOf(true)
    }
    val checkBoxData = CheckboxData(
        isChecked = isChecked,
        onCheckedChange = {
            isChecked = it
        },
        enabled = true
    )

    PreviewTheme {
        CheckboxWithContent(
            checkboxData = checkBoxData,
            content = {
                Text(text = "Name")
            }
        )
    }
}