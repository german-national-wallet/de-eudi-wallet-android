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

import android.util.Log
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.businesslogic.util.RedactedKeys
import java.io.IOException

private val REQUEST_BODY_ATTRIBUTE: AttributeKey<String> =
    AttributeKey.stringKey("http_request_body")
private val RESPONSE_BODY_ATTRIBUTE: AttributeKey<String> =
    AttributeKey.stringKey("http_response_body")

@RunWith(JUnit4::class)
class HttpTelemetryInterceptorTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    lateinit var telemetry: Telemetry

    @MockK
    lateinit var logRecordBuilder: LogRecordBuilder

    @MockK(relaxed = true)
    lateinit var chain: Interceptor.Chain

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var interceptor: HttpTelemetryInterceptor

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        // Telemetry → no active parent span (tests focus on logging, not child span creation)
        every { telemetry.startChildSpan(any(), any()) } returns null

        // Telemetry → LogRecordBuilder
        every { telemetry.getLogRecordBuilder(any<String>()) } returns logRecordBuilder
        every {
            logRecordBuilder.setAttribute(any<AttributeKey<String>>(), any<String>())
        } returns logRecordBuilder
        every {
            logRecordBuilder.setAttribute(any<AttributeKey<Long>>(), any<Long>())
        } returns logRecordBuilder
        every { logRecordBuilder.setBody(any<String>()) } returns logRecordBuilder
        every { logRecordBuilder.emit() } just Runs

        interceptor = HttpTelemetryInterceptor(telemetry)

        server = MockWebServer()
        server.start()

        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @After
    fun tearDown() {
        server.close()
        unmockkStatic(Log::class)
        unmockkAll()
    }

    @Test
    fun `interceptor logs request and response with mockwebserver3`() {
        // given
        val responseBodyJson = """{"status":"ok"}"""
        val mockResponse = MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(responseBodyJson)
            .build()
        server.enqueue(mockResponse)

        val baseUrl = server.url("/api/test")

        val requestBodyJson = """{"foo":"bar"}"""
        val requestBody = requestBodyJson.toRequestBody(
            "application/json".toMediaType()
        )

        val request = Request.Builder()
            .url(baseUrl)
            .header("Content-Type", "application/json")
            .header(AUTH_TOKEN_HEADER, "super-secret") // must NOT be logged
            .post(requestBody)
            .build()

        // when
        val response = client.newCall(request).execute()

        // then: HTTP side
        assertEquals(200, response.code)

        val recorded = server.takeRequest()
        //assertEquals("/api/test", recorded.url)
        assertEquals("POST", recorded.method)
        assertEquals("application/json; charset=utf-8", recorded.headers["Content-Type"])
        // header still sent over the wire
        assertEquals("super-secret", recorded.headers[AUTH_TOKEN_HEADER])

        // then: telemetry side
        verify { telemetry.getLogRecordBuilder("http_client_call") }

        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.HTTP_REQUEST_METHOD,
                "POST"
            )
        }
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.SERVER_ADDRESS,
                baseUrl.host
            )
        }
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.URL_FULL,
                baseUrl.toString()
            )
        }

        // Request headers logged, sensitive ones redacted
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.httpRequestHeader("Content-Type"),
                "application/json"
            )
        }
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.httpRequestHeader(AUTH_TOKEN_HEADER),
                RedactedKeys.REDACTED
            )
        }
        verify(exactly = 0) {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.httpRequestHeader(AUTH_TOKEN_HEADER),
                "super-secret"
            )
        }

        // Bodies are never sent to telemetry
        verify(exactly = 0) {
            logRecordBuilder.setAttribute(REQUEST_BODY_ATTRIBUTE, any<String>())
        }
        verify(exactly = 0) {
            logRecordBuilder.setAttribute(RESPONSE_BODY_ATTRIBUTE, any<String>())
        }

        // Response attributes
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.HTTP_RESPONSE_STATUS_CODE,
                200L
            )
        }

        verify { logRecordBuilder.emit() }
    }

    @Test
    fun `interceptor strips the query string from the logged url`() {
        server.enqueue(MockResponse.Builder().code(200).build())

        val baseUrl = server.url("/callback?code=secret-code&state=abc")

        val request = Request.Builder()
            .url(baseUrl)
            .get()
            .build()

        client.newCall(request).execute()

        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.URL_FULL,
                match<String> { !it.contains("secret-code") && !it.contains("?") }
            )
        }
    }

    @Test
    fun `interceptor logs headers but no request body for GET`() {
        // given
        val mockResponse = MockResponse.Builder()
            .code(204)
            .addHeader("X-Test-Header", "value")
            .build()
        server.enqueue(mockResponse)

        val baseUrl = server.url("/no-body")

        val request = Request.Builder()
            .url(baseUrl)
            .header("Accept", "application/json")
            .get()
            .build()

        // when
        val response = client.newCall(request).execute()

        // then
        assertEquals(204, response.code)

        // Request method + URL logged
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.HTTP_REQUEST_METHOD,
                "GET"
            )
        }
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.URL_FULL,
                baseUrl.toString()
            )
        }

        // Header logged
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.httpRequestHeader("Accept"),
                "application/json"
            )
        }

        // No request body attribute should be set
        verify(exactly = 0) {
            logRecordBuilder.setAttribute(REQUEST_BODY_ATTRIBUTE, any<String>())
        }

        // Response header logged
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.httpResponseHeader("X-Test-Header"),
                "value"
            )
        }
    }


    @Test
    fun `interceptor logs exception when proceed throws and rethrows`() {
        // given: use mocked chain directly (no server)
        val request = Request.Builder()
            .url("https://example.org/failing")
            .get()
            .build()

        every { chain.request() } returns request
        every { chain.proceed(any()) } throws IOException("boom")

        // when
        try {
            interceptor.intercept(chain)
            Assert.fail("Expected IOException to be thrown")
        } catch (e: IOException) {
            // expected
            assertEquals("boom", e.message)
        }

        // then – exception log
        verify { telemetry.getLogRecordBuilder("http_client_exception") }

        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.HTTP_REQUEST_METHOD,
                "GET"
            )
        }
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.URL_FULL,
                "https://example.org/failing"
            )
        }
        verify {
            logRecordBuilder.setAttribute(
                HttpAttributesKeys.SERVER_ADDRESS,
                "example.org"
            )
        }

        verify {
            logRecordBuilder.setBody(
                match<String> { it.contains("Network request failed: boom") }
            )
        }

        verify { logRecordBuilder.emit() }
    }

}
