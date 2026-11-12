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

package eu.europa.ec.authenticationlogic.controller.authentication

import android.content.Context
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.resourceslogic.R
import kotlinx.coroutines.launch

interface IdentificationOnlyAuthenticationController {
    fun deviceSupportsBiometrics(listener: (BiometricsAvailability) -> Unit)
    fun authenticate(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit
    )
}

class IdentificationOnlyAuthenticationControllerImpl(
    private val biometricAuthenticationController: BiometricAuthenticationController
) : IdentificationOnlyAuthenticationController {

    private val allowedAuthenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    override fun deviceSupportsBiometrics(listener: (BiometricsAvailability) -> Unit) {
        biometricAuthenticationController.deviceSupportsBiometrics(allowedAuthenticators, listener)
    }

    override fun authenticate(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit
    ) {
        if (context !is FragmentActivity) return

        context.lifecycleScope.launch {

            val data = biometricAuthenticationController.authenticate(
                activity = context,
                biometryCrypto = BiometricCrypto(cryptoObject = null),
                promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(context.getString(R.string.global_native_bio_consent_title_android))
                    .setSubtitle(context.getString(R.string.global_native_bio_consent_paragraph_android))
                    .setAllowedAuthenticators(allowedAuthenticators)
                    .build(),
                notifyOnAuthenticationFailure = notifyOnAuthenticationFailure
            )

            if (data.authenticationResult != null) {
                listener(BiometricsAuthenticate.Success)
            } else if (
                data.errorCode != BiometricsAuthError.Cancel.code &&
                data.errorCode != BiometricsAuthError.CancelByUser.code
            ) {
                listener(BiometricsAuthenticate.Failed(data.errorString.toString()))
            } else {
                listener(BiometricsAuthenticate.Cancelled)
            }
        }
    }
}
