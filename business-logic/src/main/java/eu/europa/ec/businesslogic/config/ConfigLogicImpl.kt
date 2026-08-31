package eu.europa.ec.businesslogic.config

import org.sprind.wallet.businesslogic.config.EnvironmentConfigImpl

class ConfigLogicImpl : ConfigLogic {

    override val environmentConfig: EnvironmentConfig
        get() = EnvironmentConfigImpl()

}
