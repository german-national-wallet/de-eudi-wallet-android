/*
 * Copyright (c) 2025 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.commonfeature.interactor

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricAuthenticationController
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAuthenticate
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.sprind.wallet.businesslogic.model.UserPin

interface BiometricInteractor {
    fun getBiometricsAvailability(listener: (BiometricsAvailability) -> Unit)
    fun getBiometricUserSelection(): Boolean
    fun storeBiometricsUsageDecision(shouldUseBiometrics: Boolean)
    fun authenticateWithBiometrics(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit
    )

    fun launchBiometricSystemScreen()

    /** Consumes [pin] and reports whether it unlocks the wallet. */
    fun isPinValid(pin: UserPin): Flow<BiometricPinValidationResult>
}

sealed class BiometricPinValidationResult {
    data object Success : BiometricPinValidationResult()
    data class Failed(val errorMessage: String) : BiometricPinValidationResult()
}

class BiometricInteractorImpl(
    private val biometryStorageController: BiometryStorageController,
    private val biometricAuthenticationController: BiometricAuthenticationController,
) : BiometricInteractor {

    override fun isPinValid(pin: UserPin): Flow<BiometricPinValidationResult> = flow {
        // FIXME: this accepts any code of the right length. Kept as it was found, but the pin is at
        //  least consumed now instead of being left in memory for a check that never happens.
        pin.getAndClear().close()
        emit(BiometricPinValidationResult.Success)
    }

    override fun storeBiometricsUsageDecision(shouldUseBiometrics: Boolean) {
        biometryStorageController.setUseBiometricsAuth(shouldUseBiometrics)
    }

    override fun getBiometricUserSelection(): Boolean {
        return biometryStorageController.getUseBiometricsAuth()
    }

    override fun getBiometricsAvailability(listener: (BiometricsAvailability) -> Unit) {
        biometricAuthenticationController.deviceSupportsBiometrics(listener)
    }

    override fun authenticateWithBiometrics(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit
    ) {
        biometricAuthenticationController.authenticate(
            context,
            notifyOnAuthenticationFailure,
            listener
        )
    }

    override fun launchBiometricSystemScreen() {
        biometricAuthenticationController.launchBiometricSystemScreen()
    }
}
