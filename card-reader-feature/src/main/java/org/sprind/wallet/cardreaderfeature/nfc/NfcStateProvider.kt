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

package org.sprind.wallet.cardreaderfeature.nfc

import android.content.Context
import android.nfc.NfcAdapter
import org.sprind.wallet.cardreaderfeature.domain.NfcAntennaPosition
import org.sprind.wallet.cardreaderfeature.domain.nfcAntennaPositionOf

/**
 * Reports whether the device can currently read a card over NFC.
 *
 * It is owned by the card reader entry point, next to [CardReaderNfcDispatcher],
 * so the platform adapter stays in one layer
 */
class NfcStateProvider(
    private val context: Context,
) {

    // The adapter instance is process-wide, so it is resolved once and only its
    // enabled flag is read afterwards.
    private val nfcAdapter: NfcAdapter? by lazy {
        NfcAdapter.getDefaultAdapter(context)
    }

    /**
     * `true` when the device has an NFC adapter and it is switched on.
     *
     * Devices without NFC hardware report `false` too: for the reader flow a
     * missing adapter and a disabled one both mean "cannot scan".
     */
    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true

    /**
     * Where the device carries its NFC antenna.
     *
     * Falls back to [NfcAntennaPosition.MIDDLE] when the platform does not say, and for foldables,
     * which measure their antenna against the unfolded back and would otherwise point at a spot the
     * user cannot see while the device is closed.
     */
    fun antennaPosition(): NfcAntennaPosition {
        val antennaInfo = nfcAdapter?.nfcAntennaInfo ?: return NfcAntennaPosition.MIDDLE
        if (antennaInfo.isDeviceFoldable) return NfcAntennaPosition.MIDDLE

        // The first antenna the platform lists is the reader location.
        val antenna = antennaInfo.availableNfcAntennas.firstOrNull()
            ?: return NfcAntennaPosition.MIDDLE

        return nfcAntennaPositionOf(
            locationYMillimeters = antenna.locationY,
            deviceHeightMillimeters = antennaInfo.deviceHeight,
        )
    }
}