package eu.europa.ec.businesslogic.config

class ConfigLogicImpl(override val environmentConfig: EnvironmentConfig) : ConfigLogic


interface LogConfig {

    /**
     * Whether logs are printed to logcat.
     */
    val isLogcatEnabled: Boolean

    /**
     * Whether logs are written to file, from where the debug menu can export them.
     */
    val isLogWriterEnabled: Boolean

}

/**
 * Default values are false
 */
class LogConfigImpl(
    override val isLogcatEnabled: Boolean = false,
    override val isLogWriterEnabled: Boolean = false,
) : LogConfig
