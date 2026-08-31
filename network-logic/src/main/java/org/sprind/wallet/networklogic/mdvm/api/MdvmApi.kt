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

package org.sprind.wallet.networklogic.mdvm.api

import eu.europa.ec.networklogic.di.WalletBackend
import okhttp3.OkHttpClient
import org.sprind.wallet.networklogic.common.HttpMessageSigningInterceptor
import org.sprind.wallet.networklogic.mdvm.model.MdvmApiResult
import org.sprind.wallet.networklogic.mdvm.model.request.MdvmRegisterRequest
import org.sprind.wallet.networklogic.mdvm.model.request.MdvmRenewalRequest
import org.sprind.wallet.networklogic.mdvm.model.response.MdvmChallengeResponse
import org.sprind.wallet.networklogic.mdvm.model.response.MdvmRegisterResponse
import org.sprind.wallet.networklogic.mdvm.model.response.MdvmRenewalResponse
import org.sprind.wallet.networklogic.mdvm.utils.toMdvmApiResult
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.security.PrivateKey
import kotlin.time.Clock

private const val HEADER_AUTH_CHALLENGE = "auth-challenge"
private const val HEADER_MDVI_WI_ID = "mdvm-wi-id"
private const val HEADER_SKIP_INTEGRITY_CHECKS = "skip-integrity-checks"

enum class MdvmSkipIntegrityChecksHeaderValue(val value: String) {
    /** Skip Play Integrity only. Default during the Play Integrity removal transition. */
    PLAY_INTEGRITY("play-integrity"),

    /** Skip key attestation. */
    KEY_ATTESTATION("key-attestation"),

    /** Skip all checks. Kept for backwards compatibility. */
    TRUE("true"),

    /** Skip all checks. Equivalent to [TRUE]. */
    ALL("all"),

    /** Do not skip checks. Kept for backwards compatibility and equivalent to omitting the header. */
    FALSE("false"),
    ;

    companion object {
        fun fromValue(value: String): MdvmSkipIntegrityChecksHeaderValue =
            entries.first { it.value == value }
    }
}

/**
 * WB endpoints relating to or protected by MDVM that can be
 * contacted without HTTP Message Signing.
 */
interface MdvmApi {
    @POST("/v1/mdvm/challenge")
    suspend fun challenge(
    ): Response<MdvmChallengeResponse>
}

/**
 * WB endpoints relating to or protected by MDVM that use
 * HTTP Message Signing to sign requests.
 */
interface SigningMdvmApi {
    companion object {
        internal val HEADERS = setOf(
            HEADER_AUTH_CHALLENGE,
            HEADER_MDVI_WI_ID,
            HEADER_SKIP_INTEGRITY_CHECKS,
        )
    }

    @POST("/v1/mdvm/android/register")
    suspend fun register(
        @Body request: MdvmRegisterRequest,
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_SKIP_INTEGRITY_CHECKS) skipIntegrityChecks: String,
    ): Response<MdvmRegisterResponse>
}

interface DoubleSigningMdvmApi {
    companion object {
        // As of June 2026-06, this happens to be identical to SigningMdvmApi.HEADERS, but this
        // might change if the endpoints change.s
        internal val HEADERS = setOf(
            HEADER_AUTH_CHALLENGE,
            HEADER_MDVI_WI_ID,
            HEADER_SKIP_INTEGRITY_CHECKS,
        )
    }
    @POST("/v1/mdvm/android/renewal")
    suspend fun renewal(
        @Body request: MdvmRenewalRequest,
        @Header(HEADER_MDVI_WI_ID) mdvmWiId: String,
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_SKIP_INTEGRITY_CHECKS) skipIntegrityChecks: String,
    ): Response<MdvmRenewalResponse>
}

/**
 * Provides access to [MdvmApi] endpoints.
 */
interface MdvmApiClient {
    suspend fun challenge(): MdvmApiResult<MdvmChallengeResponse>

    /**
     * Access to [SigningMdvmApi] endpoints.
     *
     * @param wiMdvmAuthPrvk The private key to use for HTTP Message Signing.
     */
    suspend fun signingApi(wiMdvmAuthPrvk: PrivateKey): SigningMdvmApiClient

    /**
     * Access to [DoubleSigningMdvmApi] endpoints.
     */
    suspend fun doubleSigningApi(
        wiMdvmAuthPrvk: PrivateKey,
        wiMdvmReattestPrvk: PrivateKey,
    ): DoubleSigningMdvmApiClient
}

/**
 * Provides access to [SigningMdvmApi] endpoints.
 */
interface SigningMdvmApiClient {
    suspend fun register(
        request: MdvmRegisterRequest,
        authChallenge: String,
        skipIntegrityChecks: MdvmSkipIntegrityChecksHeaderValue,
    ): MdvmApiResult<MdvmRegisterResponse>
}

interface DoubleSigningMdvmApiClient {
    suspend fun renewal(
        request: MdvmRenewalRequest,
        mdvmWiId: String,
        authChallenge: String,
        skipIntegrityChecks: MdvmSkipIntegrityChecksHeaderValue,
    ): MdvmApiResult<MdvmRenewalResponse>
}

/**
 * Implementation of [MdvmApiClient].
 *
 * @param api An [MdvmApi] instance for talking to Wallet Backend API that does not
 *            require HTTP Message Signing.
 * @param baseClient An [OkHttpClient] suitable for talking to [api] (w/o HTTP Message Signing).
 */
internal class MdvmApiClientImpl(
    private val api: MdvmApi,
    @WalletBackend private val baseClient:  OkHttpClient,
    private val baseRetrofit: Retrofit,
    private val clock: Clock,
) :
    MdvmApiClient
{
    override suspend fun challenge(): MdvmApiResult<MdvmChallengeResponse> {
        // "X-Auth-Token" header is implicitly added by baseHttpClient's HeaderInterceptor
        val response = api.challenge()
        return response.toMdvmApiResult()
    }

    private fun signingHttpClient(
        wiMdvmAuthPrvk: PrivateKey,
        headersToSign: Set<String>,
    ): OkHttpClient =
        // This reuses baseClient's interceptors for logging, X-Auth-Token header.
        // It also shares baseClient's connection pool, cache, timeout configuration, etc.
        baseClient.newBuilder()
            .addInterceptor(HttpMessageSigningInterceptor(
                signingKey = wiMdvmAuthPrvk,
                clock = clock,
                extraHeaderNamesToConsiderForSignature = headersToSign,
                signatureName = "mdvm-auth-sig",
                keyId = "wi-mdvm-auth-key",
            ))
            .build()

    override suspend fun signingApi(wiMdvmAuthPrvk: PrivateKey): SigningMdvmApiClient {
        val signingHttpClient = signingHttpClient(wiMdvmAuthPrvk, SigningMdvmApi.HEADERS)
        // register endpoint is on the same endpoint (same hostUrl, same GsonConverterFactory),
        // but need the HttpMessageSigningInterceptor in addition
        val signingRetrofit = baseRetrofit.newBuilder()
            .client(signingHttpClient)
            .build()

        val signingApi = signingRetrofit.create(SigningMdvmApi::class.java)
        return SigningMdvmApiClientImpl(signingApi)
    }

    override suspend fun doubleSigningApi(
        wiMdvmAuthPrvk: PrivateKey,
        wiMdvmReattestPrvk: PrivateKey
    ): DoubleSigningMdvmApiClient {
        val headersToSign = DoubleSigningMdvmApi.HEADERS
        val httpClient = signingHttpClient(wiMdvmAuthPrvk, headersToSign)
            .newBuilder()
            .addInterceptor(HttpMessageSigningInterceptor(
                signingKey = wiMdvmReattestPrvk,
                clock = clock,
                extraHeaderNamesToConsiderForSignature = headersToSign,
                signatureName = "mdvm-reattest-sig",
                keyId = "wi-mdvm-reattest-key",
            ))
            .build()
        val doubleSigningRetrofit = baseRetrofit.newBuilder()
            .client(httpClient)
            .build()
        val doubleSigningApi = doubleSigningRetrofit.create(DoubleSigningMdvmApi::class.java)
        return DoubleSigningMdvmApiClientImpl(api = doubleSigningApi)
    }
}

internal class SigningMdvmApiClientImpl(private val api: SigningMdvmApi) : SigningMdvmApiClient {
    override suspend fun register(
        request: MdvmRegisterRequest,
        authChallenge: String,
        skipIntegrityChecks: MdvmSkipIntegrityChecksHeaderValue
    ): MdvmApiResult<MdvmRegisterResponse> {
        // "X-Auth-Token" header is implicitly added by baseHttpClient's HeaderInterceptor
        val response = api.register(
            request = request,
            authChallenge = authChallenge,
            skipIntegrityChecks = skipIntegrityChecks.value,
        )
        return response.toMdvmApiResult()
    }
}

internal class DoubleSigningMdvmApiClientImpl(private val api: DoubleSigningMdvmApi): DoubleSigningMdvmApiClient {
    override suspend fun renewal(
        request: MdvmRenewalRequest,
        mdvmWiId: String,
        authChallenge: String,
        skipIntegrityChecks: MdvmSkipIntegrityChecksHeaderValue,
    ): MdvmApiResult<MdvmRenewalResponse> {
        // "X-Auth-Token" header is implicitly added by baseHttpClient's HeaderInterceptor
        val response = api.renewal(
            request = request,
            mdvmWiId = mdvmWiId,
            authChallenge = authChallenge,
            skipIntegrityChecks = skipIntegrityChecks.value,
        )
        return response.toMdvmApiResult()
    }
}