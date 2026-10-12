package eu.europa.ec.commonfeature.ui.issuer_details.model

import eu.europa.ec.commonfeature.model.DocumentType
import kotlinx.serialization.Serializable

@Serializable
data class IssuerInfo(
    val issuerName: String,
    /**
     * Which flow raised this screen. [IssuerDetailsContent] is shared between PID's card-reader
     * consent step and EAA's credential-offer screen, and the two show a different set of
     * optional detail rows below "Name" (see [IssuerDetailsContent]'s `optionalDetails`).
     */
    val documentType: DocumentType,
    val imageRes: Int? = null,
    val logoUri: String? = null,
    val address: String = "",
    val country: String = "",
    val email: String = "",
    val privacyPolicy: String = "",
    val certificateValidUntil: String = "",
    val identificationNumber: String = "",
)