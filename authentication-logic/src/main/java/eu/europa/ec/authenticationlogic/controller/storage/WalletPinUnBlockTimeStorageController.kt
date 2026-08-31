package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig

interface WalletPinUnBlockTimeStorageController {
    fun retrieveRemainingTries(): Int?
    fun retrievePinTime(): String
    fun set(iso8601utcTime: String, remainingTries: Int?)
    fun clear()
}

internal class WalletPinUnBlockTimeStorageControllerImpl(private val storageConfig: StorageConfig) :
    WalletPinUnBlockTimeStorageController {
    override fun retrievePinTime(): String =
        storageConfig.walletPinUnBlockTimeStorageProvider.retrieveTime()

    override fun retrieveRemainingTries(): Int? =
        storageConfig.walletPinUnBlockTimeStorageProvider.retrieveRemainingTries()

    override fun set(iso8601utcTime: String, remainingTries: Int?) {
        storageConfig.walletPinUnBlockTimeStorageProvider.set(
            iso8601utcTime = iso8601utcTime,
            remainingTries = remainingTries
        )
    }

    override fun clear() {
        storageConfig.walletPinUnBlockTimeStorageProvider.clear()
    }
}