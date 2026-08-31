package eu.europa.ec.corelogic.handler.reader.model

import com.governikus.ausweisapp.sdkwrapper.card.core.AccessRight

/**
 * @property requiredAttributes List of all available access rights a provider might request.
 */
data class IdentificationRequest(
    val requiredAttributes: List<AccessRight>,
    val transactionInfo: String?
)
