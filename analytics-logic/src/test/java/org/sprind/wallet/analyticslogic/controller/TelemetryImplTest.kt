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

package org.sprind.wallet.analyticslogic.controller

import eu.europa.ec.businesslogic.controller.log.LogController
import org.sprind.wallet.businesslogic.util.SpanAttributes
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

private const val LOG_TAG = "TelemetryImpl"

class TelemetryImplTest {

    @RelaxedMockK
    private lateinit var mockRum: OpenTelemetryRum

    @RelaxedMockK
    private lateinit var mockLog: LogController

    @RelaxedMockK
    private lateinit var mockTracer: Tracer

    @RelaxedMockK
    private lateinit var mockSpanBuilder: SpanBuilder

    @RelaxedMockK
    private lateinit var mockSpan: Span

    @RelaxedMockK
    private lateinit var mockScope: Scope

    @RelaxedMockK
    private lateinit var mockLogger: Logger

    @RelaxedMockK
    private lateinit var mockLogRecordBuilder: LogRecordBuilder

    private lateinit var controller: Telemetry

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        // Mock the chain of calls to create a span
        every { mockRum.openTelemetry.tracerProvider.get(any()) } returns mockTracer
        every { mockTracer.spanBuilder(any()) } returns mockSpanBuilder
        every { mockSpanBuilder.setSpanKind(any()) } returns mockSpanBuilder
        every { mockSpanBuilder.setStartTimestamp(any<Long>(), any()) } returns mockSpanBuilder
        every { mockSpanBuilder.setAllAttributes(any()) } returns mockSpanBuilder
        every { mockSpanBuilder.startSpan() } returns mockSpan
        every { mockSpan.makeCurrent() } returns mockScope
        every { mockSpan.spanContext } returns mockk<SpanContext>(relaxed = true)

        // Mock the chain for event logging
        every { mockLogger.logRecordBuilder() } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.setEventName(any()) } returns mockLogRecordBuilder
        every { mockLogRecordBuilder.setAllAttributes(any()) } returns mockLogRecordBuilder


        controller = TelemetryImpl(mockRum, mockLog, mockLogger)
    }

    @Test
    fun `startSpan creates and stores a new span when none is active`() {
        // Given
        val spanName = "Presentation"

        // When
        controller.startSpan(spanName)

        // Then
        verify { mockTracer.spanBuilder(spanName) }
        verify { mockSpanBuilder.startSpan() }
        verify { mockSpan.makeCurrent() }
    }

    @Test
    fun `startSpan ends existing span and creates new one when called with same name`() {
        // Given
        val spanName = "Presentation"
        controller.startSpan(spanName)

        // When
        controller.startSpan(spanName)

        // Then — old span ended, new one created
        verify(exactly = 2) { mockSpanBuilder.startSpan() }
        verify { mockSpan.end(any<Long>(), any<TimeUnit>()) }
    }

    @Test
    fun `endSpan closes the correct span and scope`() {
        // Given
        val spanName = "Issuance"

        val spanAttributes = SpanAttributes.of(Pair("status", "success"))
        controller.startSpan(spanName)

        // When
        controller.endSpan( "Issuance",spanAttributes)

        // Then
        verify { mockScope.close() }
        verify { mockSpan.end(any<Long>(), any<TimeUnit>()) }
    }

    @Test
    fun `endSpan silently returns if no trace with that name is active`() {
        // When
        controller.endSpan("Issuance")

        // Then
        verify(exactly = 0) { mockSpan.end(any<Long>(), any<TimeUnit>()) }
    }

    @Test
    fun `logScreen emits a screen_view event with correct attributes`() {
        // Given
        val screenName = "HomeScreen"

        // When
        controller.logScreen(screenName)

        // Then
        val expectedAttributes = Attributes.of(TelemetryConstants.KEY_SCREEN_NAME, screenName)
        verify { mockLogRecordBuilder.setEventName("screen_view") }
        verify { mockLogRecordBuilder.setAllAttributes(expectedAttributes) }
        verify { mockLogRecordBuilder.emit() }
        verify { mockLog.d(eq(LOG_TAG), any()) }
    }

    @Test
    fun `logCustomEvent emits an event with given name and attributes`() {
        // Given
        val eventName = "button_click"
        val spanAttributes = SpanAttributes.of(Pair("item_id", "123"))

        // When
        controller.logEvent(eventName, spanAttributes)

        // Then
        verify { mockLogRecordBuilder.setEventName(eventName) }
        verify { mockLogRecordBuilder.emit() }
        verify { mockLog.d(eq(LOG_TAG), any()) }
    }

    @Test
    fun `getLogRecordBuilder returns an instance of LogRecordBuilder`() {
        // Given
        val eventName = "testEventName"

        // When
        controller.getLogRecordBuilder(eventName)

        // Then
        verify { mockLogRecordBuilder.setEventName(eventName) }
        verify { mockLogger.logRecordBuilder() }
        verify { mockLog.d(eq(LOG_TAG), any()) }
    }

    @Test
    fun `nextRequestTraceParent generates a w3c traceparent when no span is active`() {
        val traceParent = controller.nextRequestTraceParent()

        val parts = traceParent.headerValue.split("-")
        assertEquals(4, parts.size)
        assertEquals("00", parts[0])
        assertEquals(TRACE_ID_HEX_LENGTH, parts[1].length)
        assertEquals(SPAN_ID_HEX_LENGTH, parts[2].length)
        assertEquals("01", parts[3])
        assertEquals(traceParent.traceId, parts[1])
        assertTrue(traceParent.traceId.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `nextRequestTraceParent reuses the active span trace id so the backend correlates`() {
        every { mockSpan.spanContext } returns mockk<SpanContext>(relaxed = true) {
            every { isValid } returns true
            every { traceId } returns ACTIVE_TRACE_ID
        }
        controller.startSpan(spanName = "Issuance")

        assertEquals(ACTIVE_TRACE_ID, controller.nextRequestTraceParent().traceId)
    }

    @Test
    fun `currentTraceId returns the active span trace id while a span is active`() {
        every { mockSpan.spanContext } returns mockk<SpanContext>(relaxed = true) {
            every { isValid } returns true
            every { traceId } returns ACTIVE_TRACE_ID
        }
        controller.startSpan(spanName = "Issuance")

        assertEquals(ACTIVE_TRACE_ID, controller.currentTraceId())
    }

    @Test
    fun `currentTraceId falls back to the last propagated id once the span has ended`() {
        val propagated = controller.nextRequestTraceParent().traceId

        assertEquals(propagated, controller.currentTraceId())
    }

    @Test
    fun `currentTraceId is stable and never blank when nothing has happened yet`() {
        val first = controller.currentTraceId()

        assertEquals(TRACE_ID_HEX_LENGTH, first.length)
        assertEquals(first, controller.currentTraceId())
    }

    private companion object {
        const val TRACE_ID_HEX_LENGTH = 32
        const val SPAN_ID_HEX_LENGTH = 16
        const val ACTIVE_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
    }
}
