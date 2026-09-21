package org.sprind.wallet.networklogic.walletbackend.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.sprind.wallet.networklogic.common.model.WithTraceId

@Serializable
data class WalletErrorResponse(
    @SerialName("code")
    val errorCode: String,
    val description: String? = null,
    val timestamp: String? = null,
    @SerialName("trace_id") val traceId: String? = null,
) : WithTraceId<WalletErrorResponse> {
    override fun withTraceId(traceId: String?): WalletErrorResponse = copy(traceId = traceId)
}