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
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE_32
import org.sprind.wallet.uilogic.component.ContentTemplateBody
import org.sprind.wallet.uilogic.component.ContentTemplateConfig
import org.sprind.wallet.uilogic.component.ContentTemplateDefaults
import org.sprind.wallet.uilogic.component.NumberedSteps
import org.sprind.wallet.uilogic.component.ProvideContentTemplateStyle

/**
 * What the flow is about to do, once the user has confirmed the card PIN is at hand: the four steps
 * from granting data release to holding the credential.
 */
@Composable
fun ProgressStepsView(
    modifier: Modifier,
) {
    ProvideContentTemplateStyle(
        style = ContentTemplateDefaults.style.copy(
            titleTextStyle = MaterialTheme.typography.titleLarge,
        ),
    ) {
        ContentTemplateBody(
            modifier = modifier,
            templateConfig = ContentTemplateConfig(verticalSpacing = SPACING_LARGE_32.dp),
            title = { Text(text = stringResource(R.string.pid_issuance_process_overview_title)) },
            extraContent = {
                NumberedSteps(
                    steps = listOf(
                        stringResource(R.string.pid_issuance_process_overview_list_1),
                        stringResource(R.string.pid_issuance_process_overview_list_2),
                        stringResource(R.string.pid_issuance_process_overview_list_3),
                        stringResource(R.string.pid_issuance_process_overview_list_4_inital),
                    ),
                )
            },
        )
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun ProgressStepsViewPreview() {
    PreviewTheme {
        ProgressStepsView(
            modifier = Modifier.fillMaxSize()
        )
    }
}