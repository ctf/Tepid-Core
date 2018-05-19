package ca.mcgill.science.ctf.tepid.server.internal

import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.models.PrintStage
import ca.mcgill.science.ctf.tepid.server.models.Printed
import ca.mcgill.science.ctf.tepid.server.tables.Destinations
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import ca.mcgill.science.ctf.tepid.server.tables.Queues
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.apache.logging.log4j.Logger
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SchemaUtils.drop
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.test.fail

fun <T> testTransaction(log: Boolean = true,
                        tables: Array<Table> = arrayOf(PrintJobs, Queues, Destinations),
                        statement: Transaction.() -> T): T = try {
    transaction {
        if (tables.isNotEmpty()) {
            drop(*tables)
            create(*tables)
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

fun resource(path: String) = File(TestConfigs::class.java.classLoader.getResource(path).file)

val testPs = resource("ps/test.ps")

/**
 * Watch job on a separate thread, locked by the [callback]
 */
inline fun watch(id: String, callback: CompletableCallback, crossinline action: (prev: PrintStage?, current: PrintStage) -> Unit) {
    var stage: PrintStage? = null
    PrintJobs.watch(id).timeout(10, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).subscribeBy(
            onNext = {
                action(stage, it)
                stage = it
            },
            onComplete = {
                callback.onComplete()
            },
            onError = {
                callback.onError(it)
            }
    )
}

fun PrintJob.assertPrinted(after: Long = 1, logger: Logger? = null) {
    val stage = stage() as? Printed ?: fail("Job did not print")
    assertTrue(after < stage.time, "No job printed after $after ms")
    if (logger != null) {
        logger.info("Sent $name to ${stage.destination}")
    }
}