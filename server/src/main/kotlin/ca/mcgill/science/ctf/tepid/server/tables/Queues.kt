package ca.mcgill.science.ctf.tepid.server.tables

import org.jetbrains.exposed.sql.Table

object Queues : Table() {
    /**
     * Unique identifier
     */
    val id = varchar("id", 128).primaryKey()
    /**
     * Display name
     */
    val name = varchar("name", 64)

    val loadBalancer = varchar("load_balancer", 32)
}