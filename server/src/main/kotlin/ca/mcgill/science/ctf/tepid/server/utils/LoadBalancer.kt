package ca.mcgill.science.ctf.tepid.server.utils

import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.loadbalancers.RoundRobin
import ca.mcgill.science.ctf.tepid.server.loadbalancers.ShortestWait
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
     *
     * By implementation, the list will have at least 2 candidates
     * 0 candidates will lead to an automatic rejection,
     * 1 candidate will lead to an automatic selection
     */
    fun select(candidates: List<String>, job: PrintJob, pageCount: Int): String

    fun register(request: PrintRequest)

    companion object {
        const val DEFAULT = "default"
        const val ROUND_ROBIN = "round_robin"
        const val SHORTEST_WAIT = "shortest_wait"

        /**
         * Get one of the packaged load balancers by key
         */
        fun fromName(name: String): LoadBalancer? = when (name) {
            DEFAULT -> RoundRobin()
            ROUND_ROBIN -> RoundRobin()
            SHORTEST_WAIT -> ShortestWait()
            else -> null
        }
    }

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
        val balancerId = transaction {
            Queues.select { Queues.id eq queue }.firstOrNull()?.get(Queues.loadBalancer)
        } ?: return null
        val newBalancer = Configs.loadBalancer(balancerId)
                ?: throw LoadBalancerException("$balancerId could not be created for queue $queue")
        balancers[queue] = newBalancer
        return newBalancer
    }

}