/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.authenticationlogic.storage

import com.google.gson.Gson
import com.google.gson.JsonParseException
import eu.europa.ec.authenticationlogic.model.BiometricAuthentication
import eu.europa.ec.authenticationlogic.provider.BiometryStorageProvider
import eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController

class PrefsBiometryStorageProvider(
    private val prefsController: EncryptedPrefsController
) : BiometryStorageProvider {

    /**
     * Returns the biometric data in order to validate that biometric is not tampered in any way.
     *
     * `null` when nothing is stored or the stored JSON is corrupt. A failure to read the encrypted
     * preferences themselves (e.g. the auth-bound master key is unavailable) propagates: callers
     * treat `null` as "no biometric data" and would otherwise reset the biometric setup.
     */
    override fun getBiometricAuthentication(): BiometricAuthentication? {
        val json = prefsController.getString(BIOMETRIC_AUTHENTICATION_KEY, "")
        if (json.isEmpty()) return null
        return try {
            Gson().fromJson(json, BiometricAuthentication::class.java)
        } catch (e: JsonParseException) {
            null
        }
    }

    /**
     * Stores the biometric data used to validate that biometric is not tampered in any way.
     *
     * @param value the biometric data.
     */
    override fun setBiometricAuthentication(value: BiometricAuthentication?) {
        if (value == null) prefsController.clear(BIOMETRIC_AUTHENTICATION_KEY)
        prefsController.setString(BIOMETRIC_AUTHENTICATION_KEY, Gson().toJson(value))
    }

    /**
     * Key to use Biometrics Auth instead of quick pin.
     *
     * Setting an empty value will clear the entry from shared prefs.
     */
    override fun setUseBiometricsAuth(value: Boolean) {
        prefsController.setBool("UseBiometricsAuth", value)
    }

    /**
     * Key to use Biometrics Auth instead of quick pin.
     *
     * Setting an empty value will clear the entry from shared prefs.
     */
    override fun getUseBiometricsAuth(): Boolean {
        return prefsController.getBool("UseBiometricsAuth", false)
    }

    private companion object {
        const val BIOMETRIC_AUTHENTICATION_KEY = "BiometricAuthentication"
    }
}
