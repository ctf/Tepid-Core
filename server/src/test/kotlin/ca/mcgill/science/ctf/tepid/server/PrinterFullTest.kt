package ca.mcgill.science.ctf.tepid.server

import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.internal.*
import ca.mcgill.science.ctf.tepid.server.models.*
import ca.mcgill.science.ctf.tepid.server.tables.Destinations
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import ca.mcgill.science.ctf.tepid.server.tables.Queues
import ca.mcgill.science.ctf.tepid.server.utils.Printer
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.fail

/**
 * Tests for the full printing lifecycle
 */
class PrinterFullTest {

    companion object : WithLogging() {
        @BeforeClass
        @JvmStatic
        fun before() {
            TestConfigs.setup()
            transaction {
                Queues.insert {
                    it[id] = testQueue
                    it[name] = "Test Queue"
                }
                Destinations.run {
                    batchInsert(destinations) {
                        this[id] = it
                        this[name] = it
                        this[queue] = testQueue
                        this[up] = it in upDestinations
                    }
                }
                log.info("Created destinations: ${Destinations.selectAll().joinToString("\n\t", prefix = "\n\t")}")
            }
            log.info("Finished set up\n\n")
        }

        const val testQueue = "test_queue"
        val destinations = (0..5).map { "destination$it" }
        val upDestinations = (0..5 step 2).map { "destination$it" }

        private val testValidator: Validator<PrintRequest> = {
            with(it) {
                when {
                    destination !in upDestinations -> Invalid("Destination $destination not valid")
                    pageCount == 0 -> Invalid("Page count not updated")
                    pageCount < colourPageCount -> Invalid("Page count less than colour page count")
                    !file.isFile -> Invalid("File was not created")
                    else -> Valid
                }
            }
        }

        private val validator = validate(Printer.hasSufficientQuota, testValidator)

        @AfterClass
        @JvmStatic
        fun after() {
            println("\n\n")
            log.info("Cleaning up")
            val count = PrintJobs.purge(0) // purge all files
            log.info("Purged $count")
        }
    }

    @Test
    fun printSingleJob() {
        val now = System.currentTimeMillis()
        val result = Printer.print("Single Job", testUser, testQueue, testPs.inputStream(), validator)
        if (result !is PrintJob)
            fail("Print response was an error: $result")
        concurrentTest { callback ->
            watch(result.id, callback) { _, current ->
                log.info("Stage $current")
            }
        }
        result.assertPrinted(now, logger = log)
    }

}