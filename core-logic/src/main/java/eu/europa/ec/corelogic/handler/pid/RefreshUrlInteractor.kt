package eu.europa.ec.corelogic.handler.pid

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.R
import org.sprind.wallet.networklogic.utils.parseUrl
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class RefreshUrlResult {
    data class Success(val authorizationResponse: AuthorizationResponse) :
        RefreshUrlResult()

    data class Failure(val errorMessage: String) : RefreshUrlResult()
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
                    val result = httpClient.newCall(request).execute()
                    val location =
                        result.headers["location"]
                            ?: throw IllegalStateException("No Location received")
                    val dPoPNonce =
                        result.headers["dpop-Nonce"]
                            ?: throw IllegalStateException("No DPoP-Nonce received")
                    val locationQueryParams = parseUrl(location)
                    val code = locationQueryParams["code"]
                        ?: throw IllegalStateException("No authorization code found")
                    val state = locationQueryParams["state"]
                        ?: throw IllegalStateException("No server state found")

                    val authResponse = AuthorizationResponse(
                        code = code,
                        state = state,
                        dPoPNonce = dPoPNonce
                    )
                    logController.d(logTag) { authResponse.toString() }
                    RefreshUrlResult.Success(authResponse)
                } catch (exception: Exception) {
                    // TODO WD-191
                    if (exception is CancellationException) throw exception
                    logController.e(logTag, exception)
                    RefreshUrlResult.Failure(genericErrorMessage)
                } catch (exception: IllegalStateException) {
                    logController.e(logTag, exception)
                    RefreshUrlResult.Failure(genericErrorMessage)
                })
        }.flowOn(Dispatchers.IO)
}
