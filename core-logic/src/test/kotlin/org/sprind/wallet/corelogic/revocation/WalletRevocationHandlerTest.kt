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

package org.sprind.wallet.corelogic.revocation

import eu.europa.ec.authenticationlogic.controller.storage.HardwareKeyStorageController
import eu.europa.ec.authenticationlogic.controller.storage.WalletPinUnBlockTimeStorageController
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.corelogic.controller.DeleteAllDocumentsPartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.testlogic.extension.runTest
import eu.europa.ec.testlogic.rule.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sprind.wallet.authenticationlogic.controller.mdvm.MdvmKeyManager
import org.sprind.wallet.authenticationlogic.controller.storage.MdvmRegistrationStorageController
import org.sprind.wallet.authenticationlogic.provider.RwscaStorageController
import org.sprind.wallet.businesslogic.controller.revocation.WalletRevocationStore

class WalletRevocationHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock private lateinit var store: WalletRevocationStore
    @Mock private lateinit var notificationManager: RevocationNotificationManager
    @Mock private lateinit var documentsController: WalletCoreDocumentsController
    @Mock private lateinit var hardwareKeyStorageController: HardwareKeyStorageController
    @Mock private lateinit var mdvmKeyManager: MdvmKeyManager
    @Mock private lateinit var mdvmRegistrationStorageController: MdvmRegistrationStorageController
    @Mock private lateinit var rwscaStorageController: RwscaStorageController
    @Mock private lateinit var walletPinUnBlockTimeStorageController: WalletPinUnBlockTimeStorageController
    @Mock private lateinit var logController: LogController

    private lateinit var handler: WalletRevocationHandler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(documentsController.wipeAllDocuments())
            .thenReturn(flowOf(DeleteAllDocumentsPartialState.Success))
        handler = WalletRevocationHandlerImpl(
            store = store,
            notificationManager = notificationManager,
            documentsController = documentsController,
            hardwareKeyStorageController = hardwareKeyStorageController,
            mdvmKeyManager = mdvmKeyManager,
            mdvmRegistrationStorageController = mdvmRegistrationStorageController,
            rwscaStorageController = rwscaStorageController,
            walletPinUnBlockTimeStorageController = walletPinUnBlockTimeStorageController,
            logController = logController,
        )
    }

    @Test
    fun `lockWallet persists the flag, then notifies, then wipes`() = coroutineRule.runTest {
        whenever(store.isRevoked()).thenReturn(false)

        handler.lockWallet()

        inOrder(store, notificationManager, documentsController) {
            verify(store).markRevoked()
            verify(notificationManager).postRevocationNotification()
            verify(documentsController).wipeAllDocuments()
        }
        verify(hardwareKeyStorageController).removeWalletRegistrationIds()
        verify(mdvmKeyManager).deleteAuthKeys()
        verify(mdvmKeyManager).deleteReattestKeys()
        verify(mdvmRegistrationStorageController).clearMdvmRegistration()
        verify(rwscaStorageController).clearRwscaRegistration()
        verify(rwscaStorageController).clearPinInitialized()
        verify(rwscaStorageController).clearPinSalt()
        verify(walletPinUnBlockTimeStorageController).clear()
    }

    @Test
    fun `lockWallet is idempotent when the wallet is already revoked`() = coroutineRule.runTest {
        whenever(store.isRevoked()).thenReturn(true)

        handler.lockWallet()

        verify(store, never()).markRevoked()
        verify(notificationManager, never()).postRevocationNotification()
        verify(documentsController, never()).wipeAllDocuments()
    }

    @Test
    fun `a failing notification does not stop the wipe`() = coroutineRule.runTest {
        whenever(store.isRevoked()).thenReturn(false)
        whenever(notificationManager.postRevocationNotification())
            .thenAnswer { throw SecurityException("notifications denied") }

        handler.lockWallet()

        verify(documentsController).wipeAllDocuments()
        verify(hardwareKeyStorageController).removeWalletRegistrationIds()
        verify(logController).e(eq("WalletRevocation"), any<Throwable>())
    }

    @Test
    fun `a failing wipe step does not stop the remaining wipe steps`() = coroutineRule.runTest {
        whenever(store.isRevoked()).thenReturn(false)
        whenever(hardwareKeyStorageController.removeWalletRegistrationIds())
            .thenAnswer { throw IllegalStateException("keystore unavailable") }
        whenever(rwscaStorageController.clearRwscaRegistration())
            .thenAnswer { throw IllegalStateException("prefs unavailable") }

        handler.lockWallet()

        verify(mdvmKeyManager).deleteAuthKeys()
        verify(mdvmRegistrationStorageController).clearMdvmRegistration()
        verify(rwscaStorageController).clearPinSalt()
        verify(walletPinUnBlockTimeStorageController).clear()
    }

    @Test
    fun `resetAfterRevocation re-wipes, runs beforeUnlock, and clears the revoked flag last`() =
        coroutineRule.runTest {
            var beforeUnlockRan = false

            val unlocked = handler.resetAfterRevocation {
                // The revoked flag must still be set while this runs.
                verify(store, never()).clearRevoked()
                beforeUnlockRan = true
            }

            assertTrue(unlocked)
            assertTrue(beforeUnlockRan)
            inOrder(documentsController, store) {
                verify(documentsController).wipeAllDocuments()
                verify(store).clearRecheckPending()
                verify(store).clearRevoked()
            }
            verify(hardwareKeyStorageController).removeWalletRegistrationIds()
        }

    @Test
    fun `a throwing beforeUnlock keeps the wallet locked`() = coroutineRule.runTest {
        val result = runCatching {
            handler.resetAfterRevocation { throw IllegalStateException("prefs write failed") }
        }

        assertTrue(result.isFailure)
        verify(store, never()).clearRevoked()
    }

    @Test
    fun `a failed document wipe aborts the reset and keeps the wallet locked`() =
        coroutineRule.runTest {
            whenever(documentsController.wipeAllDocuments())
                .thenReturn(flowOf(DeleteAllDocumentsPartialState.Failure("db locked")))

            val unlocked = handler.resetAfterRevocation()

            assertFalse(unlocked)
            verify(store, never()).clearRevoked()
        }

    @Test
    fun `a failed document wipe still locks the wallet and logs the failure`() =
        coroutineRule.runTest {
            whenever(store.isRevoked()).thenReturn(false)
            whenever(documentsController.wipeAllDocuments())
                .thenReturn(flowOf(DeleteAllDocumentsPartialState.Failure("db locked")))

            handler.lockWallet()

            verify(store).markRevoked()
            verify(logController, atLeastOnce()).e(eq("WalletRevocation"), any<() -> String>())
        }
}
