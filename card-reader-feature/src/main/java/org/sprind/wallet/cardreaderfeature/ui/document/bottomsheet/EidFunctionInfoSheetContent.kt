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

package org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import org.sprind.wallet.uilogic.component.InfoCard
import org.sprind.wallet.uilogic.component.InfoCardText

/**
 * The explanation the toolbar's help action opens on the opening step of the issuance flow: what the
 * eID function of the German ID documents is, and where the card PIN it needs comes from.
 *
 * @param onCloseClick dismisses the sheet.
 */
@Composable
fun EidFunctionInfoSheetContent(
    onCloseClick: () -> Unit,
) {
    ExplanationSheet(
        header = ExplanationSheetHeader(
            icon = AppIcons.EidLogo,
            title = stringResource(R.string.pid_issuance_eid_function_info_title),
            // The eID mark carries the brand in its gradient, so it keeps its own colors.
            tintIcon = false,
        ),
        onCloseClick = onCloseClick,
    ) {
        InfoCard(headline = stringResource(R.string.pid_issuance_eid_function_info_headline_1)) {
            InfoCardText(stringResource(R.string.pid_issuance_eid_function_info_paragraph_1))
        }
        InfoCard(headline = stringResource(R.string.pid_issuance_eid_function_info_headline_2)) {
            InfoCardText(stringResource(R.string.pid_issuance_eid_function_info_paragraph_2))
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun EidFunctionInfoSheetContentPreview() {
    PreviewTheme {
        EidFunctionInfoSheetContent(onCloseClick = {})
    }
}