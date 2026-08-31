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

package org.sprind.wallet.cardreaderfeature.domain

enum class NfcAntennaPosition {
    TOP,
    MIDDLE,
    BOTTOM,
}

/**
 * The third of the device back a reported antenna location falls into.
 *
 * Platform antenna coordinates are measured in millimetres from the bottom left of the device in
 * its natural orientation, so a large [locationYMillimeters] is near the top of the back.
 */
fun nfcAntennaPositionOf(
    locationYMillimeters: Int,
    deviceHeightMillimeters: Int,
): NfcAntennaPosition {
    if (deviceHeightMillimeters <= 0) return NfcAntennaPosition.MIDDLE

    val shareFromBottom = locationYMillimeters.toFloat() / deviceHeightMillimeters
    return when {
        shareFromBottom >= UPPER_THIRD -> NfcAntennaPosition.TOP
        shareFromBottom <= LOWER_THIRD -> NfcAntennaPosition.BOTTOM
        else -> NfcAntennaPosition.MIDDLE
    }
}

private const val LOWER_THIRD = 1f / 3
private const val UPPER_THIRD = 2f / 3