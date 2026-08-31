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

package eu.europa.ec.dashboardfeature.ui.dashboard

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.BORDER_STROKE_1
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.SwitchData
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapSwitch
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.exposeTestTagsAsResourceId
import org.sprind.wallet.businesslogic.config.EidCardType
import org.sprind.wallet.businesslogic.config.isVirtual
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.DocumentCard


@VisibleForTesting
const val PID_SIMULATED_EID_CARD_TOGGLE = "pidSimulatedEidCardToggle"

@Composable
internal fun PidOnboardingSection(
    eidCardType: EidCardType,
    onIssueDocument: () -> Unit,
    onToggleEidCard: (Boolean) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        val pidSurfaceHeight = remember { mutableIntStateOf(0) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    pidSurfaceHeight.intValue = coordinates.size.height
                }
                .zIndex(1f),
            shape = CardDefaults.shape,
            color = ThemeColors.backgroundPidLight,
            shadowElevation = 0.dp,
            border = BorderStroke(
                width = BORDER_STROKE_1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                WrapText(
                    modifier = Modifier
                        .padding(top = SPACING_LARGE.dp)
                        .padding(horizontal = SPACING_MEDIUM.dp),
                    text = stringResource(R.string.pid_inspection_initial_dashboard_title),
                    textConfig = TextConfig(
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SPACING_SMALL.dp)
                ) {
                    WrapImage(
                        modifier = Modifier.padding(start = SPACING_MEDIUM.dp),
                        iconData = AppIcons.PhoneVector
                    )

                    DocumentCard(
                        modifier = Modifier
                            .padding(
                                top = 40.dp,
                                start = SPACING_EXTRA_LARGE.dp
                            )
                            .width(189.dp)
                            .height(118.dp)
                            .align(Alignment.TopCenter)
                            .graphicsLayer(
                                rotationZ = -10f
                            )
                    )

                    Column(
                        modifier =
                            Modifier
                                .padding(top = 220.dp)
                                .fillMaxWidth()
                                .background(color = ThemeColors.backgroundPidMedium),
                    ) {
                        SimulatedEidCardToggle(
                            SwitchData(
                                checked = eidCardType.isVirtual,
                                onCheckedChange = onToggleEidCard
                            )
                        )
                        AddDocumentStartAction {
                            onIssueDocument()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimulatedEidCardToggle(switchData: SwitchData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SPACING_LARGE.dp),
        horizontalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WrapText(
            modifier =
                Modifier
                    .padding(start = SPACING_MEDIUM.dp)
                    .weight(1f),
            text = stringResource(R.string.pid_inspection_initial_dashboard_temp_label_1),
            textConfig =
                TextConfig(
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = Int.MAX_VALUE,
                ),
        )

        WrapSwitch(
            modifier = Modifier
                .padding(end = SPACING_MEDIUM.dp)
                .exposeTestTagsAsResourceId()
                .applyTestTag(PID_SIMULATED_EID_CARD_TOGGLE),
            switchData = switchData,
        )
    }
}

@Composable
private fun AddDocumentStartAction(onButtonClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SPACING_LARGE.dp),
        horizontalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WrapText(
            modifier =
                Modifier
                    .padding(start = SPACING_MEDIUM.dp)
                    .weight(1f),
            text = stringResource(R.string.pid_inspection_initial_dashboard_paragraph),
            textConfig =
                TextConfig(
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = Int.MAX_VALUE,
                ),
        )

        WrapButton(
            modifier =
                Modifier
                    .padding(end = SPACING_MEDIUM.dp),
            buttonConfig =
                ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = onButtonClick,
                ),
        ) {
            WrapText(
                textConfig =
                    TextConfig(
                        style = ThemeTextStyles.onPrimaryButton,
                        color = ThemeColors.onPrimaryButton,
                        textAlign = TextAlign.Center,
                    ),
                text = stringResource(R.string.app_onboarding_onboarding_4_prim_button),
            )
        }
    }
}

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PidOnboardingSectionPreview() {
    PreviewTheme {
        PidOnboardingSection(
            eidCardType = EidCardType.PHYSICAL,
            onIssueDocument = {},
        ) { }
    }
}