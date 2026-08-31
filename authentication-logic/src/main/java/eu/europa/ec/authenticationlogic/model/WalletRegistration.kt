package eu.europa.ec.authenticationlogic.model

import com.google.gson.annotations.SerializedName

data class WalletRegistration(
    @SerializedName("encrypted") val encryptedString: String,
    @SerializedName("iv") val ivString: String,
)