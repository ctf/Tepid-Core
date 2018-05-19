package ca.mcgill.science.ctf.tepid.server.internal

import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SchemaUtils.drop
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.concurrent.ThreadLocalRandom
import kotlin.test.fail

fun <T> testTransaction(reset: Boolean = true, log: Boolean = true, statement: Transaction.() -> T) = try {
    transaction {
        if (reset) {
            drop(PrintJobs)
            create(PrintJobs)
        }
        if (log)
            logger.addLogger(StdOutSqlLogger)
        statement()
    }
} catch (e: Exception) {
    fail("Transaction failed: ${e.message}")
}

fun sleep(min: Int, max: Int) {
    val duration = ThreadLocalRandom.current().nextInt(max - min) + min
    Thread.sleep(duration.toLong())
}

const val testUser = "test1"

fun PrintJobs.testCreate(id: String, shortUser: String = testUser): PrintJob {
    val job = PrintJob(id, "Job $id", shortUser)
    PrintJobs.create(job, File("tepid-${job.id}.ps"))
    return job
}