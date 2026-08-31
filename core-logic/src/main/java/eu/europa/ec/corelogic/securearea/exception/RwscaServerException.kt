package eu.europa.ec.corelogic.securearea.exception

// Used by both rWSCD and rWSCA during the rWSCD -> rWSCA migration period.
// TODO: Rename to RwscaServerException and drop RwscException base class once we switch to rWSCA and delete rWSCD code.
data class RwscaServerException(val errorCode: String, val traceId: String?) : RwscaException()