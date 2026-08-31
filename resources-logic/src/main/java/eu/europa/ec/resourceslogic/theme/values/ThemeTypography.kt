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

import eu.europa.ec.resourceslogic.theme.templates.ThemeTextStyle
import eu.europa.ec.resourceslogic.theme.templates.ThemeTypographyTemplate
import org.sprind.wallet.resourceslogic.generated.DesignTypography

internal class ThemeTypography {
    companion object {
        val typo: ThemeTypographyTemplate
            get() = ThemeTypographyTemplate(
                displayLarge   = DesignTypography.displayLarge,
                displayMedium  = DesignTypography.displayMedium,
                displaySmall   = DesignTypography.displaySmall,
                headlineLarge  = DesignTypography.headlineLarge,
                headlineMedium = DesignTypography.headlineMedium,
                headlineSmall  = DesignTypography.headlineSmall,
                titleLarge     = DesignTypography.titleLarge,
                titleMedium    = DesignTypography.titleMedium,
                titleSmall     = DesignTypography.titleSmall,
                labelLarge     = DesignTypography.labelLarge,
                labelMedium    = DesignTypography.labelMedium,
                labelSmall     = DesignTypography.labelSmall,
                bodyLarge      = DesignTypography.bodyLarge,
                bodyMedium     = DesignTypography.bodyMedium,
                bodySmall      = DesignTypography.bodySmall,
            )

        val titleLargeMedium:     ThemeTextStyle get() = DesignTypography.titleLargeMedium
        val titleMediumLarge:     ThemeTextStyle get() = DesignTypography.titleMediumLarge
        val labelLargeProminent:  ThemeTextStyle get() = DesignTypography.labelLargeProminent
        val labelMediumProminent: ThemeTextStyle get() = DesignTypography.labelMediumProminent
        val key:                  ThemeTextStyle get() = DesignTypography.key
    }
}
