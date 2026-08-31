package eu.europa.ec.commonfeature.interactor

import org.sprind.wallet.authenticationlogic.model.RwscaError
import org.sprind.wallet.businesslogic.model.UserPin

/**
 * The result of a call to [RwscaPinHandler.startPinSession].
 */
sealed class StartPinSessionResult {
    data object Success : StartPinSessionResult()
    data class Failure(val error: RwscaError) : StartPinSessionResult()
}

/**
 * Manages the lifetime of PinSession / PIN-derived keys.
 */
interface RwscaPinHandler {
    /**
     * Validates the PIN and starts a signing session.
     */
    suspend fun startPinSession(pin: UserPin): StartPinSessionResult

    /** Clears any state held from the current PIN session. */
    fun clearPinSession()
}
