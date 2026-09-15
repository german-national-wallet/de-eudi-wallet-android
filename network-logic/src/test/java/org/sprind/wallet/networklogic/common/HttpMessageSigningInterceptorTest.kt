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

import com.authlete.hms.ComponentIdentifier
import com.authlete.hms.ComponentValueProvider
import com.authlete.hms.SignatureBaseBuilder
import com.authlete.hms.SignatureMetadata
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.Base64
import kotlin.time.toJavaInstant

private const val TEST_SIGNATURE_NAME = "test-signature-name"
private const val TEST_KEY_ID = "test-key-id"

class HttpMessageSigningInterceptorTest {

    // Arbitrary, known EC key pair so we can verify the signature in tests.
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    // Hard-coded so it's deterministic
    private val fixedInstant = Instant.fromEpochSeconds(1_234_567_890)
    private val fixedClock = object: Clock {
        override fun now(): Instant = fixedInstant
    }

    private lateinit var interceptor: HttpMessageSigningInterceptor
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val extraHeaderNamesToConsiderForSignature = setOf(
        "auth-challenge",
        "mDVm-tOken", // case should be ignored
        "non-existent-header", // absent headers should be ignored
        "skip-integrity-checks",
    )

    @Before
    fun setUp() {
        interceptor = HttpMessageSigningInterceptor(
            signingKey = keyPair.private,
            clock = fixedClock,
            extraHeaderNamesToConsiderForSignature = extraHeaderNamesToConsiderForSignature,
            TEST_SIGNATURE_NAME,
            TEST_KEY_ID,
        )
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `computeContentDigest - empty body returns sha256 digest with correct format`() {
        val result = interceptor.computeContentDigest(ByteArray(0))
        assertEquals("sha-256=:${utf8Sha256Base64("")}:", result)
    }

    @Test
    fun `computeContentDigest - known body returns correct sha256 digest value`() {
        val body = "hello world"
        val result = interceptor.computeContentDigest(body.toByteArray(Charsets.UTF_8))
        assertEquals("sha-256=:${utf8Sha256Base64(body)}:", result)
    }

    @Test
    fun `computeContentDigest - different bodies produce different digests`() {
        val d1 = interceptor.computeContentDigest("foo".toByteArray())
        val d2 = interceptor.computeContentDigest("bar".toByteArray())
        assertFalse(d1 == d2)
    }

    @Test
    fun `header presence - Content-Digest header is added to the outgoing request`() {
        enqueue200()
        executeDefaultRequest()
        assertNotNull(server.takeRequest().headers["Content-Digest"])
    }

    @Test
    fun `header presence - Signature-Input header is added to the outgoing request`() {
        enqueue200()
        executeDefaultRequest()
        assertNotNull(server.takeRequest().headers["Signature-Input"])
    }

    @Test
    fun `header presence - Signature header is added to the outgoing request`() {
        enqueue200()
        executeDefaultRequest()
        assertNotNull(server.takeRequest().headers["Signature"])
    }

    @Test
    fun `Content-Digest header - matches actual body bytes`() {
        val bodyJson = """{"foo":"bar"}"""
        enqueue200()
        executeDefaultRequest(bodyJson)
        val expected = "sha-256=:${utf8Sha256Base64(bodyJson)}:"
        assertEquals(expected, server.takeRequest().headers["Content-Digest"])
    }

    @Test
    fun `Content-Digest header - for request with no body is empty-body hash`() {
        enqueue200()
        client.newCall(Request.Builder().url(server.url("/test")).get().build()).execute().close()
        val expected = "sha-256=:${utf8Sha256Base64("")}:"
        assertEquals(expected, server.takeRequest().headers["Content-Digest"])
    }

    @Test
    fun `Signature-Input header - uses the expected signature name`() {
        enqueue200()
        executeDefaultRequest()
        assertTrue(server.takeRequest().headers["Signature-Input"]!!.startsWith("${TEST_SIGNATURE_NAME}="))
    }

    @Test
    fun `Signature-Input header - contains @method and @path derived components`() {
        enqueue200()
        executeDefaultRequest()
        val sigInput = server.takeRequest().headers["Signature-Input"]!!
        assertTrue(sigInput.contains("\"@method\""))
        assertTrue(sigInput.contains("\"@path\""))
    }

    @Test
    fun `Signature-Input header - contains Content-Digest header component`() {
        enqueue200()
        executeDefaultRequest()
        assertTrue(server.takeRequest().headers["Signature-Input"]!!.contains("\"content-digest\""))
    }

    @Test
    fun `Signature-Input header - specifies ecdsa-p256-sha256 as the algorithm`() {
        enqueue200()
        executeDefaultRequest()
        assertTrue(server.takeRequest().headers["Signature-Input"]!!.contains("alg=\"ecdsa-p256-sha256\""))
    }

    @Test
    fun `Signature-Input header - specifies the expected keyid`() {
        enqueue200()
        executeDefaultRequest()
        assertTrue(server.takeRequest().headers["Signature-Input"]!!.contains("keyid=\"$TEST_KEY_ID\""))
    }

    @Test
    fun `Signature-Input header - created parameter matches the injected clock`() {
        enqueue200()
        executeDefaultRequest()
        assertTrue(
            server.takeRequest().headers["Signature-Input"]!!
                .contains("created=${fixedInstant.toJavaInstant().epochSecond}")
        )
    }

    @Test
    fun `Signature header - is well-formed with the expected signature name`() {
        enqueue200()
        executeDefaultRequest()
        val sigHeader = server.takeRequest().headers["Signature"]!!

        assertTrue(sigHeader.startsWith("${TEST_SIGNATURE_NAME}=:"))
        assertTrue(sigHeader.endsWith(":"))

        val base64Part = sigHeader.removePrefix("${TEST_SIGNATURE_NAME}=:").removeSuffix(":")
        assertFalse(base64Part.isEmpty())
        // Decoding must succeed and produce non-empty bytes.
        val decoded = Base64.getDecoder().decode(base64Part)
        assertTrue(decoded.isNotEmpty())
    }

    /**
     * End-to-end test: reconstructs the signature base using the same authlete API calls the
     * interceptor uses, then verifies the ECDSA signature with the matching public key.
     *
     * This catches bugs in: body reading, header mapping, component selection, and signing.
     */
    @Test
    fun `Signature header - is a cryptographically valid ECDSA-P256-SHA256 signature`() {
        val bodyJson = """{"key":"value"}"""
        val url = server.url("/test")

        enqueue200()
        val request = Request.Builder()
            .url(url)
            .header("auth-challENGe", "test-challenge") // header names are case insensitive
            .header("mDVm-token", "test-token")
            .header("skip-integrity-checks", "false")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().close()

        val recorded = server.takeRequest()

        // Mirror exactly what the interceptor builds: original headers + Content-Digest.
        val digest = "sha-256=:${utf8Sha256Base64(bodyJson)}:"
        val headers = mutableMapOf(
            "autH-chAlLenge" to listOf("test-challenge"), // header names are case insensitive
            "mdvm-token" to listOf("test-token"),
            "skip-integrity-checks" to listOf("false"),
            "content-digest" to listOf(digest),
        )

        val valueProvider = ComponentValueProvider()
        valueProvider.method = "POST"
        valueProvider.targetUri = url.toString()
        valueProvider.headers = headers

        val componentIds = mutableListOf(
            ComponentIdentifier("@method"),
            ComponentIdentifier("@path"),
            ComponentIdentifier("content-digest"),
            ComponentIdentifier("auth-challenge"),
            ComponentIdentifier("mdvm-token"),
            ComponentIdentifier("skip-integrity-checks"),
        )

        val metadata = SignatureMetadata(componentIds)

        metadata.parameters
            .setAlg("ecdsa-p256-sha256")
            .setKeyid(TEST_KEY_ID)
            .setCreated(fixedInstant.toJavaInstant())

        val signatureBaseBytes = SignatureBaseBuilder(valueProvider).build(metadata)
            .serialize().encodeToByteArray()

        val sigHeader = recorded.headers["Signature"]!!
        val sigBytes = Base64.getDecoder().decode(
            sigHeader.removePrefix("${TEST_SIGNATURE_NAME}=:").removeSuffix(":")
        )

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(keyPair.public)
        verifier.update(signatureBaseBytes)
        assertTrue("Signature must be cryptographically valid", verifier.verify(sigBytes))
    }

    // helper functions

    private fun utf8Sha256Base64(s: String): String =
        s.toByteArray(Charsets.UTF_8).toByteString().sha256().base64()

    private fun enqueue200() {
        server.enqueue(MockResponse.Builder().code(200).build())
    }

    private fun executeDefaultRequest(bodyJson: String = """{"test":true}""") {
        val request = Request.Builder()
            .url(server.url("/test"))
            .header("auth-challenge", "test-challenge")
            .header("mdvm-token", "test-token")
            .header("skip-integrity-checks", "false")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().close()
    }
}
