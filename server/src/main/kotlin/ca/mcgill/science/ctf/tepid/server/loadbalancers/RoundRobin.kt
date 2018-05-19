package ca.mcgill.science.ctf.tepid.server.loadbalancers

import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.models.PrintRequest
import ca.mcgill.science.ctf.tepid.server.utils.LoadBalancer

/**
 * Loops through candidates by index to assign the next job
 * No consideration is done with regards to the actual job or page count
 *
 * If the old index cannot be found, it starts back at candidate 0
 */
class RoundRobin : LoadBalancer {

    private var lastUsed = ""

    override fun select(candidates: List<String>, job: PrintJob, pageCount: Int): String {
        val oldIndex = candidates.indexOf(lastUsed) // -1 if not found
        return candidates[(oldIndex + 1) % candidates.size]
    }

    override fun register(request: PrintRequest) {
        lastUsed = request.destination
    }
}