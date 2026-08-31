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
import com.authlete.hms.SignatureInputField
import com.authlete.hms.SignatureMetadata
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.toByteString
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.security.PrivateKey
import java.security.Signature
import java.util.TreeSet
import kotlin.time.Clock
import kotlin.time.toJavaInstant

private const val HEADER_CONTENT_DIGEST = "Content-Digest"
private const val HEADER_NAME_SIGNATURE_INPUT = "Signature-Input"
private const val HEADER_NAME_SIGNATURE = "Signature"

class HttpMessageSigningInterceptor(
    private val signingKey: PrivateKey,
    private val clock: Clock,
    private val extraHeaderNamesToConsiderForSignature: Set<String>,
    private val signatureName: String,
    private val keyId: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val extraHeaderNamesToConsiderForSignature = extraHeaderNamesToConsiderForSignature.map { it.lowercase() }

        val bodyBytes = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readByteArray()
        } ?: ByteArray(0)

        val digest = computeContentDigest(bodyBytes)

        val headers = request.headers.toMultimap().toMutableMap()
        headers[HEADER_CONTENT_DIGEST] = listOf(digest)

        val valueProvider = ComponentValueProvider()
        valueProvider.method = request.method
        valueProvider.targetUri = request.url.toString()
        valueProvider.headers = headers

        val signatureComponentIdentifiers = mutableListOf(
            ComponentIdentifier("@method"),
            ComponentIdentifier("@path"),
            ComponentIdentifier(HEADER_CONTENT_DIGEST.lowercase()),
        )
        val caseInsensitiveHeaderNames = TreeSet(CASE_INSENSITIVE_ORDER).apply {
            addAll(headers.keys)
        }
        for (headerName in extraHeaderNamesToConsiderForSignature) {
            if (caseInsensitiveHeaderNames.contains(headerName)) {
                signatureComponentIdentifiers.add(ComponentIdentifier(headerName))
            }
        }

        val signatureMetadata = SignatureMetadata(signatureComponentIdentifiers)

        signatureMetadata.parameters
            .setAlg("ecdsa-p256-sha256")
            .setKeyid(keyId)
            .setCreated(clock.now().toJavaInstant())

        // Build signature base
        val base = SignatureBaseBuilder(valueProvider).build(signatureMetadata)

        val signatureInput = SignatureInputField(mapOf(signatureName to signatureMetadata)).serialize()

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(signingKey)
        signer.update(base.serialize().encodeToByteArray())
        val signatureBase64 = signer.sign().toByteString().base64()

        val updatedRequest = request.newBuilder()
            .header(HEADER_CONTENT_DIGEST, digest)
            .addHeader(HEADER_NAME_SIGNATURE_INPUT, signatureInput)
            .addHeader(HEADER_NAME_SIGNATURE, "$signatureName=:$signatureBase64:")

        return chain.proceed(updatedRequest.build())
    }

    internal fun computeContentDigest(body: ByteArray): String {
        val base64 = body.toByteString().sha256().base64()
        return "sha-256=:$base64:"
    }
}