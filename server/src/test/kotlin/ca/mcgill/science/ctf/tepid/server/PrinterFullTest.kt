package ca.mcgill.science.ctf.tepid.server

import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.internal.*
import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.models.PrintRequest
import ca.mcgill.science.ctf.tepid.server.models.Valid
import ca.mcgill.science.ctf.tepid.server.models.validate
import ca.mcgill.science.ctf.tepid.server.tables.Destinations
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import ca.mcgill.science.ctf.tepid.server.tables.Queues
import ca.mcgill.science.ctf.tepid.server.utils.Printer
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertTrue
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

        private fun PrintRequest.validate() {
            assertTrue(destination in upDestinations, "Destination is not valid")
            assertTrue(pageCount > 0, "Page count not updated")
            assertTrue(pageCount >= colourPageCount, "Page count less than colour page count")
            assertTrue(file.isFile, "File was not created")
        }

        private val validator = validate<PrintRequest>(Printer.hasSufficientQuota, { it.validate(); Valid })

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