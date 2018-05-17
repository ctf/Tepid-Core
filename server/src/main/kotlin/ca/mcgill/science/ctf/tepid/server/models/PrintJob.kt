package ca.mcgill.science.ctf.tepid.server.models

import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import ca.mcgill.science.ctf.tepid.server.utils.Printer
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
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
        val shortUser: String,
        /**
         * Processed compressed postscript file
         * Note that to print, the file must be decompressed.
         * Decompression will be handled internally and sent through
         * [PrintRequest.file]
         */
        val file: File) : PrintResponse() {

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

    fun hasSufficientQuota(quota: Int) = quotaCost < quota
}

/**
 * Stage info for a given print job
 */
sealed class PrintStage

data class Created(val time: Long) : PrintStage()
data class Processed(val time: Long) : PrintStage()
data class Received(val time: Long, val fileSize: Long) : PrintStage()
data class Printed(val time: Long, val destination: String, val pageCount: Int, val colourPageCount: Int) : PrintStage()
data class Failed(val time: Long, val message: String) : PrintStage()
object NotFound : PrintStage()