package ca.mcgill.science.ctf.tepid.server.tables

import ca.allanwang.kit.logger.Loggable
import ca.allanwang.kit.logger.WithLogging
import ca.allanwang.kit.rx.RxWatcher
import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.models.*
import ca.mcgill.science.ctf.tepid.server.utils.Printer
import ca.mcgill.science.ctf.tepid.server.utils.TepidException
import io.reactivex.Observable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

interface PrintJobsContract {
    /**
     * Insert the job into the db
     */
    fun create(job: PrintJob, file: File)

    /**
     * Remove all files older than [expiration] ms
     * Defaults to [Configs.expiration]
     * Those that are purged will be flagged as [PrintJobs.deleted]
     * Returns number of rows updated
     */
    fun purge(expiration: Long = Configs.expiration): Int

    /**
     * Gets the result row associated with the id if it exists
     * Note that this is not wrapped in a [transaction]
     */
    operator fun get(id: String): ResultRow?

    /**
     * Mark the job as received
     */
    fun received(id: String): Boolean

    /**
     * Mark the job as processed
     */
    fun processed(id: String, pageCount: Int, colourPageCount: Int): Boolean

    /**
     * Mark the job as printed
     */
    fun printed(id: String, destination: String, cost: Int): Boolean

    /**
     * Markt he job as failed
     */
    fun failed(id: String, message: String): Boolean

    /**
     * Gets the total quota cost used by the short user
     * Jobs counted are those that have printed and are not refunded
     */
    fun getTotalQuotaUsed(shortUser: String): Int

    /**
     * Helper function to update the print job of the given [id]
     *
     * Returns [true] if a row was updated in the transaction, and [false] otherwise
     */
    fun update(id: String, action: PrintJobs.(UpdateStatement) -> Unit): Boolean

    /**
     * See [update]
     */
    fun update(job: PrintJob, action: PrintJobs.(UpdateStatement) -> Unit): Boolean =
            PrintJobs.update(job.id, action)

    /**
     * Receives the print stage for the supplied job
     * See [PrintStage] for class options
     */
    fun stage(id: String): PrintStage

    /**
     * Returns an observable that emits the print stages of the job with the supplied [id]
     * See [PrintStage] for class options
     * Emissions are polled every [Configs.jobWatcherFrequency] ms
     */
    fun watch(id: String): Observable<PrintStage>
}

object PrintJobs : Table(), PrintJobsContract, Loggable by WithLogging("PrintJobs") {
    val id = varchar("id", 128).primaryKey()
    val shortUser = varchar("shortUser", 64)
    val name = varchar("name", 128)
    val file = varchar("file", 256)

    /**
     * Destination to which the job should be printed to
     * Initialized if [printed]
     */
    val destination = varchar("destination", 128).nullable()
    /**
     * Total page count of the job
     * Initialized if [processed]
     */
    val pageCount = integer("page_count").default(0)
    /**
     * Total colour page count of the job
     * Initialized if [processed]
     */
    val colourPageCount = integer("colour_page_count").default(0)
    /**
     * Total cost of the job based on [Configs.colourPageValue]
     * Initialized if [printed]
     */
    val quotaCost = integer("quota_cost").default(0)
    /**
     * Boolean denoting whether [quotaCost] should be considered in the usage sum
     */
    val refunded = bool("refunded").default(false)

    val deleted = bool("deleted").default(false)

    /*
     * Time stamps
     */

    val created = long("created").clientDefault { System.currentTimeMillis() }
    val received = long("received").default(-1)
    val processed = long("processed").default(-1)
    val printed = long("printed").default(-1)
    val failed = long("failed").default(-1)

    /**
     * Print job error message
     * Initialized if [failed]
     * See [Printer] for some error string constants
     */
    val error = varchar("error", 128).nullable()

    override fun create(job: PrintJob, file: File): Unit = transaction {
        insert {
            it[id] = job.id
            it[shortUser] = job.shortUser
            it[name] = job.name
            it[this.file] = file.absolutePath
        }
    }

    override fun purge(expiration: Long): Int {
        val purgeTime = System.currentTimeMillis() - expiration
        val files = transaction {
            select { (created lessEq purgeTime) and (deleted eq false) }.map {
                it[file]
            }
        }
        files.map(::File).filter(File::isFile).forEach { it.delete() }
        val updateCount = transaction {
            update({ (created lessEq purgeTime) and (deleted eq false) }) {
                it[deleted] = true
            }
        }
        if (updateCount != files.size)
            log.warn("Update count $updateCount does not match purge size ${files.size}")
        return updateCount
    }

    override operator fun get(id: String): ResultRow? =
            select { PrintJobs.id eq id }.firstOrNull()

    /*
     * Updaters
     */

    override fun received(id: String) = update(id) {
        it[received] = System.currentTimeMillis()
    }

    override fun processed(id: String, pageCount: Int, colourPageCount: Int): Boolean {
        if (pageCount < colourPageCount)
            throw TepidException("PrintJob $id: page count ($pageCount) cannot be less than colour page count ($colourPageCount)")
        return update(id) {
            it[processed] = System.currentTimeMillis()
            it[this.pageCount] = pageCount
            it[this.colourPageCount] = colourPageCount
        }
    }

    override fun printed(id: String, destination: String, cost: Int) = update(id) {
        it[printed] = System.currentTimeMillis()
        it[this.destination] = destination
        it[quotaCost] = cost
    }

    override fun failed(id: String, message: String) = update(id) {
        it[failed] = System.currentTimeMillis()
        it[error] = message
    }


    override fun getTotalQuotaUsed(shortUser: String): Int = transaction {
        select { (PrintJobs.shortUser eq shortUser) and (printed neq -1) and (refunded eq false) }.sumBy { it[quotaCost] }
    }

    override fun update(id: String, action: PrintJobs.(UpdateStatement) -> Unit): Boolean =
            transaction { update({ PrintJobs.id eq id }, 1, action) == 1 }


    override fun stage(id: String): PrintStage = transaction {
        get(id)?.run {

            // lazy retrievers
            val failed: Long by lazy { this[PrintJobs.failed] }
            val printed: Long by lazy { this[PrintJobs.printed] }
            val received: Long by lazy { this[PrintJobs.received] }
            val processed: Long by lazy { this[PrintJobs.processed] }
            val created: Long by lazy { this[PrintJobs.created] }

            // valid if processed or printed
            val destination: String by lazy { this[PrintJobs.destination]!! }
            // valid if failed
            val error: String by lazy { this[PrintJobs.error]!! }

            val pageCount: Int by lazy { this[PrintJobs.pageCount] }
            val colourPageCount: Int by lazy { this[PrintJobs.colourPageCount] }

            val fileSize: Long by lazy {
                try {
                    val file = File(this[file])
                    if (file.isFile)
                        file.length()
                    else
                        0L
                } catch (e: Exception) {
                    0L
                }
            }

            when {
                failed != -1L -> Failed(failed, error)
                printed != -1L -> Printed(printed, destination, pageCount, colourPageCount)
                processed != -1L -> Processed(processed)
                received != -1L -> Received(received, fileSize)
                else -> Created(created)
            }
        } ?: NotFound
    }

    private val watcher: PrintStageWatcher by lazy { PrintStageWatcher() }

    override fun watch(id: String) = watcher.watch(id)

}

class PrintStageWatcher(
        override val pollingInterval: Long = Configs.jobWatcherFrequency
) : RxWatcher<String, PrintStage>() {
    override val timeoutDuration: Long = 120000L

    override fun emit(id: String): PrintStage = PrintJobs.stage(id)

    override fun isCompleted(id: String, value: PrintStage): Boolean = value.finished
}