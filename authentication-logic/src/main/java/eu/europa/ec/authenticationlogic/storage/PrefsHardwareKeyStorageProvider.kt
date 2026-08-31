package eu.europa.ec.authenticationlogic.storage

import eu.europa.ec.authenticationlogic.provider.HardwareKeyStorageProvider
import eu.europa.ec.businesslogic.controller.storage.PrefKeys.Companion.KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS
import eu.europa.ec.businesslogic.controller.storage.PrefKeys.Companion.KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS
import eu.europa.ec.businesslogic.controller.storage.PrefKeys.Companion.KEY_WALLET_REGISTRATION_ALIAS
import eu.europa.ec.businesslogic.controller.storage.PrefsController

private const val SHARED_PREFERENCE_REFRESH_TOKEN_KEY = "RefreshToken"

class PrefsHardwareKeyStorageProvider(
    private val prefsController: PrefsController
) : HardwareKeyStorageProvider {

    override fun getRefreshToken(): String {
        return prefsController.getString(SHARED_PREFERENCE_REFRESH_TOKEN_KEY, "")
    }

    override fun storeRefreshToken(value: String) {
        prefsController.setString(SHARED_PREFERENCE_REFRESH_TOKEN_KEY, value)
    }

    override fun removeWalletRegistrationData() {
        // this key relates to the cypher
        prefsController.clear("StorageKey")
        prefsController.clear(KEY_WALLET_INSTANCE_PRIVATE_KEY_ALIAS)
        prefsController.clear(KEY_WALLET_INSTANCE_ATTESTATION_PRIVATE_KEY_ALIAS)
        prefsController.clear(KEY_WALLET_REGISTRATION_ALIAS)
        prefsController.clear(SHARED_PREFERENCE_REFRESH_TOKEN_KEY)
        prefsController.clear(SHARED_PREFERENCE_WALLET_REGISTRATION_KEY)
    }
}