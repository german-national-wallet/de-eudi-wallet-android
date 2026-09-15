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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sprind.wallet.networklogic.utils.getErrorCode
import java.io.IOException
import java.net.SocketTimeoutException

private const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"

class TracedIOExceptionTest {

    @Test
    fun `traceId is read from the exception itself`() {
        val exception = TracedIOException(traceId = TRACE_ID, cause = IOException("boom"))

        assertEquals(TRACE_ID, exception.traceId())
    }

    @Test
    fun `traceId is found when the exception has been wrapped again`() {
        val traced = TracedIOException(traceId = TRACE_ID, cause = IOException("boom"))
        val wrapped = IllegalStateException("interactor failed", RuntimeException("mapper", traced))

        assertEquals(TRACE_ID, wrapped.traceId())
    }

    @Test
    fun `traceId is null when no traced exception is in the cause chain`() {
        val exception = IllegalStateException("local failure", IOException("boom"))

        assertNull(exception.traceId())
    }

    @Test
    fun `traceId terminates on a circular cause chain`() {
        val first = IOException("first")
        val second = IOException("second")
        first.initCause(second)
        second.initCause(first)

        assertNull(first.traceId())
    }

    /**
     * The wrapper has to stay an [IOException] so that existing mapping of connectivity problems
     * keeps working.
     */
    @Test
    fun `remains an IOException so connectivity error mapping is unchanged`() {
        val traced = TracedIOException(
            traceId = TRACE_ID,
            cause = SocketTimeoutException("timeout"),
        )

        assertTrue(traced is IOException)
        assertEquals("NO_INTERNET", traced.getErrorCode())
    }
}