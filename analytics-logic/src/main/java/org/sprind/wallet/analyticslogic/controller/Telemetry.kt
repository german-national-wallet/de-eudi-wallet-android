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
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


object TelemetryConstants {
    /**
     * Attribute key for the screen name. It will be used when tagging a screen view event.
     */
    val KEY_SCREEN_NAME: AttributeKey<String> = AttributeKey.stringKey("screen.name")
    const val KEY_SCREEN_VIEW: String = "screen_view"
    const val SCOPE_DEFAULT: SpanName = "org.sprind.wallet"
    const val ISSUANCE: SpanName = "Issuance"
    const val PRESENTATION: SpanName = "Presentation"

}

/**
 * Type alias for a tracing scope, represented as a String.
 * This allows for both predefined and custom scope names.
 */
typealias SpanName = String
typealias ScreenName = String
typealias EventName = String

/**
 * A public handle for an active span that can be closed automatically.
 * It only exposes the `Span` itself, hiding the underlying `Scope`.
 */
interface SpanHandle : AutoCloseable {
    val span: Span
}

/**
 * The W3C trace context to propagate with an outgoing request.
 *
 * @property traceId The trace ID on its own, for reporting the request in an error.
 * @property headerValue The value for the `traceparent` request header.
 */
data class TraceParent(
    val traceId: String,
    val headerValue: String,
)

/**
 * Records analytics events
 *
 *
 * // Log screen view
 * telemetry.logScreen("HomeScreen", SpanAttributes.of(AttributeKey.stringKey("user.id") to "user123"))
 *
 * // Set up button click listener
 * findViewById<Button>(R.id.submitButton).setOnClickListener {
 *     telemetry.logEvent("submit_button_click", SpanAttributes.of(AttributeKey.stringKey("action") to "submit"))
 * }
 *
 * // Start a span for a process using a predefined scope
 * telemetry.startSpan(spanName= ISSUANCE).use { span ->
 *     // Simulate issuing a credential
 *     Thread.sleep(500)
 *     span.span.setAttribute(AttributeKey.stringKey("status"), "issued")
 * }
 *
 * Start a span with a custom, on-the-fly scope,
 * close until later state if this is not close, it will be close on termination of the app
 *
 * val spanHandle = telemetry.startSpan("MyCustomProcess")
 *
 */
interface Telemetry {
    /**
     * Logs a screen view event.
     * @param screenName The name of the screen being viewed.
     * @param attributes Additional metadata to attach to the event.
     */
    fun logScreen(screenName: String, attributes: SpanAttributes = SpanAttributes.EMPTY)

    /**
     * Logs a discrete custom event.
     * @param eventName The name of the event (e.g., "button_click").
     * @param attributes Additional metadata to attach to the event.
     */
    fun logEvent(eventName: String, attributes: SpanAttributes = SpanAttributes.EMPTY)

    /**
     * This method can start one Span at the time, it will not create a new span with the same name instead
     * it will return the active span with the given name
     * it doesn't chain spans or hold reference to the previous active span nor child spans,
     * it leverage this logic to the internal Otel library
     * @param spanName The name of the span.
     * @param initialAttributes Additional metadata to attach to the span.
     */
    fun startSpan(
        spanName: SpanName, initialAttributes: SpanAttributes = SpanAttributes.EMPTY
    ): SpanHandle

    /**
     *  This method can close a current span for the given name
     *  @param spanName The name of the span.
     *  @param finalAttributes Additional metadata to attach to the span.
     */
    fun endSpan(spanName: SpanName, finalAttributes: SpanAttributes = SpanAttributes.EMPTY)

    /**
     * Use this method to close all spans, use in the Application level onTerminate method
     * */
    fun closeSpans()

    /**
     * Retrieves a [LogRecordBuilder] for a custom event using the scope SpanScope.SCOPE_DEFAULT
     * @param eventName The name of the event (e.g., "http_request").
     * @return A [LogRecordBuilder]
     */
    fun getLogRecordBuilder(eventName: String): LogRecordBuilder

    /**
     * Returns the reference to the started (parent) span
     */
    fun getActiveParentSpan(): Span?

    /**
     * Creates a child span under the current active parent span.
     * Returns `null` if there is no active parent span.
     *
     * @param spanName The name of the child span.
     * @param spanKind The kind of span to create (defaults to [SpanKind.INTERNAL]).
     * @return The started child [Span], or `null` if no parent is active.
     */
    fun startChildSpan(spanName: String, spanKind: SpanKind = SpanKind.INTERNAL): Span?

    /**
     * Returns the trace context to propagate with an outgoing request, and remembers its trace ID
     * as the most recent one so that a failure can still be reported after the fact.
     *
     * The trace ID comes from the active parent span when there is one. Otherwise it is generated,
     * because spans are only started for some flows and a request made outside one would otherwise
     * have no ID to report.
     */
    fun nextRequestTraceParent(): TraceParent

    /**
     * Returns the trace ID that best identifies what the app is currently doing: the active parent
     * span's trace ID, otherwise the most recently propagated one.
     *
     * Never `null`, so an error shown to the user can always carry a value to quote to support.
     */
    fun currentTraceId(): String
}

internal class TelemetryImpl(
    private val rum: OpenTelemetryRum,
    private val logController: LogController,
    private val logger: Logger,
) : Telemetry {
    private val logTag = javaClass.simpleName
    private val tracer: Tracer =
        rum.openTelemetry.tracerProvider.get(TelemetryConstants.SCOPE_DEFAULT)

    /**
     * Reference to the started (parent) span
     */
    private var activeParentSpan: Span? = null

    /**
     * The most recently propagated trace ID, kept so that an error can still be reported with an ID
     * once the request that failed is over.
     */
    @Volatile
    private var lastTraceId: String = randomHex(TRACE_ID_HEX_LENGTH)

    /**
     * Internal class to hold instance of the active span to close it
     * */
    private data class ActiveSpan(val span: Span, val scope: Scope) {
        fun end(finalAttributes: SpanAttributes = SpanAttributes.EMPTY) {
            try {
                finalAttributes.forEach { (key, value) ->
                    span.setAttribute(key, value)
                }
            } finally {
                try {
                    scope.close()
                } finally {
                    span.end(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                }
            }
        }
    }

    private val activeSpans = ConcurrentHashMap<SpanName, ActiveSpan>()

    /**
     * An internal, concrete implementation of the [SpanHandle] interface.
     *
     * This class acts as a lightweight, publicly-facing handle for an [ActiveSpan] managed
     * within [TelemetryImpl]. Its primary responsibilities are:
     *
     * 1.  **Exposing the OpenTelemetry [Span]:** It provides access to the underlying `Span` object,
     *     allowing clients to add attributes or events to it directly.
     * 2.  **Enabling `AutoCloseable` functionality:** By implementing the [close] method, it allows
     *     the `Span` to be managed within a `try-with-resources` statement or a Kotlin `.use { ... }` block,
     *     ensuring that the span is properly ended.
     *
     * This handle does not contain the `Scope` object itself, thus hiding implementation details
     * from the consumer. When [close] is called, it delegates the closing logic back to
     * [TelemetryImpl.endSpan], using the `spanName` as the key to find and terminate the correct trace.
     *
     * @property span The underlying OpenTelemetry [Span] object that this handle represents.
     * @property spanName The unique name identifying the span. This is used as a key to
     *                    retrieve the corresponding [ActiveSpan] from the internal map for termination.
     */
    private inner class SpanHandleImpl(
        override val span: Span,
        private val spanName: SpanName
    ) : SpanHandle {
        override fun close() {
            endSpan(spanName)
        }
    }

    override fun startSpan(spanName: SpanName, initialAttributes: SpanAttributes): SpanHandle {
        activeSpans.remove(spanName)?.let { old ->
            old.end()
        }

        val spanBuilder = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL)
            .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)

        initialAttributes.forEach { (key, value) ->
            spanBuilder.setAttribute(key, value)
        }
        val span = spanBuilder.startSpan()
        val scope = span.makeCurrent()

        val activeSpan = ActiveSpan(span, scope)
        activeSpans[spanName] = activeSpan
        activeParentSpan = span

        return SpanHandleImpl(span, spanName)
    }

    override fun endSpan(spanName: SpanName, finalAttributes: SpanAttributes) {
        val activeSpan = activeSpans.remove(spanName) ?: return

        activeSpan.end(finalAttributes)
        activeParentSpan = null
    }

    override fun closeSpans() {
        if (activeSpans.isEmpty()) {
            return
        }

        activeSpans.keys.toList().forEach { spanName ->
            endSpan(spanName)
        }
    }

    override fun logScreen(screenName: ScreenName, attributes: SpanAttributes) {
        val attrBuilder = Attributes.builder()
        attributes.forEach { (key, value) ->
            attrBuilder.put(key, value)
        }
        val attrs = attrBuilder.put(TelemetryConstants.KEY_SCREEN_NAME, screenName).build()

        logger.logRecordBuilder().setEventName(TelemetryConstants.KEY_SCREEN_VIEW).setAllAttributes(attrs).emit()
        logController.d(logTag) { "Logged screen view: $screenName" }
    }

    override fun logEvent(eventName: EventName, attributes: SpanAttributes) {
        val eventBuilder = logger.logRecordBuilder().setEventName(eventName)
        attributes.forEach { (key, value) ->
            eventBuilder.setAttribute(key, value)
        }
        eventBuilder.emit()
        logController.d(logTag) { "Logged custom event: $eventName. with attributes: $attributes" }
    }

    override fun getLogRecordBuilder(eventName: String): LogRecordBuilder {
        logController.d(logTag) { "Logged custom event: $eventName." }
        return logger.logRecordBuilder().setEventName(eventName)
    }

    override fun getActiveParentSpan(): Span? = activeParentSpan

    override fun startChildSpan(spanName: String, spanKind: SpanKind): Span? {
        val parent = activeParentSpan ?: return null
        return tracer.spanBuilder(spanName)
            .setSpanKind(spanKind)
            .setParent(Context.root().with(parent))
            .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .startSpan()
    }

    override fun nextRequestTraceParent(): TraceParent {
        val traceId = (activeTraceId() ?: randomHex(TRACE_ID_HEX_LENGTH))
            .also { lastTraceId = it }

        val headerValue = listOf(
            TRACEPARENT_VERSION,
            traceId,
            randomHex(SPAN_ID_HEX_LENGTH),
            TRACEPARENT_FLAG_SAMPLED,
        ).joinToString(separator = "-")

        return TraceParent(traceId = traceId, headerValue = headerValue)
    }

    override fun currentTraceId(): String = activeTraceId() ?: lastTraceId

    private fun activeTraceId(): String? = activeParentSpan
        ?.spanContext
        ?.takeIf { it.isValid }
        ?.traceId

    private companion object {
        /** A W3C trace-id is 16 bytes, rendered as 32 lowercase hex characters. */
        const val TRACE_ID_HEX_LENGTH = 32

        /** A W3C span-id is 8 bytes, rendered as 16 lowercase hex characters. */
        const val SPAN_ID_HEX_LENGTH = 16

        const val TRACEPARENT_VERSION = "00"
        const val TRACEPARENT_FLAG_SAMPLED = "01"

        fun randomHex(length: Int): String = buildString(length) {
            while (this.length < length) {
                append(UUID.randomUUID().toString().replace("-", ""))
            }
        }.substring(0, length)
    }
}