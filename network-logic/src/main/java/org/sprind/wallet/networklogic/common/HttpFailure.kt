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

import org.sprind.wallet.networklogic.utils.causeChain
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.time.Instant

internal const val HTTP_REQUEST_TIMEOUT = 408
internal const val HTTP_TOO_MANY_REQUESTS = 429

/** The message of the [InterruptedIOException] OkHttp throws when a call timeout expires. */
private const val OKHTTP_CALL_TIMEOUT_MESSAGE = "timeout"

/**
 * An HTTP response whose status code has a dedicated user-facing error, raised by
 * [HttpFailureInterceptor] instead of handing the response to the caller.
 *
 * It is an [IOException] so that it travels through OkHttp, Retrofit and Ktor like any other
 * transport failure and is picked up by the handling that already exists for those (it gets
 * wrapped in a `TracedIOException`, for instance).
 *
 * @property statusCode The HTTP status code of the response.
 * @property fromGateway `true` when the response looks like it was produced by the Envoy gateway
 *   rather than the service behind it. It is read from headers any server can send, relying
 *   parties included, so it is for diagnostics only and must not drive what the user is told.
 */
sealed class HttpFailureException(
    val statusCode: Int,
    val fromGateway: Boolean,
    message: String,
) : IOException(message)

/** HTTP 408: the server gave up waiting for the request (for Envoy, the body arrived too slowly). */
class HttpRequestTimeoutException(
    fromGateway: Boolean,
) : HttpFailureException(HTTP_REQUEST_TIMEOUT, fromGateway, "HTTP $HTTP_REQUEST_TIMEOUT Request Timeout")

/**
 * HTTP 429: a local or global rate limit was hit.
 *
 * @property retryAt When the request may be retried, on the device clock, or `null` when the
 *   response did not say (see [parseRetryAfter]).
 */
class HttpRateLimitedException(
    val retryAt: Instant?,
    fromGateway: Boolean,
) : HttpFailureException(HTTP_TOO_MANY_REQUESTS, fromGateway, "HTTP $HTTP_TOO_MANY_REQUESTS Too Many Requests")

/** The network failures that have their own error screen, independently of the flow they occur in. */
sealed interface NetworkFailure {
    /** The request took too long: a client-side timeout or HTTP 408. */
    data object Timeout : NetworkFailure

    /** HTTP 429. [retryAt] is when to retry on the device clock, or `null` if unknown. */
    data class RateLimited(val retryAt: Instant?) : NetworkFailure
}

/**
 * Returns the [NetworkFailure] this throwable represents, or `null` when it is some other failure.
 * The cause chain is walked because the failure is usually wrapped (in a `TracedIOException`, and
 * by wallet-core) before it reaches the caller.
 */
fun Throwable.toNetworkFailure(): NetworkFailure? =
    causeChain().firstNotNullOfOrNull { it.asNetworkFailure() }

private fun Throwable.asNetworkFailure(): NetworkFailure? = when {
    this is HttpRateLimitedException -> NetworkFailure.RateLimited(retryAt)
    this is HttpRequestTimeoutException -> NetworkFailure.Timeout
    this is SocketTimeoutException -> NetworkFailure.Timeout
    // OkHttp's call timeout (RealCall.timeoutExit) has no type of its own, only this message;
    // other InterruptedIOExceptions are cancellations. Recheck the message after an OkHttp bump.
    this is InterruptedIOException && message == OKHTTP_CALL_TIMEOUT_MESSAGE -> NetworkFailure.Timeout
    else -> null
}
