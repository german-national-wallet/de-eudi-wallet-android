package eu.europa.ec.authenticationlogic.controller.appattestation

import eu.europa.ec.businesslogic.controller.log.LogController
import org.sprind.wallet.businesslogic.extensions.runSuspendCatching
import org.sprind.wallet.networklogic.walletbackend.api.WalletApiClient
import org.sprind.wallet.networklogic.common.NetworkFailure
import org.sprind.wallet.networklogic.common.toNetworkFailure
import org.sprind.wallet.networklogic.trace.traceId
import org.sprind.wallet.networklogic.utils.getErrorCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.sprind.wallet.networklogic.common.model.ApiResult
import org.sprind.wallet.networklogic.walletbackend.model.WalletApiResult
import org.sprind.wallet.networklogic.walletbackend.model.response.WalletRegisterResponse
import java.security.PrivateKey

sealed class WalletInstancePartialState {
    data class Success(val walletInstanceId: String, val walletInstanceRevocationCode: String) :
        WalletInstancePartialState()
    /** @property networkFailure Set when the request failed in transit (see [toNetworkFailure]). */
    data class Failure(
        val errorCode: String,
        val traceId: String?,
        val networkFailure: NetworkFailure? = null,
    ) : WalletInstancePartialState()
}

sealed class WalletRegistrationPartialState {
    data object Success : WalletRegistrationPartialState()
    /** @property networkFailure Set when the request failed in transit (see [toNetworkFailure]). */
    data class Failure(
        val errorCode: String,
        val traceId: String?,
        val networkFailure: NetworkFailure? = null,
    ) : WalletRegistrationPartialState()
}

sealed class RegistrationChallengePartialState {
    data class Success(val challenge: String) : RegistrationChallengePartialState()
    /** @property networkFailure Set when the request failed in transit (see [toNetworkFailure]). */
    data class Failure(
        val errorCode: String,
        val traceId: String?,
        val networkFailure: NetworkFailure? = null,
    ) : RegistrationChallengePartialState()
}

fun WalletApiResult<WalletRegisterResponse>.toWalletInstancePartialState() = when (this) {
    is ApiResult.Success ->
        WalletInstancePartialState.Success(
            walletInstanceId = response.walletInstanceId,
            walletInstanceRevocationCode = response.walletInstanceRevocationCode
        )

    is ApiResult.Failure ->
        WalletInstancePartialState.Failure(
            errorCode = error.errorCode,
            traceId = error.traceId
        )
}

/**
 * Controller for interacting with api client for wallet registration
 * */
interface WalletRegistrationController {
    /**
     * Gets the challenge from the server
     * */
    fun getChallenge(): Flow<RegistrationChallengePartialState>

    /**
     * Gets the wallet instance id from the server
     * */
    fun getWalletInstanceId(
        authChallenge: String,
        mdvmToken: String,
        mdvmAuthPrvk: PrivateKey,
    ): Flow<WalletInstancePartialState>
}

class WalletRegistrationControllerImpl(
    private val apiClient: WalletApiClient,
    private val logController: LogController,
) : WalletRegistrationController {
    private val logTag = javaClass.simpleName
    override fun getChallenge(): Flow<RegistrationChallengePartialState> = flow {
        runSuspendCatching {
            when (val result = apiClient.getChallenge()) {
                is ApiResult.Success -> RegistrationChallengePartialState.Success(result.response.challenge)
                is ApiResult.Failure -> RegistrationChallengePartialState.Failure(
                    errorCode = result.error.errorCode,
                    traceId = result.error.traceId
                )
            }
        }.onSuccess { result ->
            emit(result)
        }.onFailure { exception ->
            logController.e(logTag, exception)
            val errorCode = exception.getErrorCode()
            emit(
                RegistrationChallengePartialState.Failure(
                    errorCode = errorCode,
                    traceId = exception.traceId(),
                    networkFailure = exception.toNetworkFailure(),
                )
            )
        }
    }

    override fun getWalletInstanceId(
        authChallenge: String,
        mdvmToken: String,
        mdvmAuthPrvk: PrivateKey,
    ): Flow<WalletInstancePartialState> = flow {
        runSuspendCatching {
            val signingApiClient = apiClient.signingApi(mdvmAuthPrvk)

            signingApiClient.register(
                authChallenge = authChallenge,
                mdvmToken = mdvmToken,
            )

        }.onSuccess { result ->
            emit(result.toWalletInstancePartialState())
        }.onFailure { exception ->
            logController.e(logTag, exception)
            val errorCode = exception.getErrorCode()
            emit(
                WalletInstancePartialState.Failure(
                    errorCode = errorCode,
                    traceId = exception.traceId(),
                    networkFailure = exception.toNetworkFailure(),
                )
            )
        }
    }
}
