package ca.mcgill.science.ctf.tepid.server.tables

import org.jetbrains.exposed.sql.Table

object PrintJobs : Table() {
    val id = varchar("id", 128).primaryKey()
    val shortUser = varchar("shortUser", 64)
    val name = varchar("name", 128)
    val destination = varchar("destination", 128)
    val file = varchar("file", 256)
val test = long().default(-1)
}