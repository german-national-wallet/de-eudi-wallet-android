package eu.europa.ec.commonfeature.ui.issuer_details.model

import kotlinx.serialization.Serializable

@Serializable
data class IssuerInfo(
    val issuerName: String,
    val imageRes: Int? = null,
    val logoUri: String? = null,
    val address: String = "",
    val email: String = "",
    val privacyPolicy: String = "",
    val certificateValidUntil: String = "",
)