package ca.mcgill.science.ctf.tepid.server.tables

import org.jetbrains.exposed.sql.Table

object Destinations : Table() {
    /**
     * Unique identifier
     */
    val id = varchar("id", 128).primaryKey()
    /**
     * Display name
     */
    val name = varchar("name", 64)
    /**
     * Queue identifier (used to group destinations)
     */
    val queue = varchar("queue", 64) references Queues.id
    /**
     * Whether or not the printer is up and running
     */
    val up = bool("up").default(false)
}