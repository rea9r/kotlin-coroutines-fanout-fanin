package fanout

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Chapters 2 and 9: the minimal fan-out / fan-in, checked in virtual time. */
class BasicFanOutTest {

    private suspend fun fetch(id: Int, ms: Long): Int {
        delay(ms)
        return id
    }

    @Test
    fun `sequential is the sum, concurrent is the slowest one`() = runTest {
        val startSeq = testScheduler.currentTime
        val seq = listOf(fetch(1, 100), fetch(2, 100), fetch(3, 100))
        val seqElapsed = testScheduler.currentTime - startSeq

        val startConcurrent = testScheduler.currentTime
        val concurrent = coroutineScope {
            (1..3).map { id -> async { fetch(id, 100) } }.awaitAll()
        }
        val concurrentElapsed = testScheduler.currentTime - startConcurrent

        seqElapsed shouldBe 300
        concurrentElapsed shouldBe 100
        seq shouldBe listOf(1, 2, 3)
        concurrent shouldBe listOf(1, 2, 3) // awaitAll preserves input order
    }

    @Test
    fun `calling await inside the same map falls back to sequential`() = runTest {
        val start = testScheduler.currentTime
        // await() inside the map: start one, wait, start the next...
        (1..3).map { id -> async { fetch(id, 100) }.await() }
        testScheduler.currentTime - start shouldBe 300
    }
}
