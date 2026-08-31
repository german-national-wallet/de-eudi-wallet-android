package eu.europa.ec.authenticationlogic.provider

import eu.europa.ec.authenticationlogic.model.WalletRegistration

interface WalletRegistrationStorageProvider {
    /**
     * @return WalletRegistration instance if it is available in the storage else null
     */
    fun getWalletRegistration(): WalletRegistration?
    fun storeWalletRegistration(value: WalletRegistration)
}