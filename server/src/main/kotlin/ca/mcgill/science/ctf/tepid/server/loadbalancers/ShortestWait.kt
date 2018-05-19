package ca.mcgill.science.ctf.tepid.server.loadbalancers

import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.models.PrintRequest
import ca.mcgill.science.ctf.tepid.server.utils.LoadBalancer
import java.util.concurrent.ConcurrentHashMap

/**
 * Assigns job to the printer deemed to have the lowest wait time
 */
class ShortestWait : LoadBalancer {

    private val endTimes: MutableMap<String, Long> = ConcurrentHashMap()

    // Gets candidate with the lowest logged end time
    override fun select(candidates: List<String>, job: PrintJob, pageCount: Int): String =
            candidates.minBy { endTimes.getOrDefault(it, 0L) }!!

    // Gets the old end time, which is either some time in the future or now,
    // and add the duration for the current request
    override fun register(request: PrintRequest) {
        val oldEnd = Math.max(System.currentTimeMillis(), endTimes.getOrDefault(request.destination, 0L))
        endTimes[request.destination] = oldEnd + (request.pageCount * Configs.pageToMsFactor)
    }

}