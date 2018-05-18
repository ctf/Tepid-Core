package ca.mcgill.science.ctf.tepid.server.utils

import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.models.PrintRequest
import ca.mcgill.science.ctf.tepid.server.tables.Queues
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap

interface LoadBalancer {
    /**
     * Given a collection of ids and a print request,
     * select one of the ids
     *
     * Returned id should be one of the candidates
     */
    fun select(candidates: List<String>, job: PrintJob, pageCount: Int): String

    fun register(request: PrintRequest)

    /**
     * Marks the load balancer as invalid
     * Anything that can be released should be done so here
     */
    fun destroy()
}

class LoadBalancerException(message: String) : TepidException(message)

object LoadBalancers {

    private val balancers: MutableMap<String, LoadBalancer> = ConcurrentHashMap()

    /**
     * Get the load balancer for the specified queue
     * So long as the queue is a valid [Queues.id], a nonnull load balancer will be returned
     */
    operator fun get(queue: String): LoadBalancer? =
            balancers[queue] ?: update(queue)

    /**
     * Destroy the load balancer if it exists, and create a new one based on the [Queues.loadBalancer] key
     *
     * Throws [LoadBalancerException] if a valid queue has an invalid load balancer key
     * See [Configs.loadBalancer]
     */
    fun update(queue: String): LoadBalancer? {
        balancers[queue]?.destroy()
        val balancerId = transaction {
            Queues.select { Queues.id eq queue }.firstOrNull()?.get(Queues.loadBalancer)
        } ?: return null
        val newBalancer = Configs.loadBalancer(balancerId)
                ?: throw LoadBalancerException("$balancerId could not be created for queue $queue")
        balancers[queue] = newBalancer
        return newBalancer
    }

}