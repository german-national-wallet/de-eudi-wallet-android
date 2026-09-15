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
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.eudi.wallet.document.Document
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PlatformAuthInvariantTest {

    private val keyguardManager: KeyguardManager = mockk()
    private val walletCoreDocumentsController: WalletCoreDocumentsController = mockk()
    private val document: Document = mockk()

    private val subject =
        PlatformAuthInvariant(keyguardManager, walletCoreDocumentsController)

    private fun deviceSecure(secure: Boolean) {
        every { keyguardManager.isDeviceSecure } returns secure
    }

    private fun documentsPresent(present: Boolean) {
        every { walletCoreDocumentsController.getAllDocuments() } returns
            if (present) listOf(document) else emptyList()
    }

    @Test
    fun `shouldWipe is true when data exists and device is not secure`() {
        documentsPresent(present = true)
        deviceSecure(secure = false)
        assertTrue(subject.shouldWipe())
    }

    @Test
    fun `shouldWipe is false when data exists and device is secure`() {
        documentsPresent(present = true)
        deviceSecure(secure = true)
        assertFalse(subject.shouldWipe())
    }

    @Test
    fun `shouldWipe is false when no data and device is not secure`() {
        documentsPresent(present = false)
        deviceSecure(secure = false)
        assertFalse(subject.shouldWipe())
    }

    @Test
    fun `shouldWipe is false when no data and device is secure`() {
        documentsPresent(present = false)
        deviceSecure(secure = true)
        assertFalse(subject.shouldWipe())
    }
}