
package eu.europa.ec.corelogic.handler

import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationHandler
import eu.europa.ec.eudi.wallet.issue.openid4vci.AuthorizationResponse
import eu.europa.ec.corelogic.handler.pid.RefreshUrlInteractor
import eu.europa.ec.corelogic.handler.pid.RefreshUrlResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

/**
 * An [AuthorizationHandler] that delegates to AusweisSDK's NFC flow instead of opening the
 * authorizationCodeUrl in a browser window.
 */
class AusweisSdkAuthorizationHandler(
    private val refreshUrlInteractor: RefreshUrlInteractor,
) : AuthorizationHandler {

    private val _authorizationRequest = MutableSharedFlow<String>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val authorizationRequest: SharedFlow<String> = _authorizationRequest.asSharedFlow()

    private var deferredResult: CompletableDeferred<Result<AuthorizationResponse>>? = null

    override suspend fun authorize(authorizationUrl: String): Result<AuthorizationResponse> {
        val deferred = CompletableDeferred<Result<AuthorizationResponse>>()
        deferredResult = deferred

        _authorizationRequest.emit(authorizationUrl)

        return deferred.await()
    }

    suspend fun resumeWithRedirectUri(redirectUrl: String) : Result<AuthorizationResponse> {
        val currentDeferred = deferredResult
        if (currentDeferred == null || currentDeferred.isCompleted) return Result.failure(Exception("No pending authorization request available"))

       return try {
            val result = withContext(Dispatchers.IO) {
                refreshUrlInteractor.callRefreshUrl(redirectUrl)
                    .map { refreshResult ->
                        when (refreshResult) {
                            is RefreshUrlResult.Success -> {
                                val code = refreshResult.authorizationResponse.code
                                val state = refreshResult.authorizationResponse.state
                                if (code.isNotBlank()) {
                                    Result.success(AuthorizationResponse(code, state))
                                } else {
                                    Result.failure(IllegalStateException("Missing code"))
                                }
                            }
                            is RefreshUrlResult.Failure -> {
                                Result.failure(Exception(refreshResult.errorMessage))
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
            deferredResult = null
            _authorizationRequest.resetReplayCache()
        }
    }

    fun cancel(cause: Throwable? = null) {
        deferredResult?.complete(
            Result.failure(cause ?: CancellationException("User cancelled"))
        )
        deferredResult = null
    }
}