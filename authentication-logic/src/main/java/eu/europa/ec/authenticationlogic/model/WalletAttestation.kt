package eu.europa.ec.authenticationlogic.model

import com.google.gson.annotations.SerializedName


data class WalletAttestation(
    @SerializedName("encrypted") val encryptedString: String,
    @SerializedName("iv") val ivString: String,
)