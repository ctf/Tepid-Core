package ca.mcgill.science.ctf.tepid.server

import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.loadbalancers.RoundRobin
import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.models.PrintRequest
import ca.mcgill.science.ctf.tepid.server.utils.LoadBalancer
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * In memory tests for load balancer ordering
 */
class LoadBalancerTest : WithLogging() {

    private val candidates: List<String> = (0..5).printerSequence()

    private fun Iterable<Int>.printerSequence() = map { "printer$it" }

    /**
     * Create jobs by index with supplied page count, process and register in the load balancer, and return destination
     */
    private fun LoadBalancer.processJobs(vararg pageCounts: Int,
                                         action: (index: Int, request: PrintRequest) -> Unit = { index, request ->
                                             log.info("Processed $index: ${request.job.id} to ${request.destination}")
                                         }): List<String> =
            pageCounts.mapIndexed { index, pageCount ->
                val job = PrintJob(id = "jobId$index",
                        name = "Job $index",
                        shortUser = "test1")
                val destination = select(candidates, job, pageCount)
                val request = PrintRequest(job, File("job$index.ps"), destination, pageCount, 0)
                action(index, request)
                register(request)
                destination
            }

    @Test
    fun roundRobin() {
        val pageCounts = intArrayOf(3, 5, 7, 9, 11, 13, 15, 17)
        val actual = RoundRobin().processJobs(*pageCounts)
        // just cycles through
        val expected = (0 until pageCounts.size).map { candidates[it % candidates.size] }
        assertEquals(expected, actual, "Basic round robin failed")
    }

}