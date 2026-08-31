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

package org.sprind.wallet.networklogic.trace

import java.io.IOException

/**
 * An [IOException] carrying the client-generated trace ID of the request that failed.
 *
 * When a request cannot reach the backend there is no response to read a trace ID from, so the ID
 * travels with the exception instead and can be read back with [traceId] where the error is turned
 * into something the user sees.
 *
 * It remains an [IOException] so that existing handling, such as mapping to a `NO_INTERNET` error
 * code, keeps working unchanged.
 */
class TracedIOException(
    val traceId: String,
    override val cause: IOException,
) : IOException(cause.message, cause)

/**
 * Returns the client-generated trace ID attached to this throwable, or `null` if it did not
 * originate from a traced network call. The cause chain is walked because the exception is
 * frequently wrapped again before it reaches the caller.
 */
fun Throwable.traceId(): String? {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        (current as? TracedIOException)?.let { return it.traceId }
        current = current.cause
    }
    return null
}