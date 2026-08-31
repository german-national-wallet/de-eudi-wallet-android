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

package org.sprind.wallet.networklogic.rwsca.api

import org.sprind.wallet.networklogic.common.HttpMessageSignatureSpec
import org.sprind.wallet.networklogic.common.HttpMessageSigningApiFactory
import org.sprind.wallet.networklogic.rwsca.model.RwscaApiResult
import org.sprind.wallet.networklogic.rwsca.model.request.RwscaCreateKeysRequest
import org.sprind.wallet.networklogic.rwsca.model.request.RwscaInitializePinAndStartPinSessionRequest
import org.sprind.wallet.networklogic.rwsca.model.request.RwscaSignDataRequest
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaChallengeResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaCreateKeysResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaInitializePinAndStartPinSessionResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaRegisterResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaSignDataResponse
import org.sprind.wallet.networklogic.rwsca.model.response.RwscaStartPinSessionResponse
import org.sprind.wallet.networklogic.rwsca.model.toRwscaApiResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST
import java.security.PrivateKey

private const val HEADER_AUTH_CHALLENGE = "auth-challenge"
private const val HEADER_MDVM_TOKEN = "mdvm-token"
private const val HEADER_RWSCA_ACCOUNT_ID = "rwsca-account-id"
private const val HEADER_RWSCA_PIN_SESSION_TOKEN = "rwsca-pin-session-token"

interface RwscaApi {
    @POST("/v1/rwsca/challenge")
    suspend fun challenge(
        // no parameters
    ): Response<RwscaChallengeResponse>
}

interface SigningRwscaApi {
    companion object {
        internal val HEADERS = setOf(
            HEADER_AUTH_CHALLENGE,
            HEADER_MDVM_TOKEN,
            HEADER_RWSCA_ACCOUNT_ID,
            HEADER_RWSCA_PIN_SESSION_TOKEN,
        )
    }

    @POST("/v1/rwsca/register")
    suspend fun register(
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
    ): Response<RwscaRegisterResponse>

    @POST("/v1/rwsca/createKeys")
    suspend fun createKeys(
        @Body request: RwscaCreateKeysRequest,
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
        @Header(HEADER_RWSCA_ACCOUNT_ID) rwscaAccountId: String,
    ): Response<RwscaCreateKeysResponse>

    @DELETE("/v1/rwsca/deleteAccount")
    suspend fun deleteAccount(
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
        @Header(HEADER_RWSCA_ACCOUNT_ID) rwscaAccountId: String,
    ): Response<Unit>

    @POST("/v1/rwsca/signData")
    suspend fun signData(
        @Body request: RwscaSignDataRequest,
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
        @Header(HEADER_RWSCA_ACCOUNT_ID) rwscaAccountId: String,
        @Header(HEADER_RWSCA_PIN_SESSION_TOKEN) rwscaPinSessionToken: String,
    ): Response<RwscaSignDataResponse>
}

interface DoubleSigningRwscaApi {
    companion object {
        internal val HEADERS = setOf(
            HEADER_RWSCA_ACCOUNT_ID,
            HEADER_AUTH_CHALLENGE,
            HEADER_MDVM_TOKEN,
        )
    }

    @POST("/v1/rwsca/initializePinAndStartPinSession")
    suspend fun initializePinAndStartPinSession(
        @Body request: RwscaInitializePinAndStartPinSessionRequest,
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
        @Header(value = HEADER_RWSCA_ACCOUNT_ID) rwscaAccountId: String,
    ): Response<RwscaInitializePinAndStartPinSessionResponse>

    @POST("/v1/rwsca/startPinSession")
    suspend fun startPinSession(
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
        @Header(value = HEADER_RWSCA_ACCOUNT_ID) rwscaAccountId: String,
    ): Response<RwscaStartPinSessionResponse>
}

interface RwscaApiClient {
    suspend fun challenge(): RwscaApiResult<RwscaChallengeResponse>

    /**
     * Access to [SigningRwscaApi] endpoints.
     *
     * @param mdvmAuthPrvk The private key to use for HTTP Message Signing.
     */
    fun signingApi(mdvmAuthPrvk: PrivateKey): SigningRwscaApiClient

    fun doubleSigningApi(mdvmAuthPrvk: PrivateKey, rwscaPinPrvk: PrivateKey): DoubleSigningRwscaApiClient
}

interface SigningRwscaApiClient {
    suspend fun register(
        authChallenge: String,
        mdvmToken: String,
    ): RwscaApiResult<RwscaRegisterResponse>

    suspend fun createKeys(
        request: RwscaCreateKeysRequest,
        authChallenge: String,
        mdvmToken: String,
        rwscaAccountId: String,
    ): RwscaApiResult<RwscaCreateKeysResponse>

    suspend fun deleteAccount(
        authChallenge: String,
        mdvmToken: String,
        rwscaAccountId: String,
    ): RwscaApiResult<Unit>

    suspend fun signData(
        request: RwscaSignDataRequest,
        authChallenge: String,
        mdvmToken: String,
        rwscaAccountId: String,
        rwscaPinSessionToken: String,
    ): RwscaApiResult<RwscaSignDataResponse>
}

interface DoubleSigningRwscaApiClient {
    suspend fun initializePinAndStartPinSession(
        request: RwscaInitializePinAndStartPinSessionRequest,
        authChallenge: String,
        mdvmToken: String,
        rwscaAccountId: String,
    ): RwscaApiResult<RwscaInitializePinAndStartPinSessionResponse>

    suspend fun startPinSession(
        authChallenge: String,
        mdvmToken: String,
        rwscaAccountId: String,
    ): RwscaApiResult<RwscaStartPinSessionResponse>

}

internal class RwscaApiClientImpl(
    private val api: RwscaApi,
    private val signingApiFactory: HttpMessageSigningApiFactory,
) : RwscaApiClient {
    override suspend fun challenge(): RwscaApiResult<RwscaChallengeResponse> {
        // "X-Auth-Token" header is implicitly added by baseHttpClient's HeaderInterceptor
        val response = api.challenge()
        return response.toRwscaApiResult()
    }

    override fun signingApi(mdvmAuthPrvk: PrivateKey): SigningRwscaApiClient {
        val signingApi = signingApiFactory.create(
            apiClass = SigningRwscaApi::class.java,
            headersToSign = SigningRwscaApi.HEADERS,
            rwscaAuthSignatureSpec(mdvmAuthPrvk),
        )
        return SigningRwscaApiClientImpl(signingApi)
    }

    override fun doubleSigningApi(mdvmAuthPrvk: PrivateKey, rwscaPinPrvk: PrivateKey): DoubleSigningRwscaApiClient {
        val doubleSigningApi = signingApiFactory.create(
            apiClass = DoubleSigningRwscaApi::class.java,
            headersToSign = DoubleSigningRwscaApi.HEADERS,
            rwscaAuthSignatureSpec(mdvmAuthPrvk),
            HttpMessageSignatureSpec(
                signingKey = rwscaPinPrvk,
                signatureName = "rwsca-pin-sig",
                keyId = "wi-rwsca-pin-key",
            ),
        )
        return DoubleSigningRwscaApiClientImpl(doubleSigningApi)
    }

    private fun rwscaAuthSignatureSpec(mdvmAuthPrvk: PrivateKey): HttpMessageSignatureSpec =
        HttpMessageSignatureSpec(
            signingKey = mdvmAuthPrvk,
            signatureName = "rwsca-auth-sig",
            keyId = "wi-mdvm-auth-key",
        )
}

internal class SigningRwscaApiClientImpl(private val api: SigningRwscaApi): SigningRwscaApiClient {
    override suspend fun register(
        authChallenge: String,
        mdvmToken: String,
    ): RwscaApiResult<RwscaRegisterResponse> =
        api.register(
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
        ).toRwscaApiResult()

    override suspend fun createKeys(
        request: RwscaCreateKeysRequest,
        authChallenge: String,
        mdvmToken: String,
        rwscaAccountId: String,
    ): RwscaApiResult<RwscaCreateKeysResponse> =
        api.createKeys(
            request = request,
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
            rwscaAccountId = rwscaAccountId,
        ).toRwscaApiResult()

    override suspend fun deleteAccount(
        authChallenge: String,
        mdvmToken: String,
        rwscaAccountId: String
    ): RwscaApiResult<Unit> =
        api.deleteAccount(
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
            rwscaAccountId = rwscaAccountId,
        ).toRwscaApiResult()

    override suspend fun signData(
        request: RwscaSignDataRequest,
        authChallenge: String,
        mdvmToken: String,
        rwscaAccountId: String,
        rwscaPinSessionToken: String,
    ): RwscaApiResult<RwscaSignDataResponse> =
        api.signData(
            request = request,
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
            rwscaAccountId = rwscaAccountId,
            rwscaPinSessionToken = rwscaPinSessionToken,
        ).toRwscaApiResult()
}

internal class DoubleSigningRwscaApiClientImpl(private val api: DoubleSigningRwscaApi): DoubleSigningRwscaApiClient {
    override suspend fun initializePinAndStartPinSession(
        request: RwscaInitializePinAndStartPinSessionRequest,
        authChallenge: String,
        mdvmToken: String,
        rwscaAccountId: String
    ): RwscaApiResult<RwscaInitializePinAndStartPinSessionResponse> =
        api.initializePinAndStartPinSession(
            request = request,
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
            rwscaAccountId = rwscaAccountId,
        ).toRwscaApiResult()

    override suspend fun startPinSession(
        authChallenge: String,
        mdvmToken: String,
        rwscaAccountId: String
    ): RwscaApiResult<RwscaStartPinSessionResponse> =
        api.startPinSession(
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
            rwscaAccountId = rwscaAccountId,
        ).toRwscaApiResult()
}
