package ca.mcgill.science.ctf.tepid.server

import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.internal.*
import ca.mcgill.science.ctf.tepid.server.models.*
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import ca.mcgill.science.ctf.tepid.server.utils.Printer
import ca.mcgill.science.ctf.tepid.server.utils.copyFrom
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class PrinterTest : WithLogging() {

    init {
        TestConfigs.setup()
    }

    private fun testPrintThread(id: String, pageCount: Int, error: String? = null): Thread {
        PrintJobs.testCreate(id)
        log.info("Created $id")
        return Thread({
            sleep(500, 800)
            PrintJobs.received(id)
            log.info("Received $id")
            sleep(500, 800)
            PrintJobs.processed(id, pageCount, 0)
            log.info("Processed $id")
            sleep(700, 800)
            if (error == null) {
                PrintJobs.printed(id, "destination1", pageCount)
                log.info("Printed $id")
            } else {
                PrintJobs.failed(id, error)
                log.info("Failed $id")
            }
        }, "Test Print $id")
    }

    /**
     * Watch job on a separate thread, locked by the [callback]
     */
    private fun watch(id: String, callback: CompletableCallback) {
        lateinit var stage: PrintStage
        PrintJobs.watch(id).timeout(10, TimeUnit.SECONDS).subscribeOn(Schedulers.io()).subscribeBy(
                onNext = {
                    // given adequate sleep times, we should be capturing all stages properly
                    when (it) {
                        is Received -> assertTrue(stage is Created)
                        is Processed -> assertTrue(stage is Received)
                        is Printed -> assertTrue(stage is Processed)
                    }
                    log.info("Stage $it")
                    stage = it
                },
                onComplete = {
                    callback.onComplete()
                },
                onError = {
                    callback.onError(it)
                }
        )
    }

    @Test
    fun watcher() {
        concurrentTest { result ->
            val thread = testPrintThread("job1", 8)
            watch("job1", result)
            thread.start()
        }
    }

    @Test
    fun noDecompression() {
        val source = testPs
        val dest = File.createTempFile("tepid-test-nd", "ps")
        dest.copyFrom(Printer.decompress(source))
        val equal = source.length() == dest.length() &&
                source.inputStream().bufferedReader().readLines() == dest.inputStream().bufferedReader().readLines()
        dest.delete()
        assertTrue(equal, "Decompressing non xz formatted file did not result in exact duplicate")
    }

    @Test
    fun compression() {
        val source = testPs
        val tmp = File.createTempFile("tepid-test-c", "ps")
        try {
            println(tmp.length())
            source.inputStream().use { input ->
                Printer.compress(tmp.outputStream()).use { output ->
                    input.copyTo(output)
                }
            }
            assertTrue(tmp.length() > 0 && tmp.length() < source.length(), "Compressed file was not smaller")
        } catch (e: Exception) {
            fail(e.message)
        } finally {
            tmp.delete()
        }
    }
}