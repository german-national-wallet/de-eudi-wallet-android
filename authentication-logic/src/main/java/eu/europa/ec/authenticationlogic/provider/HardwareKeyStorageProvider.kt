package eu.europa.ec.authenticationlogic.provider

interface HardwareKeyStorageProvider {
    /**
     * @return refresh token if it is available in the storage else null
     */
    fun getRefreshToken(): String

    /**
     * Use this method only to store the refresh token
     * @return Unit
     */
    fun storeRefreshToken(value: String)

    /**
     * Removes wallet registration specific data needed for PID
     * This logic will be updated in the future once we have a stable storage strategy
     * TODO: this is a quick fix for the issue WD-2191
     */
    fun removeWalletRegistrationData()
}