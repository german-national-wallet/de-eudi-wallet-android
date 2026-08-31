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

package org.sprind.wallet.cardreaderfeature.ui.document.onboarding

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import eu.europa.ec.uilogic.component.wrap.WrapImage
import org.sprind.wallet.uilogic.component.ContentIllustrationPlacement
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle

/**
 * Second step of the issuance flow: asks whether the user has already set the 6-digit card PIN the
 * eID function needs, and shows the card with its PIN pad so the question is recognizable without
 * knowing the official term.
 *
 * The three answers ("yes" / "no, with the letter" / "no letter or forgotten") live in the flow's
 * sticky bottom bar as the shared `button_navigation_bottom` section, together with the row that
 * opens the explanation sheet, see
 * [org.sprind.wallet.cardreaderfeature.ui.document.read.CardReaderStickyButtons].
 */
@Composable
fun OnboardingPinView(
    modifier: Modifier,
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleLarge,
        ),
    ) {
        ContentTemplateBody(
            modifier = modifier,
            templateConfig = ContentTemplateConfig(
                verticalSpacing = SPACING_LARGE_32.dp,
                illustrationPlacement = ContentIllustrationPlacement.BELOW_TEXT,
            ),
            title = { Text(text = stringResource(R.string.pid_issuance_onboarding_eid_title)) },
            // Decoration: it pictures the card and the PIN the title asks about, so the template
            // keeps it out of the accessibility tree.
            illustration = {
                WrapImage(
                    iconData = AppIcons.StackedIdCardsWithPin,
                    modifier = Modifier
                        .width(ILLUSTRATION_WIDTH)
                        .aspectRatio(ILLUSTRATION_ASPECT_RATIO),
                    contentScale = ContentScale.Fit,
                )
            },
        )
    }
}

private val ILLUSTRATION_WIDTH = 207.dp
private const val ILLUSTRATION_ASPECT_RATIO = 1.319f

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun OnboardingPinContentPreview() {
    PreviewTheme {
        OnboardingPinView(
            modifier = Modifier.fillMaxSize()
        )
    }
}