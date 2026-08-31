package eu.europa.ec.startupfeature.interactor.registration

import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletRegistrationPartialState
import eu.europa.ec.authenticationlogic.controller.storage.WalletRegistrationStorageController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.sprind.wallet.commonfeature.interactor.MdvmInteractor
import org.sprind.wallet.networklogic.common.model.ApiResult

sealed class WalletInitialRegistrationPartialState {
    data object AlreadyRegistered : WalletInitialRegistrationPartialState()
    data object Success : WalletInitialRegistrationPartialState()
    /**
     * @property errorCode The recognised code, used to pick the message shown to the user.
     * @property backendErrorCode The code exactly as the backend reported it, so that a code this
     * app does not recognise is still shown to the user instead of being flattened to
     * [ErrorCode.UNKNOWN].
     */
    data class Failure(
        val errorCode: ErrorCode,
        val traceId: String?,
        val backendErrorCode: String = errorCode.name,
    ) : WalletInitialRegistrationPartialState()

    enum class ErrorCode {
        WB_SERVICE_UNAVAILABLE,
        WB_BAD_REQUEST,
        WB_INTERNAL_ERROR,
        UNKNOWN
    }
}

interface WalletRegistrationInteractor {
    /**
     * Registers wallet on server
     */
    suspend fun registerWallet(): Flow<WalletInitialRegistrationPartialState>
}

class WalletRegistrationInteractorImpl(
    private val appAttestationController: AppAttestationController,
    private val walletRegistrationStorageController: WalletRegistrationStorageController,
    private val mdvmInteractor: MdvmInteractor,
) : WalletRegistrationInteractor {
    override suspend fun registerWallet(): Flow<WalletInitialRegistrationPartialState> = flow {
        if (walletRegistrationStorageController.getWalletRegistration() != null) {
            emit(WalletInitialRegistrationPartialState.AlreadyRegistered)
            return@flow
        }

        when (val mdvmResult = mdvmInteractor.mdvmRegistration()) {
            is ApiResult.Failure -> {
                emit(
                    WalletInitialRegistrationPartialState.Failure(
                        errorCode = WalletInitialRegistrationPartialState.ErrorCode.UNKNOWN,
                        traceId = mdvmResult.error.traceId,
                        backendErrorCode = mdvmResult.error.code,
                    )
                )
                return@flow
            }

            is ApiResult.Success -> Unit
        }

        appAttestationController.registerWallet().collect { registrationResult ->
            when (registrationResult) {
                is WalletRegistrationPartialState.Success -> {
                    emit(WalletInitialRegistrationPartialState.Success)
                }

                is WalletRegistrationPartialState.Failure -> emit(
                    WalletInitialRegistrationPartialState.Failure(
                        errorCode = runCatching {
                            WalletInitialRegistrationPartialState.ErrorCode.valueOf(
                                registrationResult.errorCode
                            )
                        }.getOrDefault(WalletInitialRegistrationPartialState.ErrorCode.UNKNOWN),
                        traceId = registrationResult.traceId,
                        backendErrorCode = registrationResult.errorCode,
                    )
                )
            }
        }
    }
}
