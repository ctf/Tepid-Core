package ca.mcgill.science.ctf.tepid.server.internal

import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SchemaUtils.drop
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
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