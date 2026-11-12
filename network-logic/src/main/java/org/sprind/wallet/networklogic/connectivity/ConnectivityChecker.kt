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
import android.net.NetworkCapabilities

/** Asks the OS whether the device can currently reach the internet (WD-4685). */
fun interface ConnectivityChecker {

    /**
     * `false` when the OS reports no network with internet capability, e.g. Wi-Fi and mobile
     * data off or airplane mode. A `true` result does not guarantee that a request succeeds.
     */
    fun isOnline(): Boolean
}

/**
 * @param connectivityManager `null` when the system service is unavailable, in which case the
 *   device is reported offline rather than failing.
 */
internal class AndroidConnectivityChecker(
    private val connectivityManager: ConnectivityManager?,
) : ConnectivityChecker {

    override fun isOnline(): Boolean {
        val manager = connectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
