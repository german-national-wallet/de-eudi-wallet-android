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

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.security.PrivateKey
import kotlin.time.Clock

internal data class HttpMessageSignatureSpec(
    val signingKey: PrivateKey,
    val signatureName: String,
    val keyId: String,
)

/**
 * Creates Retrofit API proxies that sign outgoing requests with RFC 9421 HTTP Message Signatures.
 *
 * Some wallet backend endpoints require one or more named signatures over the same request. The
 * factory keeps those signed clients close to the regular Retrofit setup by deriving from the
 * shared [baseClient] and [baseRetrofit], then adding one [HttpMessageSigningInterceptor] per
 * requested signature.
 *
 * Each [HttpMessageSignatureSpec] represents one signature component. The interceptor order is the
 * same as the vararg order, which matters when an endpoint requires multiple signatures because each
 * interceptor appends its own `Signature` and `Signature-Input` values to the request.
 *
 * [headersToSign] must contain the endpoint-specific headers, such as `auth-challenge`,
 * `mdvm-token`, or `content-digest`. The interceptor also signs the required pseudo components
 * (`@method` and `@path`).
 */
internal class HttpMessageSigningApiFactory(
    private val baseClient: OkHttpClient,
    private val baseRetrofit: Retrofit,
    private val clock: Clock,
) {
    /**
     * Builds an API implementation whose requests are signed with [signatures].
     */
    fun <T> create(
        apiClass: Class<T>,
        headersToSign: Set<String>,
        vararg signatures: HttpMessageSignatureSpec,
    ): T {
        val httpClient = signatures.fold(baseClient) { client, signature ->
            client.newBuilder()
                .addInterceptor(
                    HttpMessageSigningInterceptor(
                        signingKey = signature.signingKey,
                        clock = clock,
                        extraHeaderNamesToConsiderForSignature = headersToSign,
                        signatureName = signature.signatureName,
                        keyId = signature.keyId,
                    )
                )
                .build()
        }

        return baseRetrofit.newBuilder()
            .client(httpClient)
            .build()
            .create(apiClass)
    }
}
