package eu.europa.ec.corelogic.handler.pid

data class AuthorizationResponse(
    val code: String, val state: String, val dPoPNonce: String
)