package ca.mcgill.science.ctf.tepid.server.tables

import ca.allanwang.kit.logger.Loggable
import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.utils.LoadBalancer
import ca.mcgill.science.ctf.tepid.server.utils.LoadBalancers
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

interface QueuesContract {
    /**
     * Save the new load balancer in the db if it's valid and reload it
     * Returns [true] if the load balancer has changed, and [false] otherwise
     */
    fun updateLoadBalancer(id: String, loadBalancer: String): Boolean
}

object Queues : Table(), QueuesContract, Loggable by WithLogging("Queues") {
    /**
     * Unique identifier
     */
    val id = varchar("id", 128).primaryKey()
    /**
     * Display name
     */
    val name = varchar("name", 64)

    val loadBalancer = varchar("load_balancer", 32).default(LoadBalancer.DEFAULT)

    override fun updateLoadBalancer(id: String, loadBalancer: String): Boolean {
        val oldBalancerName = transaction {
            Queues.select { Queues.id eq id }.firstOrNull()?.get(Queues.loadBalancer)
        }
        if (oldBalancerName == null) {
            log.error("Could not find queue $id; failed to update load balancer")
            return false
        }
        val newBalancer = Configs.loadBalancer(loadBalancer)
        if (newBalancer == null) {
            log.error("Could not find load balancer $loadBalancer for queue $id; aborting update")
            return false
        }
        transaction {
            Queues.update({ Queues.id eq id }, 1) {
                it[Queues.loadBalancer] = loadBalancer
            }
        }
        LoadBalancers.update(id, newBalancer)
        return true
    }
}