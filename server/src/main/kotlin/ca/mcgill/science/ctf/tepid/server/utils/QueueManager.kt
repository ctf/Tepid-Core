package ca.mcgill.science.ctf.tepid.server.utils

import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.models.PrintRequest
import ca.mcgill.science.ctf.tepid.server.tables.Destinations
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import ca.mcgill.science.ctf.tepid.server.tables.Queues
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

interface QueueManagerContract {

    /**
     * Pure function to retrieve a destination candidate
     */
    fun getDestination(queue: String,  job: PrintJob, pageCount: Int): String?

    /**
     * Consumer to register a new print job
     * Used to keep track of destination loads
     */
    fun registerJob(request: PrintRequest)

}

object QueueManager : QueueManagerContract, WithLogging() {
    override fun getDestination(queue: String, job: PrintJob, pageCount: Int): String? {
        val candidates = transaction {
            Destinations.select { (Destinations.queue eq queue) and (Destinations.up eq true) }
                    .map { it[Destinations.id] }
        }
        val balancer = transaction { Queues.select { Queues.id eq queue }.firstOrNull()?.get(Queues.loadBalancer) } ?: return null
        return Configs.loadBalancer(balancer)?.select(candidates, job, pageCount)
    }

    override fun registerJob(request: PrintRequest) {
        LoadBalancers[request.destination]?.register(request)
    }
}

