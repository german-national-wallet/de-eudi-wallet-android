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

package eu.europa.ec.uilogic.component.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import eu.europa.ec.resourceslogic.theme.values.ThemeColors

/**
 * Creates previews for Light and Dark mode
 * */
@Preview(
    name = "Light Mode English", showBackground = true, uiMode = UI_MODE_NIGHT_NO,
    backgroundColor = ThemeColors.eudiw_theme_light_background_preview,
    locale = "en"
)
@Preview(
    name = "Light Mode English With Increased Font Scale",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_NO,
    backgroundColor = ThemeColors.eudiw_theme_light_background_preview,
    fontScale = 2.0f,
    locale = "en"
)
// EUDI-removed
/*
@Preview(
    name = "Dark Mode English", showBackground = true, uiMode = UI_MODE_NIGHT_YES,
    backgroundColor = ThemeColors.eudiw_theme_dark_background_preview,
    locale = "en"
)

@Preview(
    name = "Dark Mode English With Increased Font Scale",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    backgroundColor = ThemeColors.eudiw_theme_dark_background_preview,
    fontScale = 2.0f,
    locale = "en"
)
*/


@Preview(
    name = "Light Mode German", showBackground = true, uiMode = UI_MODE_NIGHT_NO,
    backgroundColor = ThemeColors.eudiw_theme_light_background_preview,
    locale = "de"
)


@Preview(
    name = "Light Mode German With Increased Font Scale",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_NO,
    backgroundColor = ThemeColors.eudiw_theme_light_background_preview,
    fontScale = 2.0f,
    locale = "de"
)

// EUDI-removed
/*
@Preview(
    name = "Dark Mode German", showBackground = true, uiMode = UI_MODE_NIGHT_YES,
    backgroundColor = ThemeColors.eudiw_theme_dark_background_preview,
    locale = "de"
)

@Preview(
    name = "Dark Mode German With Increased Font Scale",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    backgroundColor = ThemeColors.eudiw_theme_dark_background_preview,
    fontScale = 2.0f,
    locale = "de"
)
*/

annotation class ThemeModeWithGermanAndEnglishPreviews