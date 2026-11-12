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

import okhttp3.Interceptor
import okhttp3.Response
import kotlin.time.Clock

private const val SERVER_HEADER = "Server"
private const val ENVOY_SERVER = "envoy"
private const val ENVOY_RATE_LIMITED_HEADER = "x-envoy-ratelimited"
private const val RETRY_AFTER_HEADER = "Retry-After"
private const val DATE_HEADER = "Date"

/**
 * Turns the HTTP responses that have a dedicated error screen into [HttpFailureException]s, so they
 * can be told apart from other failures whichever client made the request (the Wallet Backend
 * Retrofit APIs or the wallet-core Ktor client used for issuance and presentation):
 * - 408 becomes [HttpRequestTimeoutException];
 * - 429 becomes [HttpRateLimitedException], carrying when to retry from `Retry-After`.
 *
 * A 408 or 429 with a JSON body is passed through as well: it comes from a service that reports its
 * own error code, which the caller needs. For instance RWSCA answers a PIN attempt during the
 * backoff delay with a 429 `PIN_RETRY_BLOCKED` carrying `try_allowed_after`. The responses
 * converted here are the ones without such a body, mainly those of the Envoy gateway, which would
 * otherwise surface as an unknown backend error.
 *
 * Every other response is passed through untouched. Nothing is retried here: several requests
 * carry single-use challenges, so a retry has to restart the operation. Note that OkHttp itself
 * already retries a 408 once (unless it has a positive `Retry-After`) before this interceptor sees
 * it, which is safe: the server gave up before processing the request.
 *
 * Register it as an application interceptor inside `TraceContextInterceptor`, so the exception is
 * wrapped with the client trace id.
 */
class HttpFailureInterceptor(
    private val clock: Clock,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.hasJsonBody()) return response
        val failure = when (response.code) {
            HTTP_REQUEST_TIMEOUT -> HttpRequestTimeoutException(fromGateway = response.isFromGateway())
            HTTP_TOO_MANY_REQUESTS -> response.toRateLimitedException()
            else -> return response
        }
        response.close()
        throw failure
    }

    private fun Response.toRateLimitedException(): HttpRateLimitedException {
        val now = clock.now()
        val delay = parseRetryAfter(
            retryAfter = header(RETRY_AFTER_HEADER),
            date = header(DATE_HEADER),
            now = now,
        )
        return HttpRateLimitedException(
            retryAt = delay?.let { now + it },
            fromGateway = isFromGateway() || header(ENVOY_RATE_LIMITED_HEADER) != null,
        )
    }

    private fun Response.isFromGateway(): Boolean =
        header(SERVER_HEADER).equals(ENVOY_SERVER, ignoreCase = true)

    /** `application/json`, or any `+json` type such as `application/problem+json`. */
    private fun Response.hasJsonBody(): Boolean =
        body.contentType()?.subtype?.let { it == "json" || it.endsWith("+json") } == true
}
