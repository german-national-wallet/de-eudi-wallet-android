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

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.utils.ELEVATION_8
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import org.sprind.wallet.cardreaderfeature.domain.CardScanStatus
import org.sprind.wallet.cardreaderfeature.domain.NfcAntennaPosition
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.CARD_SCAN_STATUS_MIN_HEIGHT
import org.sprind.wallet.cardreaderfeature.ui.document.bottomsheet.CardScanStatusSheetContent
import org.sprind.wallet.uilogic.component.WrapLottieAnimation

/**
 * What a running card read is: where the card has to be held, what it is being read for, and how
 * far it has got.
 *
 * @property antennaPosition decides which tapping animation is played, so the phone on screen is
 *   pictured with the card where this device actually reads it.
 * @property status the stage the read is at, see [CardScanStatus].
 * @property scanReason what the card is being read for, which is what the outcome is phrased in
 *   terms of.
 * @property readingProgress percentage the SDK reports while reading, or `null` before it starts.
 */
@Immutable
data class CardScanConfig(
    val antennaPosition: NfcAntennaPosition,
    val status: CardScanStatus,
    val scanReason: NfcScanReason,
    val readingProgress: Int? = null,
)

/**
 * The screen a card is read on: the tapping animation playing behind a dialog at the bottom of the
 * screen that reports the read.
 *
 * The dialog cannot be dismissed — not by a tap outside it or a back press.
 *
 * Accessibility:
 * - the animation is decorative and kept out of the accessibility tree: the dialog in front of it
 *   says in words where the card goes;
 * - the dialog, being modal, holds the accessibility focus, which is where everything about the read
 *   is announced (see [CardScanStatusSheetContent]).
 */
@Composable
fun CardScanView(
    modifier: Modifier,
    config: CardScanConfig,
    onCancelClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    Box(
        // The dialog covers the lower part of the screen, so the animation is centred in what is
        // left above it rather than in the screen, where the dialog would hide it.
        modifier = modifier.padding(bottom = CARD_SCAN_STATUS_MIN_HEIGHT + SCAN_DIALOG_MARGIN),
        contentAlignment = Alignment.Center,
    ) {
        WrapLottieAnimation(
            animation = config.antennaPosition.tappingAnimation,
            iterations = 1,
            modifier = Modifier
                .size(SCAN_ANIMATION_SIZE)
                .clearAndSetSemantics { },
        )
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            // Full width, so the dialog can be laid out against the bottom edge of the screen.
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(SCAN_DIALOG_MARGIN),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SCAN_DIALOG_CORNER),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = ELEVATION_8.dp,
            ) {
                CardScanStatusSheetContent(
                    status = config.status,
                    scanReason = config.scanReason,
                    readingProgress = config.readingProgress,
                    onCancelClick = onCancelClick,
                    onContinueClick = onContinueClick,
                )
            }
        }
    }
}

@get:RawRes
private val NfcAntennaPosition.tappingAnimation: Int
    get() = when (this) {
        NfcAntennaPosition.TOP -> R.raw.nfc_tapping_top
        NfcAntennaPosition.MIDDLE -> R.raw.nfc_tapping_middle
        NfcAntennaPosition.BOTTOM -> R.raw.nfc_tapping_bottom
    }

private val SCAN_ANIMATION_SIZE = 264.dp

private val SCAN_DIALOG_MARGIN = SPACING_MEDIUM.dp
private val SCAN_DIALOG_CORNER = 28.dp

@ThemeModeWithGermanAndEnglishPreviews
@Composable
private fun CardScanPreview() {
    PreviewTheme {
        CardScanView(
            modifier = Modifier.fillMaxSize(),
            config = CardScanConfig(
                antennaPosition = NfcAntennaPosition.MIDDLE,
                status = CardScanStatus.READY,
                scanReason = NfcScanReason.EID_PIN,
            ),
            onCancelClick = {},
            onContinueClick = {},
        )
    }
}