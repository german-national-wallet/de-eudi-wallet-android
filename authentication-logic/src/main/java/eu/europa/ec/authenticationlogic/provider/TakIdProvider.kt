
package eu.europa.ec.authenticationlogic.provider

import eu.europa.ec.businesslogic.controller.log.LogController

interface TakIdProvider {
    var takId: String?
}

internal class TakIdProviderImpl(private val logController: LogController) : TakIdProvider {
    private val logTag = javaClass.simpleName

    override var takId: String? = null
        set(value) {
            logController.d(logTag) { "takId: $value" }
            field = value
        }
}