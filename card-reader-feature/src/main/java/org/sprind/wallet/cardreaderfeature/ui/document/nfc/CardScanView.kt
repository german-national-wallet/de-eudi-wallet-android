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

package org.sprind.wallet.cardreaderfeature.ui.document.nfc

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModeWithGermanAndEnglishPreviews
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import org.sprind.wallet.cardreaderfeature.domain.CardScanStatus
import org.sprind.wallet.cardreaderfeature.domain.NfcAntennaPosition
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
 * The screen a card is read on: the tapping animation playing behind a sheet that reports the read.
 *
 * The sheet cannot be dismissed — not by a swipe, a tap outside it, or a back press
 *
 * Accessibility:
 * - the animation is decorative and kept out of the accessibility tree: the sheet in front of it
 *   says in words where the card goes;
 * - the sheet, being modal, holds the accessibility focus, which is where everything about the read
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
        // The sheet covers the lower part of the screen, so the animation is centred in what is
        // left above it rather than in the screen, where the sheet would hide it.
        modifier = modifier.padding(bottom = SCAN_SHEET_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        WrapLottieAnimation(
            animation = config.antennaPosition.tappingAnimation,
            modifier = Modifier
                .size(SCAN_ANIMATION_SIZE)
                .clearAndSetSemantics { },
        )
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // Refusing the hidden state is what makes a swipe down bounce back instead of ending the
        // read.
        confirmValueChange = { it != SheetValue.Hidden },
    )

    WrapModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        // There is nothing to drag the sheet to, so it is drawn without the handle that would
        // suggest otherwise.
        dragHandle = {},
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
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

@get:RawRes
private val NfcAntennaPosition.tappingAnimation: Int
    get() = when (this) {
        NfcAntennaPosition.TOP -> R.raw.nfc_tapping_top
        NfcAntennaPosition.MIDDLE -> R.raw.nfc_tapping_middle
        NfcAntennaPosition.BOTTOM -> R.raw.nfc_tapping_bottom
    }

private val SCAN_ANIMATION_SIZE = 264.dp

private val SCAN_SHEET_HEIGHT = 280.dp

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