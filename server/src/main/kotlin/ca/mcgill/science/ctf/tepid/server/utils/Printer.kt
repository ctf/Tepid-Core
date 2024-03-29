package ca.mcgill.science.ctf.tepid.server.utils

import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.Configs
import ca.mcgill.science.ctf.tepid.server.models.*
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZFormatException
import org.tukaani.xz.XZInputStream
import org.tukaani.xz.XZOutputStream
import java.io.*
import java.util.concurrent.*

interface PrinterContract {
    /**
     * Submits a request to print the provided job
     * [jobName] job name for display
     * [shortUser] unique identifier for user who is printing
     * [queueName] unique identifier for the queue to use
     * [stream] file contents; should be compressed using [XZOutputStream] // todo make more obvious
     * [validate] optional predicate to ensure print candidate is valid; defaults to accept all candidates
     *
     * This method guarantees a row creation in [PrintJobs],
     * and will pass the job through a series of stages, resulting in either
     * [Printed] or [Failed]
     *
     * Returns a [PrintJob] is the stream is received properly,
     * and an [PrintError] otherwise.
     *
     * Note that receiving a [PrintJob] does not necessarily mean that the job will print,
     * as the actual processing is handled asynchronously on another thread.
     * If everything goes through, a print request will be sent to [Configs.print]
     */
    fun print(jobName: String,
              shortUser: String,
              queueName: String,
              stream: InputStream,
              validate: Validator<PrintRequest> = Printer.hasSufficientQuota): PrintResponse

    /**
     * Wraps a supplied stream with the compression stream
     */
    fun compress(stream: OutputStream): OutputStream

    fun compress(file: File): OutputStream = compress(file.outputStream())

    /**
     * Wraps the supplied file with the compression stream,
     * or returns the original file stream as a fallback
     */
    fun decompress(file: File): InputStream
}

//typealias Validator = (candidate: PrintRequest) -> Validation

object Printer : PrinterContract, WithLogging() {

    /*
     * Error constants
     */

    const val POSTSCRIPT_ERROR = "postscript_error"
    const val INVALID_DESTINATION = "invalid_destination"
    const val INSUFFICIENT_QUOTA = "insufficient_quota"
    const val PRINT_FAILURE = "print_failure"
    const val PROCESS_FAILURE = "process_failure"

    /*
     * Validators
     */

    /**
     * Check if user has sufficient quota
     */
    val hasSufficientQuota: Validator<PrintRequest> = {
        if (it.hasSufficientQuota()) Valid
        else Invalid(Printer.INSUFFICIENT_QUOTA)
    }

    private inline val tmpDir: File
        get() = Configs.tmpDir

    override fun compress(stream: OutputStream): OutputStream = XZOutputStream(stream, LZMA2Options())

    override fun decompress(file: File): InputStream = try {
        file.inputStream().use(::XZInputStream)
    } catch (e: XZFormatException) {
        file.inputStream()
    }

    override fun print(jobName: String,
                       shortUser: String,
                       queueName: String,
                       stream: InputStream,
                       validate: Validator<PrintRequest>): PrintResponse {

        if (!tmpDir.exists() && !tmpDir.mkdirs()) {
            log.error("Failed to create tmp path ${tmpDir.absolutePath}")
            return PrintError("Failed to create tmp path")
        }

        /*
         * Initialize
         */

        val id = Configs.generateId()
        log.debug("Printing job data $id")
        val tmpXz = File("${tmpDir.absolutePath}/$id.ps.xz")
        val job = PrintJob(id, shortUser, jobName)
        PrintJobs.create(job, tmpXz)

        /*
         * Helper methods
         */

        fun received() = PrintJobs.received(id)

        fun processed(pageCount: Int, colourPageCount: Int) =
                PrintJobs.processed(id, pageCount, colourPageCount)

        fun printed(destination: String, cost: Int) =
                PrintJobs.printed(id, destination, cost)

        fun failed(message: String) {
            PrintJobs.failed(id, message)
            cancel(id)
        }

        try {
            // todo test and validate
            //write compressed job to disk
            tmpXz.copyFrom(stream)
            //let db know we have received data
            received()

            submit(id) {

                /*
                 * Note that this is a runnable that will be submitted to the executor service
                 * This block does not run in the same thread!
                 */

                // Generates a random file name with our prefix and suffix
                val tmp = File.createTempFile("tepid", ".ps")
                try {
                    //decompress data
                    tmp.copyFrom(decompress(tmpXz))

                    // Detect PostScript monochrome instruction
                    val br = BufferedReader(FileReader(tmp.absolutePath))
                    val now = System.currentTimeMillis()
                    val psMonochrome = br.isMonochrome()
                    log.trace("Detected ${if (psMonochrome) "monochrome"
                    else "colour"} for job $id in ${System.currentTimeMillis() - now} ms")
                    //count pages
                    val psInfo = Configs.psInfo(tmp) ?: throw PrintException(POSTSCRIPT_ERROR)
                    val pageCount = psInfo.pages
                    val colourPageCount = if (psMonochrome) 0 else psInfo.colourPages
                    log.trace("Job $id has ${psInfo.pages} pages, $colourPageCount in color")

                    //update page count and stage in db
                    processed(pageCount, colourPageCount)

                    val destination = QueueManager.getDestination(queueName, job, pageCount)
                            ?: throw PrintException(INVALID_DESTINATION)
                    val request = PrintRequest(job, tmp, destination, psInfo.pages, colourPageCount)

                    val validation = validate(request)
                    if (validation is Invalid)
                        throw PrintException(validation.message)

                    if (!Configs.print(request)) throw PrintException(PRINT_FAILURE)

                    printed(destination, request.quotaCost)
                } catch (e: PrintException) {
                    log.error("Job $id failed: ${e.message}")
                    failed(e.message)
                } catch (e: Exception) {
                    log.error("Job $id failed", e)
                    failed(e::class.java.simpleName)
                } finally {
                    tmp.delete()
                }
            }
            return job
        } catch (e: Exception) {
            log.error("Job $id failed", e)
            failed("Failed to process")
            return PrintError(PROCESS_FAILURE)
        }
    }

    class PrintException(message: String) : TepidException(message)

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