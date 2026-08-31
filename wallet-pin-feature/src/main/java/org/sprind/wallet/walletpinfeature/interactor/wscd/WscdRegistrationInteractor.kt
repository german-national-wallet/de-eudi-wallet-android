package org.sprind.wallet.walletpinfeature.interactor.wscd

import kotlinx.coroutines.flow.Flow
import org.sprind.wallet.businesslogic.model.UserPin


sealed class WscaRegistrationResult {
    data object Success : WscaRegistrationResult()
    data class Failure(val errorCode: String, val traceId: String? = null) : WscaRegistrationResult()
}

interface WscaRegistrationInteractor {
    /**
     * Registers wallet with wscd, consuming [pin] in the process.
     */
    suspend fun register(pin: UserPin): Flow<WscaRegistrationResult>
    fun isAlreadyRegistered(): Boolean
}

