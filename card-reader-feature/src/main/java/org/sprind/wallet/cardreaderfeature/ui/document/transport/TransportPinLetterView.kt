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

package org.sprind.wallet.cardreaderfeature.ui.document.transport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.wrap.Banner
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.NoKnownPinFindTownHallBottomSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.read.ReadCardBottomSheetConfig

@ExperimentalMaterial3Api
@Composable
fun TransportPinLetterContent(
    modifier: Modifier,
    bottomCardSheetConfig: ReadCardBottomSheetConfig,
    onSearchCitizenOfficeButtonClick: () -> Unit,
) {
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ContentHeader(
                modifier = Modifier.fillMaxWidth(),
                config = ContentHeaderConfig(
                    title = stringResource(R.string.eid_setup_transport_pin_intro_title),
                    titleTextConfig = TextConfig(MaterialTheme.typography.titleLarge),
                ),
            )

            WrapImage(
                iconData = AppIcons.LetterTransportPin,
                modifier = Modifier.height(170.dp),
                contentScale = ContentScale.FillHeight
            )

            Banner(
                modifier = Modifier.fillMaxWidth(),
                body = stringResource(R.string.eid_setup_transport_pin_intro_paragraph)
            )
        }
    }
    if (bottomCardSheetConfig.isBottomSheetOpen) {
        WrapModalBottomSheet(
            onDismissRequest = { bottomCardSheetConfig.onBottomSheetDismissRequest() },
            sheetState = bottomCardSheetConfig.sheetState
        ) {
            NoKnownPinFindTownHallBottomSheetContent(
                onSecondaryButtonClick = onSearchCitizenOfficeButtonClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun TransportPinLetterViewPreview() {
    PreviewTheme {
        val sheet = rememberModalBottomSheetState(
            skipPartiallyExpanded = false
        )
        TransportPinLetterContent(
            modifier = Modifier.fillMaxSize(),
            bottomCardSheetConfig = ReadCardBottomSheetConfig(
                title = "",
                sheetState = sheet,
                isBottomSheetOpen = false,
                onBottomSheetDismissRequest = {}
            ),
            onSearchCitizenOfficeButtonClick = {}
        )
    }
}
