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

package eu.europa.ec.corelogic.handler.pid

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationResponse
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.sprind.wallet.corelogic.issuance.AuthorizationRedirectException
import org.sprind.wallet.networklogic.utils.parseUrl

sealed class RefreshUrlResult {
    data class Success(val authorizationResponse: AuthorizationResponse) :
        RefreshUrlResult()

    /** @property cause What went wrong, for classifying the failure (e.g. a timeout). */
    data class Failure(val errorMessage: String, val cause: Throwable? = null) : RefreshUrlResult()
}

/**
 * Interactor to complete the refreshUrl
 */
interface RefreshUrlInteractor {
    /**
     * @return A [Flow] of [RefreshUrlResult] indicating the result
     */
    suspend fun callRefreshUrl(refreshUrl: String): Flow<RefreshUrlResult>
}

/**
 * Internal use only.
 */
internal class RefreshUrlInteractorImpl(
    private val logController: LogController,
    private val resourceProvider: ResourceProvider,
    private val httpClient: OkHttpClient,
) : RefreshUrlInteractor {

    private val logTag = javaClass.simpleName
    private val genericErrorMessage
        get() = resourceProvider.getString(R.string.generic_error_message)

    override suspend fun callRefreshUrl(refreshUrl: String): Flow<RefreshUrlResult> =
        flow {
            emit(
                try {
                    val request = Request.Builder().url(refreshUrl).build()
                    val headers = httpClient.newCall(request).execute().use { it.headers }
                    val location = headers["location"]
                        ?: throw IllegalStateException("No Location received")
                    val locationQueryParams = parseUrl(location)
                    locationQueryParams["error"]?.let { error ->
                        throw AuthorizationRedirectException(
                            error = error,
                            errorDescription = locationQueryParams["error_description"],
                        )
                    }
                    val code = locationQueryParams["code"]
                        ?: throw IllegalStateException("No authorization code found")
                    val state = locationQueryParams["state"]
                        ?: throw IllegalStateException("No server state found")

                    val authResponse = AuthorizationResponse(
                        authorizationCode = code,
                        serverState = state,
                        dpopNonce = headers["DPoP-Nonce"]?.takeIf { it.isNotBlank() },
                    )
                    logController.d(logTag) { "Finish-authorization redirect parsed" }
                    RefreshUrlResult.Success(authResponse)
                } catch (exception: Exception) {
                    // TODO WD-191
                    if (exception is CancellationException) throw exception
                    logController.e(logTag, exception)
                    RefreshUrlResult.Failure(genericErrorMessage, cause = exception)
                })
        }.flowOn(Dispatchers.IO)
}
