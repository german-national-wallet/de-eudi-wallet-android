package org.sprind.wallet.networklogic.walletbackend.model.response

import com.google.gson.annotations.SerializedName

data class WalletRegisterResponse(
    @SerializedName("wpb_wi_id")
    val walletInstanceId: String,
    @SerializedName("wpb_wi_revocation_code")
    val walletInstanceRevocationCode: String
)
