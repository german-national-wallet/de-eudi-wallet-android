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

import org.junit.Test
import kotlin.test.assertEquals

class NfcAntennaPositionTest {

    @Test
    fun `an antenna high on the back reads as the top of the device`() {
        assertEquals(
            NfcAntennaPosition.TOP,
            nfcAntennaPositionOf(locationYMillimeters = 130, deviceHeightMillimeters = 150),
        )
    }

    @Test
    fun `an antenna halfway up the back reads as the middle of the device`() {
        assertEquals(
            NfcAntennaPosition.MIDDLE,
            nfcAntennaPositionOf(locationYMillimeters = 75, deviceHeightMillimeters = 150),
        )
    }

    @Test
    fun `an antenna low on the back reads as the bottom of the device`() {
        assertEquals(
            NfcAntennaPosition.BOTTOM,
            nfcAntennaPositionOf(locationYMillimeters = 20, deviceHeightMillimeters = 150),
        )
    }

    @Test
    fun `a device that reports no height keeps the middle position`() {
        assertEquals(
            NfcAntennaPosition.MIDDLE,
            nfcAntennaPositionOf(locationYMillimeters = 130, deviceHeightMillimeters = 0),
        )
    }
}