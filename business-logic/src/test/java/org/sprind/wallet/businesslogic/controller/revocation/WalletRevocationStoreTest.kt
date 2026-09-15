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

package org.sprind.wallet.businesslogic.controller.revocation

import eu.europa.ec.businesslogic.controller.storage.PrefsController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WalletRevocationStoreTest {

    @Mock
    private lateinit var prefsController: PrefsController

    private lateinit var closeable: AutoCloseable

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
    }

    private fun makeStore(revokedInPrefs: Boolean = false): WalletRevocationStore {
        whenever(prefsController.getBool(eq("wallet_revoked"), eq(false)))
            .thenReturn(revokedInPrefs)
        return WalletRevocationStoreImpl(prefsController)
    }

    @Test
    fun `revoked flag round-trips through preferences`() {
        val store = makeStore(revokedInPrefs = false)
        assertFalse(store.isRevoked())

        whenever(prefsController.getBool(eq("wallet_revoked"), eq(false))).thenReturn(true)
        assertTrue(store.isRevoked())
    }

    @Test
    fun `markRevoked writes with commit semantics so the writer may die right afterwards`() {
        makeStore().markRevoked()

        verify(prefsController).setBoolSync("wallet_revoked", true)
    }

    @Test
    fun `revokedFlow is seeded from preferences and follows mark and clear`() {
        val store = makeStore(revokedInPrefs = true)
        assertEquals(true, store.revokedFlow.value)

        store.clearRevoked()
        assertEquals(false, store.revokedFlow.value)

        store.markRevoked()
        assertEquals(true, store.revokedFlow.value)
    }

    @Test
    fun `clearRevoked removes the preference key`() {
        makeStore().clearRevoked()

        verify(prefsController).clear("wallet_revoked")
    }

    @Test
    fun `recheck-pending flag is independent of the revoked flag`() {
        val store = makeStore()

        store.markRecheckPending()
        verify(prefsController).setBoolSync("wallet_revocation_recheck_pending", true)

        store.clearRecheckPending()
        verify(prefsController).clear("wallet_revocation_recheck_pending")

        whenever(prefsController.getBool(eq("wallet_revocation_recheck_pending"), eq(false)))
            .thenReturn(true)
        assertTrue(store.isRecheckPending())
        assertFalse(store.isRevoked())
    }
}
