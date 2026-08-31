package eu.europa.ec.authenticationlogic.storage

import eu.europa.ec.authenticationlogic.provider.WalletPinUnBlockTimeStorageProvider
import eu.europa.ec.businesslogic.controller.storage.PrefsController

internal class PrefsWalletPinUnBlockTimeStorageProvider(
    private val prefsController: PrefsController,
) : WalletPinUnBlockTimeStorageProvider {

    private companion object {
        const val PIN_BLOCK_TIME_KEY = "PIN_BLOCK_TIME_KEY"
        const val PIN_REMAINING_TRIES_KEY = "PIN_REMAINING_TRIES_KEY"
    }

    override fun retrieveTime(): String {
        return prefsController.getString(PIN_BLOCK_TIME_KEY, "")
    }

    override fun retrieveRemainingTries(): Int? {
       prefsController.getInt(PIN_REMAINING_TRIES_KEY, 9999).let {
           return if(it == 9999) {
               null
           } else {
               it
           }
        }
    }

    override fun set(iso8601utcTime: String, remainingTries: Int?) {
        prefsController.setString(PIN_BLOCK_TIME_KEY, iso8601utcTime)
        prefsController.setInt(PIN_REMAINING_TRIES_KEY, remainingTries?: 9999)
    }

    override fun clear() {
        prefsController.clear(PIN_BLOCK_TIME_KEY)
        prefsController.clear(PIN_REMAINING_TRIES_KEY)
    }
}