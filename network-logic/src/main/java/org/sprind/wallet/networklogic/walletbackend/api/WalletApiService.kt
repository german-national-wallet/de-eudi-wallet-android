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

package org.sprind.wallet.networklogic.walletbackend.api

import org.sprind.wallet.networklogic.common.HttpMessageSignatureSpec
import org.sprind.wallet.networklogic.common.HttpMessageSigningApiFactory
import org.sprind.wallet.networklogic.walletbackend.model.WalletApiResult
import org.sprind.wallet.networklogic.walletbackend.model.request.AttestationRequest
import org.sprind.wallet.networklogic.walletbackend.model.attestation.WalletAttestationResponse
import org.sprind.wallet.networklogic.walletbackend.model.response.WalletChallengeResponse
import org.sprind.wallet.networklogic.walletbackend.model.response.WalletRegisterResponse
import org.sprind.wallet.networklogic.walletbackend.model.toWalletApiResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST
import java.security.PrivateKey

private const val HEADER_AUTH_CHALLENGE = "auth-challenge"
private const val HEADER_MDVM_TOKEN = "mdvm-token"
private const val HEADER_WPB_WI_ID = "wpb-wi-id"

interface WalletApi {
    @POST("/v1/wpb/challenge")
    suspend fun getChallenge(
    ): Response<WalletChallengeResponse>
}

interface SigningWalletApi {
    companion object {
        internal val HEADERS = setOf(
            HEADER_AUTH_CHALLENGE,
            HEADER_MDVM_TOKEN,
            HEADER_WPB_WI_ID,
        )
    }

    @POST("/v1/wpb/register")
    suspend fun register(
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
    ): Response<WalletRegisterResponse>

    @DELETE("/v1/wpb/deleteAccount")
    suspend fun deleteAccount(
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
        @Header(HEADER_WPB_WI_ID) walletInstanceId: String,
    ): Response<Unit>
}

interface DoubleSigningWalletApi {
    companion object {
        internal val HEADERS = setOf(
            HEADER_AUTH_CHALLENGE,
            HEADER_MDVM_TOKEN,
            HEADER_WPB_WI_ID,
        )
    }

    @POST("/v1/wpb/attestation")
    suspend fun generateAttestation(
        @Body request: AttestationRequest,
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
        @Header(HEADER_WPB_WI_ID) walletInstanceId: String,
    ): Response<WalletAttestationResponse>
}

interface WalletApiClient {
    suspend fun getChallenge(): WalletApiResult<WalletChallengeResponse>

    fun signingApi(mdvmAuthPrvk: PrivateKey): SigningWalletApiClient

    fun doubleSigningApi(mdvmAuthPrvk: PrivateKey, wiaPrvk: PrivateKey): DoubleSigningWalletApiClient
}

interface SigningWalletApiClient {
    suspend fun register(
        authChallenge: String,
        mdvmToken: String,
    ): WalletApiResult<WalletRegisterResponse>

    suspend fun deleteAccount(
        authChallenge: String,
        mdvmToken: String,
        walletInstanceId: String,
    ): WalletApiResult<Unit>
}

interface DoubleSigningWalletApiClient {
    suspend fun generateAttestation(
        request: AttestationRequest,
        authChallenge: String,
        mdvmToken: String,
        walletInstanceId: String,
    ): WalletApiResult<WalletAttestationResponse>
}

internal class WalletApiClientImpl(
    private val walletApiService: WalletApi,
    private val signingApiFactory: HttpMessageSigningApiFactory,
) : WalletApiClient {
    override suspend fun getChallenge(): WalletApiResult<WalletChallengeResponse> =
        walletApiService.getChallenge().toWalletApiResult()

    override fun signingApi(mdvmAuthPrvk: PrivateKey): SigningWalletApiClient {
        val signingApi = signingApiFactory.create(
            apiClass = SigningWalletApi::class.java,
            headersToSign = SigningWalletApi.HEADERS,
            wpbAuthSignatureSpec(mdvmAuthPrvk),
        )
        return SigningWalletApiClientImpl(signingApi)
    }

    override fun doubleSigningApi(mdvmAuthPrvk: PrivateKey, wiaPrvk: PrivateKey): DoubleSigningWalletApiClient {
        val doubleSigningApi = signingApiFactory.create(
            apiClass = DoubleSigningWalletApi::class.java,
            headersToSign = DoubleSigningWalletApi.HEADERS,
            wpbAuthSignatureSpec(mdvmAuthPrvk),
            HttpMessageSignatureSpec(
                signingKey = wiaPrvk,
                signatureName = "wpb-wia-sig",
                keyId = "wi-wia-key",
            ),
        )
        return DoubleSigningWalletApiClientImpl(doubleSigningApi)
    }

    private fun wpbAuthSignatureSpec(mdvmAuthPrvk: PrivateKey): HttpMessageSignatureSpec =
        HttpMessageSignatureSpec(
            signingKey = mdvmAuthPrvk,
            signatureName = "wpb-auth-sig",
            keyId = "wi-mdvm-auth-key",
        )
}

internal class SigningWalletApiClientImpl(private val api: SigningWalletApi) : SigningWalletApiClient {
    override suspend fun register(
        authChallenge: String,
        mdvmToken: String,
    ): WalletApiResult<WalletRegisterResponse> =
        api.register(
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
        ).toWalletApiResult()

    override suspend fun deleteAccount(
        authChallenge: String,
        mdvmToken: String,
        walletInstanceId: String,
    ): WalletApiResult<Unit> =
        api.deleteAccount(
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
            walletInstanceId = walletInstanceId,
        ).toWalletApiResult()
}

internal class DoubleSigningWalletApiClientImpl(private val api: DoubleSigningWalletApi) :
    DoubleSigningWalletApiClient {
    override suspend fun generateAttestation(
        request: AttestationRequest,
        authChallenge: String,
        mdvmToken: String,
        walletInstanceId: String,
    ): WalletApiResult<WalletAttestationResponse> =
        api.generateAttestation(
            request = request,
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
            walletInstanceId = walletInstanceId,
        ).toWalletApiResult()
}
