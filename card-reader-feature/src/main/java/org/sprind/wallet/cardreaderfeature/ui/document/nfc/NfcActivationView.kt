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

package org.sprind.wallet.cardreaderfeature.ui.document.nfc

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import eu.europa.ec.uilogic.component.wrap.WrapImage
import org.sprind.wallet.uilogic.component.ContentIllustrationPlacement
import org.sprind.wallet.uilogic.component.ContentNotice
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle

/**
 * Dead end of the reader flow while the device has NFC switched off: the card can
 * not be read at all, so instead of letting the user reach a scan that cannot
 * succeed, this step explains why and offers the jump to the system settings.
 *
 * The whole group — title, illustration and banner — is centered as one block, and
 * the flow's step indicator is intentionally left out here: this is a detour from
 * the numbered steps, not one of them.
 *
 * The primary action lives in the flow's sticky bottom bar, see
 * [org.sprind.wallet.cardreaderfeature.ui.document.read.CardReaderStickyButtons].
 */
@Composable
fun NfcActivationView(
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
            title = { Text(text = stringResource(R.string.nfc_scanning_nfc_not_activated_title)) },
            illustration = {
                // Decoration: the title already says NFC is off and the banner explains
                // what to do, so announcing the artwork would only repeat both.
                WrapImage(
                    iconData = AppIcons.NfcNotActivated,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            },
            extraContent = {
                // Pushes the notice down the body, the way the design places it away from
                // the illustration. The weight resolves because the scrolling column keeps
                // the viewport height as its minimum.
                Spacer(modifier = Modifier.weight(NOTICE_SPACER_WEIGHT))
                // No leading icon: the design shows the plain notice, and the title above
                // already carries the alert.
                ContentNotice(icon = null) {
                    Text(text = stringResource(R.string.nfc_scanning_nfc_not_activated_paragraph))
                }
            },
        )
    }
}

private const val NOTICE_SPACER_WEIGHT = .5f

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NfcActivationPreview() {
    PreviewTheme {
        NfcActivationView(modifier = Modifier.fillMaxSize())
    }
}