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
import eu.europa.ec.resourceslogic.theme.templates.ThemeTextStyle
import eu.europa.ec.resourceslogic.theme.templates.ThemeTextStyle.Companion.toTextStyle
import eu.europa.ec.resourceslogic.theme.templates.structures.ThemeFontWeight
import eu.europa.ec.resourceslogic.theme.templates.structures.ThemeTextAlign
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.resourceslogic.theme.values.ThemeTypography
import org.sprind.wallet.resourceslogic.generated.eudiDiatypeMedium

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

        val labelMediumProminent get() = ThemeTypography.labelMediumProminent.toTextStyle()

        val titleMediumLarge get() = ThemeTypography.titleMediumLarge.toTextStyle()

        /**
         * Dialog title per Figma "M3/title/medium": 24sp, Medium (500), 28 line height, centered.
         * Figma uses "EUDI Diatype Semi Extended Medium", which is not bundled; the regular-width
         * Diatype Medium is used instead. Swap the font here once the semi-extended medium is added.
         */
        val dialogTitle get() = ThemeTextStyle(
            fontFamily = listOf(eudiDiatypeMedium),
            fontWeight = ThemeFontWeight.W500,
            fontSize = 24,
            lineHeight = 28f,
            letterSpacing = 0f,
            textAlign = ThemeTextAlign.Center,
        ).toTextStyle()

        /** The banner headline per Figma: "M3/headline/small" set in Bold (700) rather than Medium. */
        val bannerTitle get() = ThemeTypography.Companion.typo.headlineSmall.copy(
            fontFamily = ThemeTypography.labelLargeProminent.fontFamily,
            fontWeight = ThemeFontWeight.W700,
        ).toTextStyle()

        val bottomNavigationLabelSelected get() = ThemeTextStyle(
            fontFamily = ThemeTypography.labelLargeProminent.fontFamily,
            fontWeight = ThemeFontWeight.W700,
        ).toTextStyle()
    }
}
