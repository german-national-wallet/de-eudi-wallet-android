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

package org.sprind.wallet.cardreaderfeature.ui.document.can

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.EidPinBottomSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.read.ReadCardBottomSheetConfig
import eu.europa.ec.commonfeature.ui.success.SuccessImage
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_100
import eu.europa.ec.uilogic.component.wrap.Banner
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet

@Composable
fun CanSuccessView(
    modifier: Modifier,
    bottomCardSheetConfig: ReadCardBottomSheetConfig,
    onSetCardPinButtonClick: () -> Unit,
    onSearchCitizenOfficeButtonClick: () -> Unit,
) {
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ContentHeader(
                modifier = Modifier.fillMaxWidth(),
                config = ContentHeaderConfig(
                    title = stringResource(R.string.pid_issuance_can_success_title),
                    titleTextConfig = TextConfig(MaterialTheme.typography.titleLarge),
                ),
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                SuccessImage(
                    modifier = Modifier
                        .size(SIZE_100.dp)
                        .align(Alignment.Center)
                )
            }

            Banner(
                body = stringResource(R.string.pid_issuance_can_success_paragraph_1),
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        if (bottomCardSheetConfig.isBottomSheetOpen) {
            WrapModalBottomSheet(
                onDismissRequest = { bottomCardSheetConfig.onBottomSheetDismissRequest() },
                sheetState = bottomCardSheetConfig.sheetState
            ) {
                EidPinBottomSheetContent(
                    onSetCardPinButtonClick = onSetCardPinButtonClick,
                    onSearchCitizenOfficeButtonClick = onSearchCitizenOfficeButtonClick,
                )
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CanSuccessPreview() {
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    PreviewTheme {
        CanSuccessView(
            modifier = Modifier.fillMaxSize(),
            bottomCardSheetConfig = ReadCardBottomSheetConfig(
                title = "",
                sheetState = sheet,
                isBottomSheetOpen = false,
                onBottomSheetDismissRequest = {}
            ),
            onSetCardPinButtonClick = {},
            onSearchCitizenOfficeButtonClick = {}
        )
    }
}
