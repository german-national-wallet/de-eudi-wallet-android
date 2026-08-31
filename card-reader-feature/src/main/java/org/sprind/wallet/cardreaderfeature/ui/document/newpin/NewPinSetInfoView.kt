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

@file:OptIn(ExperimentalMaterial3Api::class)

package org.sprind.wallet.cardreaderfeature.ui.document.newpin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.NewPinInfoSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.read.ReadCardBottomSheet
import org.sprind.wallet.cardreaderfeature.ui.document.read.ReadCardBottomSheetConfig
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_LARGE
import eu.europa.ec.uilogic.component.wrap.Banner
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapImage

@Composable
fun NewPinSetInfoView(
    modifier: Modifier,
    bottomCardSheetConfig: ReadCardBottomSheetConfig,
) {
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_LARGE.dp)
        ) {
            ContentHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { heading() },
                config = ContentHeaderConfig(
                    title = stringResource(R.string.eid_setup_card_pin_intro_title),
                    titleTextConfig = TextConfig(MaterialTheme.typography.titleLarge),
                ),
            )

            // Decoration: the title and the banner below carry the whole message, so the artwork
            // would only add an "Eid Pin Set" announcement with nothing behind it.
            WrapImage(
                iconData = AppIcons.EidPinSet,
                modifier = Modifier.clearAndSetSemantics { },
            )

            Banner(
                modifier = Modifier.fillMaxWidth(),
                body = stringResource(R.string.eid_setup_card_pin_intro_paragraph)
            )
        }
    }
    ReadCardBottomSheet(bottomCardSheetConfig) {
        NewPinInfoSheetContent(title = bottomCardSheetConfig.title)
    }
}


@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun NewPinSetInfoPreview() {
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    PreviewTheme {
        NewPinSetInfoView(
            modifier = Modifier.fillMaxSize(),
            bottomCardSheetConfig = ReadCardBottomSheetConfig(
                title = "",
                sheetState = sheet,
                isBottomSheetOpen = false,
                onBottomSheetDismissRequest = {}
            ),
        )
    }
}

