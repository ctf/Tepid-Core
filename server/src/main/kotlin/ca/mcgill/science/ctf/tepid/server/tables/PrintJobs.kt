package ca.mcgill.science.ctf.tepid.server.tables

import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object PrintJobs : Table() {
    val id = varchar("id", 128).primaryKey()
    val shortUser = varchar("shortUser", 64)
    val name = varchar("name", 128)
    val file = varchar("file", 256)

    val destination = varchar("destination", 128).nullable()
    val pageCount = integer("page_count").default(0)
    val colourPageCount = integer("colour_page_count").default(0)
    val created = long("created").clientDefault { System.currentTimeMillis() }
    val received = long("received").default(-1)
    val processed = long("processed").default(-1)
    val printed = long("printed").default(-1)
    val failed = long("failed").default(-1)
    val error = varchar("error", 128).nullable()

    fun create(job: PrintJob): Unit = transaction {
        insert {
            it[id] = job.id
            it[shortUser] = job.shortUser
            it[name] = job.name
            it[file] = job.file.absolutePath
        }
    }
}