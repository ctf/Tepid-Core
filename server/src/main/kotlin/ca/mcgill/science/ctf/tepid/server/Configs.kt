package ca.mcgill.science.ctf.tepid.server

import ca.allanwang.kit.logger.WithLogging
import ca.allanwang.kit.utils.os
import ca.mcgill.science.ctf.tepid.server.models.DbConfigs
import ca.mcgill.science.ctf.tepid.server.models.PrintRequest
import ca.mcgill.science.ctf.tepid.server.models.connect
import ca.mcgill.science.ctf.tepid.server.utils.Utils
import java.io.File
import java.math.BigInteger

/**
 * Set of configs to handle specific actions within tepid
 * Update using [Tepid.configure]
 */
object Configs : WithLogging() {

    /**
     * Unique id generator
     */
    var generateId: () -> String = {
        BigInteger(130, Utils.random).toString(32)
    }

    /**
     * Directly in which all pending postscript files will be created
     */
    var tmpDir: File = File(os(
            windows = "${System.getProperty("java.io.tmpdir")}\\tepid",
            linux = "/tmp/tepid").value)

    /**
     * Time in ms to keep postscript files
     * Defaults to 1 day
     */
    var expiration: Long = 1L * 24 * 60 * 60 * 100

    /**
     * The amount of quota deducted for printing one page in colour
     * By default, monochrome pages = 1 quota, colour pages = 3 quota
     */
    var colourPageValue: Int = 3

    /**
     * Configurations used to connect to the database
     */
    var dbConfigs: DbConfigs? = null
        set(value) {
            field = value
            if (value == null)
                throw RuntimeException("DbConfigs cannot be null")
            value.connect()
        }

    /**
     * Function to get the base quota of a user
     * This will be called from many threads
     */
    lateinit var baseQuota: (shortUser: String) -> Int

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

    class ConfigException(override val message: String) : RuntimeException(message)
}

