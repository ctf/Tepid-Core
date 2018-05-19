package ca.mcgill.science.ctf.tepid.server.models

import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import ca.mcgill.science.ctf.tepid.server.utils.Printer
import java.io.File

/**
 * Return type for [Printer.print]
 */
sealed class PrintResponse

/**
 * Immutable structure containing print job attributes and db getters
 */
data class PrintJob(
        /**
         * Unique id for the job
         */
        val id: String,
        /**
         * Name of the print job
         * Does not have to be unique
         */
        val name: String,
        /**
         * Unique identifier for the user who is printing
         */
        val shortUser: String) : PrintResponse() {

    /**
     * Fetch the current print job stage from the db
     */
    fun stage(): PrintStage = PrintJobs.stage(id)

}

data class PrintError(
        val message: String,
        val timeStamp: Long = System.currentTimeMillis()) : PrintResponse()

/**
 * Contains all the necessary info to print an actual job
 *
 * [job] attributes for the print job
 * [file] location of temp file to print
 * [destination] unique identifier for destination to print to
 * [pageCount] total page count
 * [colourPageCount] page count for coloured pages
 */
data class PrintRequest(val job: PrintJob,
                        val file: File,
                        val destination: String,
                        val pageCount: Int,
                        val colourPageCount: Int) {

    /**
     * The quota amount that will be deducted if this page prints
     */
    val quotaCost: Int
        get() = (pageCount - colourPageCount) + colourPageCount * Configs.colourPageValue

    /**
     * Fetches quota from database and check if it is greater than the cost
     */
    fun hasSufficientQuota(): Boolean {
        val baseQuota = Configs.baseQuota(job.shortUser)
        val quotaUsed = PrintJobs.getTotalQuotaUsed(job.shortUser)
        return hasSufficientQuota(baseQuota - quotaUsed)
    }

    fun hasSufficientQuota(quota: Int): Boolean = quotaCost < quota
}

/**
 * Stage info for a given print job
 * [finished] means that the stage will no longer change
 */
sealed class PrintStage(val finished: Boolean)

data class Created(val time: Long) : PrintStage(false)
data class Processed(val time: Long) : PrintStage(false)
data class Received(val time: Long, val fileSize: Long) : PrintStage(false)
data class Printed(val time: Long, val destination: String, val pageCount: Int, val colourPageCount: Int) : PrintStage(true)
data class Failed(val time: Long, val message: String) : PrintStage(true)
object NotFound : PrintStage(true) {
    override fun toString(): String = "NotFound"
}