package ca.mcgill.science.ctf.tepid.server.tables

import ca.mcgill.science.ctf.tepid.server.utils.LoadBalancer
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

    val loadBalancer = varchar("load_balancer", 32).default(LoadBalancer.DEFAULT)
}