package eu.europa.ec.authenticationlogic.provider

interface WalletPinUnBlockTimeStorageProvider {
    fun retrieveTime(): String
    fun retrieveRemainingTries(): Int?
    fun set(iso8601utcTime: String, remainingTries: Int?)
    fun clear()
}