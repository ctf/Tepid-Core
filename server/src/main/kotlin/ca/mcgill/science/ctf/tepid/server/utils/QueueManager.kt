package ca.mcgill.science.ctf.tepid.server.utils

interface QueueManagerContract {

    /**
     * Pure function to retrieve a destination candidate
     */
    fun getDestination(queueName: String, pageCount: Int): String?

    /**
     * Consumer to register a new print job
     * Used to keep track of destination loads
     */
    fun registerJob(destination: String, pageCount: Int)

}

object QueueManager : QueueManagerContract {
    override fun getDestination(queueName: String, pageCount: Int): String? {
        TODO("not implemented")
    }

    override fun registerJob(destination: String, pageCount: Int) {
        TODO("not implemented")
    }
}