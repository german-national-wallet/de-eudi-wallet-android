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

package org.sprind.wallet.businesslogic.extensions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 *
 * This function captures exceptions of type [TimeoutCancellationException] and [Throwable],
 * returning them as a failure result. If a [CancellationException] is caught, it is rethrown
 * without wrapping it in a `Result`. This prevents unwanted interference with the cancellation
 * handling of Coroutines.
 * Reference: https://github.com/Kotlin/kotlinx.coroutines/issues/1814#issuecomment-1027931634
 *
 * @param block The suspending block of code to execute.
 * @return A [Result] containing the outcome of the block execution.
 *         If the block completes successfully, the result will be [Result.success].
 *         If an exception is thrown, it will be wrapped in [Result.failure]
 *         unless it is a [CancellationException], in which case it will be rethrown.
 */
inline fun <R> runSuspendCatching(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (t: TimeoutCancellationException) {
        Result.failure(t)
    } catch (c: CancellationException) {
        throw c
    } catch (e: Throwable) {
        Result.failure(e)
    }
}