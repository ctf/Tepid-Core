package ca.mcgill.science.ctf.tepid.server.tables

import ca.allanwang.kit.rx.RxWatcher
import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.models.*
import ca.mcgill.science.ctf.tepid.server.utils.Printer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object PrintJobs : Table() {
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

    fun create(job: PrintJob): Unit = transaction {
        insert {
            it[id] = job.id
            it[shortUser] = job.shortUser
            it[name] = job.name
            it[file] = job.file.absolutePath
        }
    }

    private fun get(id: String): ResultRow? =
            select { PrintJobs.id eq id }.firstOrNull()

    /**
     * Gets the total quota cost used by the short user
     * Jobs counted are those that have printed and are not refunded
     */
    fun getTotalQuotaUsed(shortUser: String): Int = transaction {
        select { (PrintJobs.shortUser eq shortUser) and (printed neq -1) and (refunded eq false) }.sumBy { it[quotaCost] }
    }

    /**
     * Helper function to update the print job of the given [id]
     *
     * Returns [true] if a row was updated in the transaction, and [false] otherwise
     */
    fun update(id: String, action: PrintJobs.(UpdateStatement) -> Unit): Boolean =
            update({ PrintJobs.id eq id }, 1, action) == 1

    /**
     * Receives the print stage for the supplied job
     * See [PrintStage] for class options
     */
    fun stage(id: String): PrintStage = transaction {
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
                received != -1L -> Received(received, fileSize)
                processed != -1L -> Processed(processed)
                else -> Created(created)
            }
        } ?: NotFound
    }

    private val watcher = PrintStageWatcher()

    fun watch(id: String) = watcher.watch(id)

}

class PrintStageWatcher : RxWatcher<String, PrintStage>() {
    override val pollingInterval: Long = 1000L
    override val timeoutDuration: Long = 120000L

    override fun emit(id: String): PrintStage = PrintJobs.stage(id)

    override fun isCompleted(id: String, value: PrintStage): Boolean = value.finished
}