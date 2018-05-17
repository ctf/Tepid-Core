package ca.mcgill.science.ctf.tepid.server

import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.utils.Printer
import ca.mcgill.science.ctf.tepid.server.utils.PrinterContract

/**
 * Set of configs to handle specific actions within tepid
 * This should be the only class that needs modification
 */
object Tepid : WithLogging(),
        PrinterContract by Printer {

    /**
     * Configuration method
     * This must be called at least once before any other part of tepid is used
     *
     * Most arguments already have defaults, but the following must be provided:
     * [Configs.dbConfigs], [Configs.baseQuota]
     *
     * See [Configs] for all attributes
     *
     */
    fun configure(action: Configs.() -> Unit) {
        Configs.action()
        Configs.validate()
    }

}