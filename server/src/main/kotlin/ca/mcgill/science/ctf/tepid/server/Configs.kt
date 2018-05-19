package ca.mcgill.science.ctf.tepid.server

import ca.allanwang.kit.logger.WithLogging
import ca.allanwang.kit.utils.os
import ca.mcgill.science.ctf.tepid.server.Configs.baseQuota
import ca.mcgill.science.ctf.tepid.server.Configs.dbConfigs
import ca.mcgill.science.ctf.tepid.server.models.DbConfigs
import ca.mcgill.science.ctf.tepid.server.models.PrintRequest
import ca.mcgill.science.ctf.tepid.server.models.connect
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import ca.mcgill.science.ctf.tepid.server.utils.LoadBalancer
import ca.mcgill.science.ctf.tepid.server.utils.TepidException
import ca.mcgill.science.ctf.tepid.server.utils.Utils
import java.io.File
import java.math.BigInteger
import java.util.concurrent.TimeUnit

/**
 * Set of configs to handle specific actions within tepid
 * Update using [Tepid.configure]
 *
 * Most arguments already have defaults, but the following must be provided:
 * [dbConfigs], [baseQuota]
 *
 * Given the nature of the printing system, every function here should be thread safe
 */
object Configs : WithLogging() {

    /*
     * ---------------------------------------------------
     * Mandatory Configs
     * ---------------------------------------------------
     */

    /**
     * Configurations used to connect to the database
     */
    var dbConfigs: DbConfigs? = null
        set(value) {
            field = value
            if (value == null)
                fail("DbConfigs cannot be null")
            value.connect()
        }

    /**
     * Function to get the base quota of a user
     */
    lateinit var baseQuota: (shortUser: String) -> Int

    /*
     * ---------------------------------------------------
     * Optional Configs
     * ---------------------------------------------------
     */

    /**
     * Unique id generator
     */
    var generateId: () -> String = {
        BigInteger(130, Utils.secureRandom).toString(32)
    }

    /**
     * Directly in which all pending postscript files will be created
     */
    var tmpDir: File = File(os(
            windows = "${System.getProperty("java.io.tmpdir")}\\tepid",
            linux = "/tmp/tepid").value)

    /**
     * Given an identifier, create a load balancer
     */
    var loadBalancer: (key: String) -> LoadBalancer? = LoadBalancer.Companion::fromName

    /**
     * General estimation factor used by the load balancers
     * Value should be the duration in ms to print a single page
     */
    var pageToMsFactor: Long = 1500

    /**
     * Frequency in ms to check for job updates
     * Used in [PrintJobs.watch]
     */
    var jobWatcherFrequency: Long = 1000

    /**
     * Time in ms to keep postscript files
     * Defaults to 1 day
     */
    var expiration: Long = TimeUnit.DAYS.toMillis(1)

    /**
     * The amount of quota deducted for printing one page in colour
     * By default, monochrome pages = 1 quota, colour pages = 3 quota
     */
    var colourPageValue: Int = 3

    /**
     * Given a print job, execute the command to print
     * Return [true] if successful, and [false] otherwise
     * Defaults to just logging the request
     */
    var print: (job: PrintRequest) -> Boolean = { log.info("PrintImpl: $it"); true }

    internal fun validate() {
        dbConfigs ?: fail("DbConfigs not supplied")
        if (!this::baseQuota.isInitialized) fail("BaseQuota function not initialized")
    }

    private fun fail(message: String): Nothing = throw ConfigException(message)

    class ConfigException(message: String) : TepidException(message)
}

