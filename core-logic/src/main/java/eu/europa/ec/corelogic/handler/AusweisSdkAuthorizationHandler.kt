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

package eu.europa.ec.corelogic.handler

import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationHandler
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationResponse
import eu.europa.ec.corelogic.handler.pid.RefreshUrlInteractor
import eu.europa.ec.corelogic.handler.pid.RefreshUrlResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sprind.wallet.businesslogic.config.UserRuntimeConfig
import org.sprind.wallet.corelogic.eid.AuthOutcome
import org.sprind.wallet.corelogic.eid.EidEngine
import org.sprind.wallet.corelogic.eid.EidWorkflow
import org.sprind.wallet.corelogic.eid.eidCardSource
import java.util.concurrent.CancellationException

/**
 * An [AuthorizationHandler] that authorizes through the eID card instead of opening the
 * authorizationCodeUrl in a browser window.
 *
 * [authorize] starts the eID authentication and offers it as [authentication] to the screen that
 * takes the user through it. The authorization is answered once that screen hands over the redirect
 * the authentication ended at, through [resumeWithRedirectUri]; a failed authentication leaves it
 * waiting, for the screen to retry or to give the issuance up.
 */
class AusweisSdkAuthorizationHandler(
    private val eidEngine: EidEngine,
    private val userRuntimeConfig: UserRuntimeConfig,
    private val refreshUrlInteractor: RefreshUrlInteractor,
) : AuthorizationHandler {

    private val _authentication = MutableStateFlow<EidWorkflow<AuthOutcome>?>(null)

    /** The eID authentication the pending authorization waits on, if one does. */
    val authentication: StateFlow<EidWorkflow<AuthOutcome>?> = _authentication.asStateFlow()

    private var deferredResult: CompletableDeferred<Result<AuthorizationResponse>>? = null

    /** Stops offering the authentication of an issuance that has been given up on. */
    fun forgetPendingAuthorization() {
        _authentication.value = null
    }

    override suspend fun authorize(authorizationUrl: String): Result<AuthorizationResponse> {
        val deferred = CompletableDeferred<Result<AuthorizationResponse>>()
        deferredResult = deferred

        val authentication = eidEngine.authenticate(
            tcTokenUrl = authorizationUrl,
            card = userRuntimeConfig.eidCardSource(),
        )
        _authentication.value = authentication
        try {
            return deferred.await()
        } finally {
            // Answered or given up on, the authorization has no more use for it; one that ended
            // already is left as it is.
            authentication.cancel()
            _authentication.compareAndSet(authentication, null)
        }
    }

    suspend fun resumeWithRedirectUri(redirectUrl: String) : Result<AuthorizationResponse> {
        val currentDeferred = deferredResult
        if (currentDeferred == null || currentDeferred.isCompleted) return Result.failure(Exception("No pending authorization request available"))
        val resumedAuthentication = _authentication.value

       return try {
            val result = withContext(Dispatchers.IO) {
                refreshUrlInteractor.callRefreshUrl(redirectUrl)
                    .map { refreshResult ->
                        when (refreshResult) {
                            is RefreshUrlResult.Success -> {
                                val response = refreshResult.authorizationResponse
                                if (response.authorizationCode.isNotBlank()) {
                                    Result.success(response)
                                } else {
                                    Result.failure(IllegalStateException("Missing code"))
                                }
                            }
                            is RefreshUrlResult.Failure -> {
                                Result.failure(Exception(refreshResult.errorMessage, refreshResult.cause))
                            }
                        }
                    }.first()
            }
            currentDeferred.complete(result)
            result
        } catch (e: Exception) {
            val failure = Result.failure<AuthorizationResponse>(e)
            currentDeferred.complete(failure)
            failure
        } finally {
            // Only the authorization resumed here: a newer one is left pending, and offered.
            if (deferredResult === currentDeferred) deferredResult = null
            _authentication.compareAndSet(resumedAuthentication, null)
        }
    }

    fun cancel(cause: Throwable? = null) {
        deferredResult?.complete(
            Result.failure(cause ?: CancellationException("User cancelled"))
        )
        deferredResult = null
    }
}
