package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import java.security.KeyStore.PrivateKeyEntry

interface HardwareKeyStorageController {
    fun saveRefreshToken(token: String)
    fun getRefreshToken(): String

    /**
     * Internally the following storages are going to be cleared up
     * storageConfig.hardwareKeyStorageProvider.removeWalletRegistrationData()
     * cryptoController.removeWalletRegistrationData()
     * TODO: this is a quick fix for the issue WD-2191
     */
    fun removeWalletRegistrationIds()
}

internal class HardwareKeyStorageControllerImpl(
    private val storageConfig: StorageConfig,
    private val cryptoController: CryptoController,
) : HardwareKeyStorageController {

    override fun saveRefreshToken(token: String) {
        storageConfig.hardwareKeyStorageProvider.storeRefreshToken(token)
    }

    override fun getRefreshToken(): String {
        return storageConfig.hardwareKeyStorageProvider.getRefreshToken()
    }

    override fun removeWalletRegistrationIds() {
        storageConfig.hardwareKeyStorageProvider.removeWalletRegistrationData()
        cryptoController.removeWalletRegistrationData()
    }
}