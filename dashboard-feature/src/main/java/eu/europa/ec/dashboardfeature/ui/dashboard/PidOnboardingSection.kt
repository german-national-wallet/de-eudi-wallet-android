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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.ThemeColors
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.SwitchData
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapSwitch
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.exposeTestTagsAsResourceId
import org.sprind.wallet.businesslogic.config.EidCardType
import org.sprind.wallet.businesslogic.config.isVirtual
import org.sprind.wallet.resourceslogic.theme.values.ThemeTextStyles
import org.sprind.wallet.uilogic.component.PidCredentialCard


@VisibleForTesting
const val PID_SIMULATED_EID_CARD_TOGGLE = "pidSimulatedEidCardToggle"

/**
 * What the dashboard shows while there is no digital ID yet: a tilted preview of the card, fading
 * into the button that starts the issuance. Where the simulated eID card is available, a strip
 * with its switch closes the card.
 */
@Composable
internal fun PidOnboardingSection(
    eidCardType: EidCardType,
    isSimulatedEidCardEnabled: Boolean,
    onIssueDocument: () -> Unit,
    onToggleEidCard: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SECTION_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(SPACING_MEDIUM.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_MEDIUM.dp),
        ) {
            WrapText(
                text = stringResource(R.string.overview_initial_pid_teaser_title),
                textConfig = TextConfig(
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = Int.MAX_VALUE,
                ),
            )

            CardPreview(onIssueDocument = onIssueDocument)
        }

        if (isSimulatedEidCardEnabled) {
            SimulatedEidCardToggle(
                SwitchData(
                    checked = eidCardType.isVirtual,
                    onCheckedChange = onToggleEidCard,
                ),
            )
        }
    }
}

/** The card, cut off where the button starts, so it reads as a preview of what is created. */
@Composable
private fun CardPreview(onIssueDocument: () -> Unit) {
    val sectionColor = MaterialTheme.colorScheme.surfaceContainer
    val fadeBrush = remember(sectionColor) {
        Brush.verticalGradient(
            0f to sectionColor.copy(alpha = 0f),
            FADE_OPAQUE_FROM to sectionColor,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            .clipToBounds(),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = SPACING_SMALL.dp),
        ) {
            // The design shows the full-size card scaled down as a whole, so compose it at its
            // design size under a smaller density: every dp and sp inside it, text included,
            // shrinks with it.
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = density.density * PREVIEW_CARD_SCALE,
                ),
            ) {
                PidCredentialCard(
                    modifier = Modifier
                        .width(PID_CARD_DESIGN_WIDTH)
                        .rotate(PREVIEW_CARD_ROTATION)
                        .clearAndSetSemantics { },
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(FADE_HEIGHT)
                .background(fadeBrush),
        )

        WrapButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(vertical = SPACING_EXTRA_SMALL.dp),
            buttonConfig = ButtonConfig(
                type = ButtonType.PRIMARY,
                onClick = onIssueDocument,
            ),
        ) {
            WrapIcon(
                modifier = Modifier.size(SIZE_MEDIUM_LARGE.dp),
                iconData = AppIcons.Add,
                customTint = ThemeColors.onPrimaryButton,
            )
            Spacer(modifier = Modifier.width(SPACING_SMALL.dp))
            WrapText(
                text = stringResource(R.string.overview_initial_pid_teaser_prim_button),
                textConfig = TextConfig(
                    style = ThemeTextStyles.onPrimaryButton,
                    color = ThemeColors.onPrimaryButton,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun SimulatedEidCardToggle(switchData: SwitchData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_SMALL.dp),
        horizontalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WrapText(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.pid_inspection_initial_dashboard_temp_label_1),
            textConfig = TextConfig(
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = Int.MAX_VALUE,
            ),
        )

        WrapSwitch(
            modifier = Modifier
                .exposeTestTagsAsResourceId()
                .applyTestTag(PID_SIMULATED_EID_CARD_TOGGLE),
            switchData = switchData,
        )
    }
}

private val SECTION_SHAPE = RoundedCornerShape(SPACING_LARGE.dp)
private val PREVIEW_HEIGHT = 168.dp

// The width the design draws the full PID card at, and the share of it the preview shows: 254dp.
private val PID_CARD_DESIGN_WIDTH = 328.dp
private const val PREVIEW_CARD_SCALE = 254f / 328f
private const val PREVIEW_CARD_ROTATION = -2.86f
private val FADE_HEIGHT = 97.dp
private const val FADE_OPAQUE_FROM = 0.45f

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun PidOnboardingSectionPreview() {
    PreviewTheme {
        Box(modifier = Modifier.padding(SPACING_MEDIUM.dp)) {
            PidOnboardingSection(
                eidCardType = EidCardType.PHYSICAL,
                isSimulatedEidCardEnabled = true,
                onIssueDocument = {},
                onToggleEidCard = {},
            )
        }
    }
}
