package eu.europa.ec.authenticationlogic.storage

import com.google.gson.Gson
import com.google.gson.JsonParseException
import eu.europa.ec.authenticationlogic.model.WalletRegistration
import eu.europa.ec.authenticationlogic.provider.WalletRegistrationStorageProvider
import eu.europa.ec.businesslogic.controller.storage.EncryptedPrefsController

const val SHARED_PREFERENCE_WALLET_REGISTRATION_KEY = "WalletRegistration"

internal class PrefsWalletRegistrationStorageProvider(
    private val prefsController: EncryptedPrefsController
) : WalletRegistrationStorageProvider {
    /**
     * Returns `null` when nothing is stored or the stored JSON is corrupt. A failure to read the
     * encrypted preferences themselves (e.g. the auth-bound master key is unavailable) propagates:
     * callers treat `null` as "not registered" and would otherwise register afresh over an
     * existing registration.
     */
    override fun getWalletRegistration(): WalletRegistration? {
        val json = prefsController.getString(SHARED_PREFERENCE_WALLET_REGISTRATION_KEY, "")
        if (json.isEmpty()) return null
        return try {
            Gson().fromJson(json, WalletRegistration::class.java)
        } catch (e: JsonParseException) {
            null
        }
    }

    override fun storeWalletRegistration(value: WalletRegistration) {
        prefsController.setString(SHARED_PREFERENCE_WALLET_REGISTRATION_KEY, Gson().toJson(value))
    }
}