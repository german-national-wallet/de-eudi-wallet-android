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

package org.sprind.wallet.analyticslogic.interceptor

import org.sprind.wallet.analyticslogic.controller.Telemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.sprind.wallet.businesslogic.util.RedactedKeys
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Defines standard attribute keys for HTTP client and server telemetry as per OpenTelemetry conventions.
 *
 * These keys can be used to enrich spans and logs with HTTP-specific information.
 */
object HttpAttributesKeys {

    // Header Prefix
    private const val REQUEST_HEADER_PREFIX = "http_request_header_"
    private const val RESPONSE_HEADER_PREFIX = "http_response_header_"

    // Trace context
    val TRACE_ID: AttributeKey<String> = AttributeKey.stringKey("trace_id")
    val SPAN_ID: AttributeKey<String> = AttributeKey.stringKey("span_id")

    // Request attributes
    val HTTP_REQUEST_METHOD: AttributeKey<String> = AttributeKey.stringKey("http_request_method")
    val SERVER_ADDRESS: AttributeKey<String> = AttributeKey.stringKey("server_address")
    val URL_FULL: AttributeKey<String> = AttributeKey.stringKey("url_full")

    // Response attributes
    val HTTP_RESPONSE_STATUS_CODE: AttributeKey<Long> =
        AttributeKey.longKey("http_response_status_code")

    private val STANDARD_HEADER_REGEX = Regex("^[A-Za-z-]+$")

    /**
     * Normalizes an HTTP header name based on whether it follows standard
     * HTTP header naming conventions.
     *
     * Standard HTTP headers are composed only of alphabetic characters and
     * hyphens (e.g., "Content-Type", "X-Forwarded-For"). These are normalized
     * by converting them to lowercase and replacing hyphens with underscores.
     *
     * Custom or non-standard header names (e.g., "myHeader", "header.json",
     * "sdk-version1", "x_custom") contain characters outside the typical
     * A–Z / a–z / '-' pattern. These headers are returned **unchanged** to avoid
     * modifying application-specific or vendor-defined naming.
     *
     * Examples:
     *  - "Content-Type"   → "content_type"
     *  - "X-Request-Id"   → "x_request_id"
     *  - "myHeader"       → "myHeader"        (custom, preserved)
     *  - "header.json"    → "header.json"     (custom, preserved)
     *  - "sdk-version1"   → "sdk-version1"    (custom, preserved)
     *
     * @param key The original HTTP header name.
     * @return A normalized header key for standard headers, or the original name
     *         if the header is custom / non-standard.
     */
    private fun normalizeHeaderKey(key: String): String {
        return if (STANDARD_HEADER_REGEX.matches(key)) {
            key.lowercase().replace('-', '_')
        } else {
            key // Custom header → leave as is
        }
    }

    fun httpResponseHeader(headerName: String): AttributeKey<String> =
        AttributeKey.stringKey(RESPONSE_HEADER_PREFIX + normalizeHeaderKey(headerName))

    // In HttpAttributes object
    fun httpRequestHeader(headerName: String): AttributeKey<String> =
        AttributeKey.stringKey(REQUEST_HEADER_PREFIX + normalizeHeaderKey(headerName))
}

internal fun HttpUrl.withoutQueryOrFragment(): String =
    newBuilder().query(null).fragment(null).build().toString()

private const val MAX_BODY_SIZE = 5120 // 5 KB limit to avoid logging excessively large bodies
const val AUTH_TOKEN_HEADER = "X-Auth-Token"

/**
 * An OkHttp interceptor that logs request and response details to the configured
 * OpenTelemetry Logs provider.
 *
 * @param telemetry The controller used to build and emit log records.
 */
class HttpTelemetryInterceptor(private val telemetry: Telemetry) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Span name format: "METHOD host/path", example. "POST example.com/api/test"
        val spanName = "${request.method} ${request.url.host}${request.url.encodedPath.ifEmpty { "/" }}"
        val span = telemetry.startChildSpan(spanName, SpanKind.CLIENT)

        try {
            val tracedRequest = if (span != null) {
                val sc = span.spanContext
                val traceparent = "00-${sc.traceId}-${sc.spanId}-${if (sc.isSampled) "01" else "00"}"
                request.newBuilder()
                    .header("traceparent", traceparent)
                    .build()
            } else {
                request
            }

            val response: Response
            try {
                response = chain.proceed(tracedRequest)
            } catch (e: IOException) {
                logNetworkException(request, e)
                span?.setStatus(StatusCode.ERROR, e.message ?: "Network error")
                span?.recordException(e)
                throw e
            }

            val logBuilder = telemetry.getLogRecordBuilder(eventName = "http_client_call")
            span?.let {
                logBuilder.setAttribute(HttpAttributesKeys.TRACE_ID, it.spanContext.traceId)
                logBuilder.setAttribute(HttpAttributesKeys.SPAN_ID, it.spanContext.spanId)
            }
            logRequest(logBuilder, request)
            logResponse(logBuilder, response)
            logBuilder.emit()

            span?.setAttribute(HttpAttributesKeys.HTTP_REQUEST_METHOD, request.method)
            span?.setAttribute(HttpAttributesKeys.URL_FULL, request.url.withoutQueryOrFragment())
            span?.setAttribute(HttpAttributesKeys.HTTP_RESPONSE_STATUS_CODE, response.code.toLong())
            if (response.code >= 400) {
                span?.setStatus(StatusCode.ERROR, "HTTP ${response.code}")
            }

            return response
        } finally {
            span?.end(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        }
    }

    private fun logRequest(logBuilder: LogRecordBuilder, request: Request) {
        logBuilder.setAttribute(HttpAttributesKeys.HTTP_REQUEST_METHOD, request.method)
        logBuilder.setAttribute(HttpAttributesKeys.URL_FULL, request.url.withoutQueryOrFragment())
        logBuilder.setAttribute(HttpAttributesKeys.SERVER_ADDRESS, request.url.host)

        logHeaders(logBuilder, request.headers, HttpAttributesKeys::httpRequestHeader)
    }

    private fun logResponse(logBuilder: LogRecordBuilder, response: Response) {
        logBuilder.setAttribute(
            HttpAttributesKeys.HTTP_RESPONSE_STATUS_CODE,
            response.code.toLong()
        )
        logHeaders(logBuilder, response.headers, HttpAttributesKeys::httpResponseHeader)
    }

    /**
     * Logs HTTP headers to a {@link LogRecordBuilder} while filtering sensitive information.
     *
     * This function iterates over a {@link Headers} collection, transforming each header into
     * an OpenTelemetry attribute. It uses the provided {@code attributeKeyFactory} to generate
     * a unique {@link AttributeKey} for each header name, allowing distinction between request
     * and response headers.
     *
     * Headers named in {@link RedactedKeys} have their value replaced with a placeholder, so the
     * header is still visible in telemetry but its value never leaves the device.
     *
     * @param logBuilder The OpenTelemetry {@link LogRecordBuilder} to which the header
     *                   attributes will be added.
     * @param headers The OkHttp {@link Headers} object containing the HTTP headers to log.
     * @param attributeKeyFactory A higher-order function that takes a header name {@code String}
     *                            and returns the corresponding {@code AttributeKey<String>} for
     *                            logging. This allows the caller to control the attribute naming
     *                            convention (e.g., prefixing with "http_request_header_").
     *                            Current factory exist in {@link HttpAttributesKeys::httpResponseHeader}
     *
     * @sample
     * // Usage with the logHeaders function
     * logHeaders(logBuilder, response.headers, HttpAttributesKeys::httpResponseHeader)
     */
    private fun logHeaders(
        logBuilder: LogRecordBuilder,
        headers: Headers,
        attributeKeyFactory: (String) -> AttributeKey<String>
    ) {
        headers.forEach { (headerName, value) ->
            val logged = if (RedactedKeys.isRedacted(headerName)) RedactedKeys.REDACTED else value
            logBuilder.setAttribute(attributeKeyFactory(headerName), logged)
        }
    }

    private fun logNetworkException(request: Request, e: IOException) {
        val body =
            "Network request failed: ${e.message}\n${
                e.stackTraceToString().take(MAX_BODY_SIZE)
            }"
        telemetry.getLogRecordBuilder(eventName = "http_client_exception")
            .setAttribute(HttpAttributesKeys.HTTP_REQUEST_METHOD, request.method)
            .setAttribute(HttpAttributesKeys.URL_FULL, request.url.withoutQueryOrFragment())
            .setAttribute(HttpAttributesKeys.SERVER_ADDRESS, request.url.host)
            .setBody(
                body
            )
            .emit()
    }
}
