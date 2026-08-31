package org.sprind.wallet.networklogic.walletbackend.model.response

import com.google.gson.annotations.SerializedName

data class WalletErrorResponse(
    @SerializedName("code")
    val errorCode: String,
    val traceId: String? = null,
)

internal fun WalletErrorResponse.withTraceId(traceId: String?): WalletErrorResponse =
    this.copy(traceId = traceId)
