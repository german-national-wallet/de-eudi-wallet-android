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

package org.sprind.wallet.networklogic.common

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import org.sprind.wallet.networklogic.utils.causeChain
import java.net.ConnectException
import java.net.NoRouteToHostException

/** How many more times a request that could not connect is sent before it fails. */
const val CONNECTION_FAILURE_RETRIES = 2

private const val CONNECTION_FAILURE_RETRY_DELAY_MS = 500L

/**
 * The errno Android names in the message of the [ConnectException] it throws when the device has
 * no network at all (libcore's `IoBridge.connect` maps only `EHOSTUNREACH` and `EADDRNOTAVAIL` to
 * a [NoRouteToHostException]). Recheck after a platform bump.
 */
private const val NETWORK_UNREACHABLE_ERRNO = "ENETUNREACH"

/**
 * Whether this throwable, or one of its causes, is a failure to connect to the server. The cause
 * chain is walked because the failure is usually wrapped (in a `TracedIOException`, for instance).
 *
 * A failure because the device has no network ([isNetworkUnreachable]) is left out: it is not the
 * relying party's fault, and resending will not fix it.
 */
fun Throwable.isConnectionFailure(): Boolean =
    causeChain().any { it is ConnectException } && !isNetworkUnreachable()

/**
 * Whether this throwable, or one of its causes, says the device has no route to the network: a
 * [NoRouteToHostException], or the [ConnectException] Android throws for `ENETUNREACH`.
 */
fun Throwable.isNetworkUnreachable(): Boolean =
    causeChain().any {
        it is NoRouteToHostException || it.message?.contains(NETWORK_UNREACHABLE_ERRNO) == true
    }

/**
 * Sends a request that could not connect up to [CONNECTION_FAILURE_RETRIES] more times before it
 * fails, as the timeout design asks for a relying party's response endpoint (WD-5012).
 *
 * Only connection failures are retried, for every request on the client: such a request never
 * reached the server, so sending it again is safe even when it carries a single-use proof. Timeouts
 * and error responses are not retried, because the server may already have processed the request.
 */
fun HttpClientConfig<*>.retryConnectionFailures(
    delayMillis: Long = CONNECTION_FAILURE_RETRY_DELAY_MS,
) {
    install(HttpRequestRetry) {
        noRetry()
        retryOnExceptionIf(maxRetries = CONNECTION_FAILURE_RETRIES) { _, cause ->
            cause.isConnectionFailure()
        }
        constantDelay(millis = delayMillis, randomizationMs = 0)
    }
}
