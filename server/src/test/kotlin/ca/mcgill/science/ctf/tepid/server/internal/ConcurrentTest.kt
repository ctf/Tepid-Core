package ca.mcgill.science.ctf.tepid.server.internal

import io.reactivex.Completable
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.fail

/**
 * Created by Allan Wang on 17/05/17.
 */

interface CompletableCallback {
    fun onComplete()
    fun onError(message: String?) = onError(Throwable(message))
    fun onError(throwable: Throwable)
}

/**
 * Helper class to test asynchronous methods
 * The execution is launched aon a separate thread and blocked until a response is received
 * or a timeout occurs
 */
inline fun concurrentTest(count: Int = 1, timeout: Long = 5, crossinline caller: (callback: CompletableCallback) -> Unit) {
    val counter = AtomicInteger(0)
    val result = Completable.create { emitter ->
        caller(object : CompletableCallback {
            override fun onComplete() {
                if (counter.incrementAndGet() == count)
                    emitter.onComplete()
            }

            override fun onError(throwable: Throwable) {
                emitter.onError(throwable)
            }
        })
    }.blockingGet(timeout, TimeUnit.SECONDS)
    if (result != null)
        throw RuntimeException("Concurrent fail: ${result.message}")
}

class InternalTest {
    @Test
    fun concurrentTest() = try {
        concurrentTest { result ->
            Thread().run {
                Thread.sleep(100)
                result.onError("Intentional fail")
            }
        }
        fail("Did not throw exception")
    } catch (e: Exception) {
        // pass
    }
}