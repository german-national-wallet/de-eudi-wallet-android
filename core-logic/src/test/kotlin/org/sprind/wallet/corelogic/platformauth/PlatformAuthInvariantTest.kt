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

package org.sprind.wallet.corelogic.platformauth

import android.app.KeyguardManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformAuthInvariantTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val keyguardManager: KeyguardManager = mockk()

    private val databaseFile: File by lazy { File(temporaryFolder.root, "EudiWalletDocumentManager.bin") }

    private val subject by lazy { PlatformAuthInvariant(keyguardManager, databaseFile) }

    private fun deviceSecure(secure: Boolean) {
        every { keyguardManager.isDeviceSecure } returns secure
    }

    private fun walletDataPresent(present: Boolean) {
        if (present) databaseFile.writeBytes(byteArrayOf(0)) else databaseFile.delete()
    }

    @Test
    fun `shouldWipe is true when data exists and device is not secure`() {
        walletDataPresent(present = true)
        deviceSecure(secure = false)
        assertTrue(subject.shouldWipe())
        assertFalse(subject.shouldWarn())
    }

    @Test
    fun `shouldWipe is false when data exists and device is secure`() {
        walletDataPresent(present = true)
        deviceSecure(secure = true)
        assertFalse(subject.shouldWipe())
        assertFalse(subject.shouldWarn())
    }

    @Test
    fun `shouldWarn is true when no data and device is not secure`() {
        walletDataPresent(present = false)
        deviceSecure(secure = false)
        assertFalse(subject.shouldWipe())
        assertTrue(subject.shouldWarn())
    }

    @Test
    fun `nothing to do when no data and device is secure`() {
        walletDataPresent(present = false)
        deviceSecure(secure = true)
        assertFalse(subject.shouldWipe())
        assertFalse(subject.shouldWarn())
    }

    @Test
    fun `an empty database file still counts as wallet data`() {
        databaseFile.createNewFile()
        deviceSecure(secure = false)
        assertTrue(subject.shouldWipe())
    }

    @Test
    fun `isDeviceSecure reflects the keyguard regardless of wallet data`() {
        deviceSecure(secure = true)
        walletDataPresent(present = true)
        assertTrue(subject.isDeviceSecure())
        walletDataPresent(present = false)
        assertTrue(subject.isDeviceSecure())

        deviceSecure(secure = false)
        assertFalse(subject.isDeviceSecure())
        walletDataPresent(present = true)
        assertFalse(subject.isDeviceSecure())
    }
}
