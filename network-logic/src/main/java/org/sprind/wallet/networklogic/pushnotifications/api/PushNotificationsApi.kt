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

package org.sprind.wallet.networklogic.pushnotifications.api

import org.sprind.wallet.networklogic.common.HttpMessageSignatureSpec
import org.sprind.wallet.networklogic.common.HttpMessageSigningApiFactory
import org.sprind.wallet.networklogic.pushnotifications.model.PushNotificationsApiResult
import org.sprind.wallet.networklogic.pushnotifications.model.PushNotificationsChallengeResponse
import org.sprind.wallet.networklogic.pushnotifications.model.PushNotificationsRegisterRequest
import org.sprind.wallet.networklogic.pushnotifications.model.toPushNotificationsApiResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST
import java.security.PrivateKey

private const val HEADER_AUTH_CHALLENGE = "auth-challenge"
private const val HEADER_MDVM_TOKEN = "mdvm-token"

/**
 * Retrofit interface for the unsigned push notifications challenge endpoint.
 */
interface PushNotificationsApi {
    /** Requests a push notifications auth challenge JWT from the backend. */
    @POST("/v1/pns/challenge")
    suspend fun getChallenge(): Response<PushNotificationsChallengeResponse>
}

/**
 * Retrofit interface for push notifications endpoints that require HTTP Message Signatures.
 */
interface SigningPushNotificationsApi {
    companion object {
        internal val HEADERS = setOf(
            HEADER_AUTH_CHALLENGE,
            HEADER_MDVM_TOKEN,
        )
    }

    /** Registers an FCM token with the push notifications backend. */
    @POST("/v1/pns/register")
    suspend fun register(
        @Body request: PushNotificationsRegisterRequest,
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
    ): Response<Unit>

    /** Deletes the current push notifications registration from the backend. */
    @DELETE("/v1/pns/delete")
    suspend fun delete(
        @Header(HEADER_AUTH_CHALLENGE) authChallenge: String,
        @Header(HEADER_MDVM_TOKEN) mdvmToken: String,
    ): Response<Unit>
}

/**
 * Client facade for the push notifications backend API.
 */
interface PushNotificationsApiClient {
    /** Retrieves a push notifications auth challenge JWT (unsigned request). */
    suspend fun getChallenge(): PushNotificationsApiResult<PushNotificationsChallengeResponse>

    /**
     * Returns a signing client that signs requests with the given MDVM auth private key.
     *
     * @param mdvmAuthPrvk The MDVM auth private key used for HTTP Message Signatures.
     */
    fun signingApi(mdvmAuthPrvk: PrivateKey): SigningPushNotificationsApiClient
}

/**
 * Client facade for signed push notifications endpoints (register, delete).
 */
interface SigningPushNotificationsApiClient {
    /**
     * Registers an FCM token with the push notifications backend.
     *
     * @param mppRegistrationToken The FCM registration token to register.
     * @param authChallenge The push notifications auth challenge JWT.
     * @param mdvmToken The current MDVM token.
     */
    suspend fun register(
        mppRegistrationToken: String,
        authChallenge: String,
        mdvmToken: String,
    ): PushNotificationsApiResult<Unit>

    /**
     * Deletes the current push notifications registration from the backend.
     *
     * @param authChallenge The push notifications auth challenge JWT.
     * @param mdvmToken The current MDVM token.
     */
    suspend fun delete(
        authChallenge: String,
        mdvmToken: String,
    ): PushNotificationsApiResult<Unit>
}

internal class PushNotificationsApiClientImpl(
    private val pushNotificationsApi: PushNotificationsApi,
    private val signingApiFactory: HttpMessageSigningApiFactory,
) : PushNotificationsApiClient {
    override suspend fun getChallenge(): PushNotificationsApiResult<PushNotificationsChallengeResponse> =
        pushNotificationsApi.getChallenge().toPushNotificationsApiResult()

    override fun signingApi(mdvmAuthPrvk: PrivateKey): SigningPushNotificationsApiClient {
        val signingApi = signingApiFactory.create(
            apiClass = SigningPushNotificationsApi::class.java,
            headersToSign = SigningPushNotificationsApi.HEADERS,
            pushNotificationsAuthSignatureSpec(mdvmAuthPrvk),
        )
        return SigningPushNotificationsApiClientImpl(signingApi)
    }

    private fun pushNotificationsAuthSignatureSpec(mdvmAuthPrvk: PrivateKey): HttpMessageSignatureSpec =
        HttpMessageSignatureSpec(
            signingKey = mdvmAuthPrvk,
            signatureName = "pns-auth-sig",
            keyId = "wi-mdvm-auth-key",
        )
}

internal class SigningPushNotificationsApiClientImpl(
    private val api: SigningPushNotificationsApi,
) : SigningPushNotificationsApiClient {
    override suspend fun register(
        mppRegistrationToken: String,
        authChallenge: String,
        mdvmToken: String,
    ): PushNotificationsApiResult<Unit> =
        api.register(
            request = PushNotificationsRegisterRequest(mppRegistrationToken = mppRegistrationToken),
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
        ).toPushNotificationsApiResult()

    override suspend fun delete(
        authChallenge: String,
        mdvmToken: String,
    ): PushNotificationsApiResult<Unit> =
        api.delete(
            authChallenge = authChallenge,
            mdvmToken = mdvmToken,
        ).toPushNotificationsApiResult()
}