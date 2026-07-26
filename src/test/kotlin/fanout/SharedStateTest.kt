package fanout

import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

/**
 * The four guards for shared mutable state. These run on a real multi-threaded
 * dispatcher (not runTest's single thread), so each guard is exercised under
 * actual parallelism: the invariant holds only because the guard works.
 */
class SharedStateTest {

    private val n = 1_000

    @Test
    fun `mutex serializes concurrent updates`() = runBlocking<Unit>(Dispatchers.Default) {
        val mutex = Mutex()
        var total = 0
        coroutineScope {
            repeat(n) { launch { mutex.withLock { total += 1 } } }
        }
        total shouldBe n
    }

    @Test
    fun `atomic updates a single value indivisibly`() = runBlocking<Unit>(Dispatchers.Default) {
        val total = AtomicInteger(0)
        coroutineScope {
            repeat(n) { launch { total.addAndGet(1) } }
        }
        total.get() shouldBe n
    }

    @Test
    fun `confining updates to one execution slot serializes them`() = runBlocking<Unit>(Dispatchers.Default) {
        val confined = Dispatchers.Default.limitedParallelism(1)
        var total = 0
        coroutineScope {
            repeat(n) { launch { withContext(confined) { total += 1 } } }
        }
        total shouldBe n
    }

    @Test
    fun `a single owner over a channel aggregates without shared mutation`() = runBlocking<Unit>(Dispatchers.Default) {
        val channel = Channel<Int>()
        val total = coroutineScope {
            val aggregator = async {
                var sum = 0
                repeat(n) { sum += channel.receive() }
                sum
            }
            repeat(n) { launch { channel.send(1) } }
            aggregator.await()
        }
        total shouldBe n
    }
}
