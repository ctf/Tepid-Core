package ca.mcgill.science.ctf.tepid.server.tables

import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.utils.Printer
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

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

    /**
     * Gets the total quota cost used by the short user
     * Jobs counted are those that have printed and are not refunded
     */
    fun getTotalQuotaUsed(shortUser: String): Int = transaction {
        select { (PrintJobs.shortUser eq shortUser) and (printed neq -1) and (refunded eq false) }.sumBy { it[quotaCost] }
    }
}