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

package eu.europa.ec.resourceslogic.theme.values

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

/**
 * Safely parses a CSS color string to a Compose Color using Android's Color.parseColor().
 *
 * @param cssColor The CSS color string (e.g., "#5B5B71", "#FFFFFF", "#AARRGGBB")
 * @param fallback The color to return if parsing fails. Defaults to Color.Unspecified.
 * @return The parsed Color, or the fallback if parsing failed.
 */
fun parseCssColor(cssColor: String?, fallback: Color = Color.Unspecified): Color {
    return when {
        cssColor.isNullOrEmpty() -> fallback
        !cssColor.startsWith("#") -> fallback
        else -> try {
            Color(cssColor.toColorInt())
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }
}
