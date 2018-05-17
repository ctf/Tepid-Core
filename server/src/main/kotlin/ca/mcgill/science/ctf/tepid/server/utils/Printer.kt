package ca.mcgill.science.ctf.tepid.server.utils

import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.models.*
import java.io.*
import java.util.concurrent.*

interface PrinterContract {
    /**
     * Validates a job request and produces the job entity or an error
     * [jobName] display name for the request
     *
     */
    fun print(jobName: String,
              shortUser: String,
              colourEnabled: Boolean,
              quota: Int,
              stream: InputStream): PrintResponse
}

object Printer : PrinterContract, WithLogging() {

    private inline val tmpDir:File
            get() = Configs.tmpDir

    override fun print(jobName: String,
              shortUser: String,
              colourEnabled: Boolean,
              quota: Int,
              stream: InputStream): PrintResponse {
        val id = Configs.generateId()
        log.debug("Receiving job data $id")
        if (!tmpDir.exists() && !tmpDir.mkdirs()) {
            log.error("Failed to create tmp path ${tmpDir.absolutePath}")
            return PrintError("Failed to create tmp path")
        }

        try {
            // todo test and validate
            //write compressed job to disk
            val tmpXz = File("${tmpDir.absolutePath}/$id.ps.xz")
            tmpXz.copyFrom(stream)
            //let db know we have received data
            CouchDb.updateWithResponse<PrintJob>(id) {
                file = tmpXz.absolutePath
                log.info("Updating job $id with path $file")
                received = System.currentTimeMillis()
            }

            submit(id) {

                /*
                 * Note that this is a runnable that will be submitted to the executor service
                 * This block does not run in the same thread!
                 */

                // Generates a random file name with our prefix and suffix
                val tmp = File.createTempFile("tepid", ".ps")
                try {
                    //decompress data
                    val decompress = XZInputStream(FileInputStream(tmpXz))
                    tmp.copyFrom(decompress)

                    // Detect PostScript monochrome instruction
                    val br = BufferedReader(FileReader(tmp.absolutePath))
                    val now = System.currentTimeMillis()
                    val psMonochrome = br.isMonochrome()
                    log.trace("Detected ${if (psMonochrome) "monochrome" else "colour"} for job $id in ${System.currentTimeMillis() - now} ms")
                    //count pages
                    val psInfo = Gs.psInfo(tmp) ?: throw PrintException("Internal Error")
                    val color = if (psMonochrome) 0 else psInfo.colourPages
                    log.trace("Job $id has ${psInfo.pages} pages, $color in color")

                    //update page count and status in db
                    var j2: PrintJob = CouchDb.update(id) {
                        pages = psInfo.pages
                        colorPages = color
                        processed = System.currentTimeMillis()
                    } ?: throw PrintException("Could not update")

                    //check if user has color printing enabled
                    if (color > 0 && SessionManager.queryUser(j2.userIdentification, null)?.colorPrinting != true)
                        throw PrintException(PrintError.COLOR_DISABLED)

                    //check if user has sufficient quota to print this job
                    if (Users.getQuota(j2.userIdentification) < psInfo.pages + color * 2)
                        throw PrintException(PrintError.INSUFFICIENT_QUOTA)

                    //add job to the queue
                    j2 = QueueManager.assignDestination(id)
                    //todo check destination field
                    val destination = j2.destination ?: throw PrintException(PrintError.INVALID_DESTINATION)

                    val dest = CouchDb.path(destination).getJson<FullDestination>()
                    if (sendToSMB(tmp, dest, debug)) {
                        j2.printed = System.currentTimeMillis()
                        CouchDb.path(id).putJson(j2)
                        log.info("${j2._id} sent to destination")
                    } else {
                        throw PrintException("Could not send to destination")
                    }
                } catch (e: Exception) {
                    log.error("Job $id failed", e)
                    val msg = (e as? PrintException)?.message ?: "Failed to process"
                    failJob(id, msg)
                } finally {
                    tmp.delete()
                }
            }
            return true to "Successfully created request $id"
        } catch (e: Exception) {
            // todo check if this is necessary, given that the submit code is handled separately
            log.error("Job $id failed", e)
            failJob(id, "Failed to process")
            return false to "Failed to process"
        }
    }

    class PrintException(message: String) : RuntimeException(message)

    private val executor: ExecutorService = ThreadPoolExecutor(5, 30, 10, TimeUnit.MINUTES,
            ArrayBlockingQueue<Runnable>(300, true))
    private val runningTasks: MutableMap<String, Future<*>> = ConcurrentHashMap()
    private val lock: Any = Any()

    /**
     * Run an task in the service
     * Upon completion, it will remove itself from [runningTasks]
     */
    private fun submit(id: String, action: () -> Unit) {
        log.info("Submitting task $id")
        val future = executor.submit {
            try {
                action()
            } finally {
                cancel(id)
            }
        }
        runningTasks[id] = future
    }

    private fun cancel(id: String) {
        try {
            val future = runningTasks.remove(id)
            if (future?.isDone == false)
                future.cancel(true)
        } catch (e: Exception) {
            log.error("Failed to cancel job $id", e)
        }
    }

    private const val INDICATOR_COLOR = "/ProcessColorModel /DeviceCMYK"
    private const val INDICATOR_MONOCHROME = "/ProcessColorModel /DeviceGray"

    /**
     * Returns true if monochrome was detected,
     * or false if color was detected
     * Defaults to monochrome
     */
    private fun BufferedReader.isMonochrome(): Boolean {
        for (line in lines()) {
            if (line.contains(INDICATOR_MONOCHROME))
                return true
            if (line.contains(INDICATOR_COLOR))
                return false
        }
        return true
    }

}