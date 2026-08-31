/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.resourceslogic.theme.values

import androidx.compose.ui.text.style.TextAlign
import eu.europa.ec.resourceslogic.theme.templates.ThemeTextStyle.Companion.toTextStyle
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.ThemeTypography

class ThemeTextStyles {

    companion object {
        val onPrimaryButton get() = ThemeTypography.Companion.typo.labelLarge.toTextStyle().copy(
            color = ThemeColors.onPrimaryButton,
            textAlign = TextAlign.Center,
        )
        val onSecondaryButton get() = ThemeTypography.Companion.typo.labelLarge.toTextStyle().copy(
            color = ThemeColors.onSecondaryButton,
            textAlign = TextAlign.Center,
        )
        val key get() = ThemeTypography.key.toTextStyle()
    }
}
