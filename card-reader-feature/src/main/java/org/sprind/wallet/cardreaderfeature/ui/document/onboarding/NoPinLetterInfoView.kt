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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle

/**
 * End of the card PIN branch: the user has no PIN letter, or has forgotten the card PIN, so the PIN
 * can only be set at a citizen's office. The screen says so and offers the office finder, which is
 * the only way on from here — the flow cannot continue until the PIN exists.
 *
 * The cityscape the designs put above the text is not part of this body: it spans the screen and
 * reaches behind the status bar, so the route draws it as its toolbar instead, see
 * [org.sprind.wallet.cardreaderfeature.ui.document.read.CardReaderToolbar]. The office finder lives
 * in the flow's sticky bottom bar, see
 * [org.sprind.wallet.cardreaderfeature.ui.document.read.CardReaderStickyButtons].
 */
@Composable
fun NoPinLetterInfoView(
    modifier: Modifier,
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleMedium,
        ),
    ) {
        ContentTemplateBody(
            modifier = modifier,
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_MEDIUM.dp),
            title = { Text(text = stringResource(R.string.pid_issuance_no_letter_forgot_info_title)) },
            body = { Text(text = stringResource(R.string.pid_issuance_no_letter_forgot_info_paragraph)) },
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NoPinLetterInfoViewPreview() {
    PreviewTheme {
        NoPinLetterInfoView(
            modifier = Modifier.fillMaxSize()
        )
    }
}