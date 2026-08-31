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

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.CanBottomSheetContent
import org.sprind.wallet.cardreaderfeature.ui.document.read.ReadCardBottomSheetConfig
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

@Composable
fun EidPinBlockedView(
    modifier: Modifier,
    bottomCardSheetConfig: ReadCardBottomSheetConfig,
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
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContentHeader(
                modifier = Modifier.fillMaxWidth(),
                config = ContentHeaderConfig(
                    title = stringResource(R.string.pid_issuance_can_intro_title),
                    titleTextConfig = TextConfig(MaterialTheme.typography.titleLarge),
                ),
            )

            WrapImage(iconData = AppIcons.PinBlocked)
            
            Banner(
                body = stringResource(R.string.pid_issuance_can_intro_paragraph_1),
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        if (bottomCardSheetConfig.isBottomSheetOpen) {
            WrapModalBottomSheet(
                onDismissRequest = { bottomCardSheetConfig.onBottomSheetDismissRequest() },
                sheetState = bottomCardSheetConfig.sheetState
            ) {
                CanBottomSheetContent()
            }
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ErrorPinPreview() {
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    PreviewTheme {
        EidPinBlockedView(
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
