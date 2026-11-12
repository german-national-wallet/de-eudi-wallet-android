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

package org.sprind.wallet.networklogic.connectivity

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AndroidConnectivityCheckerTest {

    private val network: Network = mock()

    @Test
    fun `is offline without a connectivity manager`() {
        assertFalse(AndroidConnectivityChecker(connectivityManager = null).isOnline())
    }

    @Test
    fun `is offline without an active network`() {
        val manager: ConnectivityManager = mock { on { activeNetwork } doReturn null }

        assertFalse(AndroidConnectivityChecker(manager).isOnline())
    }

    @Test
    fun `is offline when the active network has no capabilities`() {
        val manager: ConnectivityManager = mock {
            on { activeNetwork } doReturn network
            on { getNetworkCapabilities(network) } doReturn null
        }

        assertFalse(AndroidConnectivityChecker(manager).isOnline())
    }

    @Test
    fun `is offline when the active network has no internet capability`() {
        assertFalse(AndroidConnectivityChecker(managerWithInternet(false)).isOnline())
    }

    @Test
    fun `is online when the active network has the internet capability`() {
        assertTrue(AndroidConnectivityChecker(managerWithInternet(true)).isOnline())
    }

    private fun managerWithInternet(hasInternet: Boolean): ConnectivityManager {
        val capabilities: NetworkCapabilities = mock {
            on { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } doReturn hasInternet
        }
        return mock {
            on { activeNetwork } doReturn network
            on { getNetworkCapabilities(network) } doReturn capabilities
        }
    }
}
