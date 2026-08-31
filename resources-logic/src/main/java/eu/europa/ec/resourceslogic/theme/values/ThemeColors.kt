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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eu.europa.ec.resourceslogic.theme.ThemeManager
import eu.europa.ec.resourceslogic.theme.templates.ThemeColorsTemplate
import org.sprind.wallet.resourceslogic.generated.DarkColors
import org.sprind.wallet.resourceslogic.generated.DesignColors
import org.sprind.wallet.resourceslogic.generated.LightColors

private val isInDarkMode: Boolean
    get() {
        return ThemeManager.instance.set.isInDarkMode
    }

private val designColors: DesignColors
    get() = if (isInDarkMode) DarkColors else LightColors

class ThemeColors {
    companion object {
        private const val white: Long = 0xFFFFFFFF
        private const val black: Long = 0xFF000000

        private const val eudiw_theme_light_surfaceBrightest: Long = white

        // Light theme extra colors palette.
        internal const val eudiw_theme_light_legacy_success: Long = 0xFF55953B
        internal val eudiw_theme_light_warning: Long get() = LightColors.warning
        internal const val eudiw_theme_light_pending: Long = 0xFFAB5200
        internal const val eudiw_theme_light_divider: Long = 0xFFD9D9D9
        internal const val eudiw_theme_light_highlight: Long = 0xED546E7A
        internal const val eudiw_theme_light_default_eaa: Long = 0xFF5B5B71

        private const val eudiw_theme_dark_surfaceBrightest: Long = black

        // Dark theme extra colors palette.
        internal const val eudiw_theme_dark_legacy_success: Long = 0xFF93D875
        internal val eudiw_theme_dark_warning: Long get() = DarkColors.warning
        internal const val eudiw_theme_dark_pending: Long = 0xFFCC8B3F
        internal const val eudiw_theme_dark_divider: Long = 0xFFD9D9D9
        internal const val eudiw_theme_dark_highlight: Long = 0xFFCAC4D0
        internal const val eudiw_theme_dark_default_eaa: Long = 0xFFA5A5B8

        const val eudiw_theme_light_background_preview: Long = 0xFFFFFFFFL // = LightColors.schemesSurface
        const val eudiw_theme_dark_background_preview: Long = 0xFF141218L  // = DarkColors.schemesSurface

        internal val lightColors = ThemeColorsTemplate(
            primary = LightColors.schemesPrimary,
            onPrimary = LightColors.schemesOnPrimary,
            primaryContainer = LightColors.schemesPrimaryContainer,
            onPrimaryContainer = LightColors.schemesOnPrimaryContainer,
            secondary = LightColors.schemesSecondary,
            onSecondary = LightColors.schemesOnSecondary,
            secondaryContainer = LightColors.schemesSecondaryContainer,
            onSecondaryContainer = LightColors.schemesOnSecondaryContainer,
            tertiary = LightColors.schemesTertiary,
            onTertiary = LightColors.schemesOnTertiary,
            tertiaryContainer = LightColors.schemesTertiaryContainer,
            onTertiaryContainer = LightColors.schemesOnTertiaryContainer,
            error = LightColors.schemesError,
            errorContainer = LightColors.schemesErrorContainer,
            onError = LightColors.schemesOnError,
            onErrorContainer = LightColors.schemesOnErrorContainer,
            background = LightColors.schemesBackground,
            onBackground = LightColors.schemesOnBackground,
            surface = LightColors.schemesSurface,
            onSurface = LightColors.schemesOnSurface,
            surfaceVariant = LightColors.schemesSurfaceVariant,
            onSurfaceVariant = LightColors.schemesOnSurfaceVariant,
            outline = LightColors.schemesOutline,
            inverseOnSurface = LightColors.schemesInverseOnSurface,
            inverseSurface = LightColors.schemesInverseSurface,
            inversePrimary = LightColors.schemesInversePrimary,
            surfaceTint = LightColors.schemesSurfaceTint,
            outlineVariant = LightColors.schemesOutlineVariant,
            scrim = LightColors.schemesScrim,
            surfaceBright = LightColors.schemesSurfaceBright,
            surfaceDim = LightColors.schemesSurfaceDim,
            surfaceContainer = LightColors.schemesSurfaceContainer,
            surfaceContainerHigh = LightColors.schemesSurfaceContainerHigh,
            surfaceContainerHighest = LightColors.schemesSurfaceContainerHighest,
            surfaceContainerLow = LightColors.schemesSurfaceContainerLow,
            surfaceContainerLowest = LightColors.schemesSurfaceContainerLowest,
        )

        internal val darkColors = ThemeColorsTemplate(
            primary = DarkColors.schemesPrimary,
            onPrimary = DarkColors.schemesOnPrimary,
            primaryContainer = DarkColors.schemesPrimaryContainer,
            onPrimaryContainer = DarkColors.schemesOnPrimaryContainer,
            secondary = DarkColors.schemesSecondary,
            onSecondary = DarkColors.schemesOnSecondary,
            secondaryContainer = DarkColors.schemesSecondaryContainer,
            onSecondaryContainer = DarkColors.schemesOnSecondaryContainer,
            tertiary = DarkColors.schemesTertiary,
            onTertiary = DarkColors.schemesOnTertiary,
            tertiaryContainer = DarkColors.schemesTertiaryContainer,
            onTertiaryContainer = DarkColors.schemesOnTertiaryContainer,
            error = DarkColors.schemesError,
            errorContainer = DarkColors.schemesErrorContainer,
            onError = DarkColors.schemesOnError,
            onErrorContainer = DarkColors.schemesOnErrorContainer,
            background = DarkColors.schemesBackground,
            onBackground = DarkColors.schemesOnBackground,
            surface = DarkColors.schemesSurface,
            onSurface = DarkColors.schemesOnSurface,
            surfaceVariant = DarkColors.schemesSurfaceVariant,
            onSurfaceVariant = DarkColors.schemesOnSurfaceVariant,
            outline = DarkColors.schemesOutline,
            inverseOnSurface = DarkColors.schemesInverseOnSurface,
            inverseSurface = DarkColors.schemesInverseSurface,
            inversePrimary = DarkColors.schemesInversePrimary,
            surfaceTint = DarkColors.schemesSurfaceTint,
            outlineVariant = DarkColors.schemesOutlineVariant,
            scrim = DarkColors.schemesScrim,
            surfaceBright = DarkColors.schemesSurfaceBright,
            surfaceDim = DarkColors.schemesSurfaceDim,
            surfaceContainer = DarkColors.schemesSurfaceContainer,
            surfaceContainerHigh = DarkColors.schemesSurfaceContainerHigh,
            surfaceContainerHighest = DarkColors.schemesSurfaceContainerHighest,
            surfaceContainerLow = DarkColors.schemesSurfaceContainerLow,
            surfaceContainerLowest = DarkColors.schemesSurfaceContainerLowest,
        )

        val primary: Color
            get() = Color(designColors.schemesPrimary)

        val secondary: Color
            get() = Color(designColors.schemesSecondary)

        val legacy_success: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_legacy_success)
            } else {
                Color(eudiw_theme_light_legacy_success)
            }

        val warning: Color
            get() = Color(designColors.warning)

        val pending: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_pending)
            } else {
                Color(eudiw_theme_light_pending)
            }

        val error: Color
            get() = Color(designColors.schemesError)

        val divider: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_divider)
            } else {
                Color(eudiw_theme_light_divider)
            }

        val primaryPid: Color
            get() = Color(designColors.colorPid)

        val onPrimaryPid: Color
            get() = Color(designColors.onColorPid)

        val backgroundPidLight: Color
            get() = Color(designColors.backgroundPidLight)

        val backgroundPidMedium: Color
            get() = Color(designColors.backgroundPidMedium)

        val defaultEaa: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_default_eaa)
            } else {
                Color(eudiw_theme_light_default_eaa)
            }

        val onSuccess: Color get() = Color(designColors.onSuccess)
        val success: Color get() = Color(designColors.success)

        /** Outline of an unfilled `tertiaryContainer` surface, e.g. an empty code-entry cell. */
        val tertiaryOutline: Color get() = Color(designColors.schemesTertiaryOutline)

        val labelLarge: Color
            get() = Color(designColors.schemesOnBackground)

        // TODO: The below color derivations of button and button text colors should be done by UX / in Figma.
        // If those were exported by Figma, we could just say "primaryButton = Color(signColors.schemesTertiary)
        // here. Then, Android and iOS wouldn't need to duplicate the UX knowledge that the color of
        // a primary button happens to match designColors.schemesTertiary.
        val primaryButton: Color
            get() = Color(designColors.schemesPrimaryContainer)

        val primaryButtonOutline: Color
            get() = Color(designColors.schemesPrimaryOutline)

        val onPrimaryButton: Color
            get() = Color(designColors.schemesOnPrimaryContainer)

        val disabledPrimaryButton: Color
            get() = primaryButton.copy(
                alpha = 0.32f
            )

        val onDisabledPrimaryButton: Color
            get() = onPrimaryButton.copy(
                alpha = 0.56f
            )

        val primaryButtonColors: ButtonColors
            get() = ButtonColors(
                containerColor = primaryButton,
                contentColor = onPrimaryButton,
                disabledContainerColor = disabledPrimaryButton,
                disabledContentColor = onDisabledPrimaryButton,
            )

        val secondaryButton: Color
            get() = Color.Transparent

        val onSecondaryButton: Color
            get() = Color(designColors.schemesOnSurface)

        val disabledSecondaryButton: Color
            get() = Color(designColors.schemesBackground).copy(
                alpha = 0.12f
            )

        val onDisabledSecondaryButton: Color
            get() = Color(designColors.schemesOnSurface)

        val secondaryButtonColors: ButtonColors
            get() = ButtonColors(
                containerColor = ThemeColors.secondaryButton,
                contentColor = ThemeColors.onSecondaryButton,
                disabledContainerColor = disabledSecondaryButton,
                disabledContentColor = onDisabledSecondaryButton,
            )

        val surfaceBrightest: Color
            get() = if (isInDarkMode) {
                Color(eudiw_theme_dark_surfaceBrightest)
            } else {
                Color(eudiw_theme_light_surfaceBrightest)
            }
    }
}

val ColorScheme.success: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_legacy_success)
    } else {
        Color(ThemeColors.eudiw_theme_light_legacy_success)
    }

val ColorScheme.warning: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_warning)
    } else {
        Color(ThemeColors.eudiw_theme_light_warning)
    }

val ColorScheme.pending: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_pending)
    } else {
        Color(ThemeColors.eudiw_theme_light_pending)
    }

val ColorScheme.divider: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_divider)
    } else {
        Color(ThemeColors.eudiw_theme_light_divider)
    }

val ColorScheme.highlight: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color(ThemeColors.eudiw_theme_dark_highlight)
    } else {
        Color(ThemeColors.eudiw_theme_light_highlight)
    }
