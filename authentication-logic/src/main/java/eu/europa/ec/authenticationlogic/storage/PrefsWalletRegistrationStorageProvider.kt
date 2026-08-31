package eu.europa.ec.authenticationlogic.storage

import com.google.gson.Gson
import eu.europa.ec.authenticationlogic.model.WalletRegistration
import eu.europa.ec.authenticationlogic.provider.WalletRegistrationStorageProvider
import eu.europa.ec.businesslogic.controller.storage.PrefsController

const val SHARED_PREFERENCE_WALLET_REGISTRATION_KEY = "WalletRegistration"

internal class PrefsWalletRegistrationStorageProvider(
    private val prefsController: PrefsController
) : WalletRegistrationStorageProvider {
    override fun getWalletRegistration(): WalletRegistration? {
        return try {
            Gson().fromJson(
                prefsController.getString(SHARED_PREFERENCE_WALLET_REGISTRATION_KEY, ""),
                WalletRegistration::class.java
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun storeWalletRegistration(value: WalletRegistration) {
        prefsController.setString(SHARED_PREFERENCE_WALLET_REGISTRATION_KEY, Gson().toJson(value))
    }
}