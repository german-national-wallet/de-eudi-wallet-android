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

package org.sprind.wallet.businesslogic.controller.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldWipeOnInitializeTest {

    private val cause = Exception("keystore")

    @Test
    fun `returns the check result when it succeeds`() {
        assertTrue(shouldWipeOnInitialize { true })
        assertFalse(shouldWipeOnInitialize { false })
    }

    @Test
    fun `wipes when the keyset is lost`() {
        assertTrue(shouldWipeOnInitialize { throw EncryptedPrefsUnavailableException.KeysetLost(cause) })
    }

    @Test
    fun `rethrows when the data is intact`() {
        listOf(
            EncryptedPrefsUnavailableException.Locked(cause),
            EncryptedPrefsUnavailableException.Transient(cause),
            EncryptedPrefsUnavailableException.InsecureDevice(cause),
        ).forEach { e ->
            assertSame(e, assertThrows(EncryptedPrefsUnavailableException::class.java) {
                shouldWipeOnInitialize { throw e }
            })
        }
    }

    @Test
    fun `rethrows unrelated failures`() {
        val e = IllegalStateException("bug")
        assertSame(e, assertThrows(IllegalStateException::class.java) {
            shouldWipeOnInitialize { throw e }
        })
    }
}
