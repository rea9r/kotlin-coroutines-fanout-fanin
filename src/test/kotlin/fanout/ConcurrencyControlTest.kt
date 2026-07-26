package fanout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test

/** Bounding concurrency, time, and retries. */
class ConcurrencyControlTest {

    @Test
    fun `semaphore caps concurrency to its permits`() = runTest {
        val gate = Semaphore(permits = 2)
        var inFlight = 0
        var maxInFlight = 0
        coroutineScope {
            (1..4).map {
                async {
                    gate.withPermit {
                        inFlight++
                        maxInFlight = maxOf(maxInFlight, inFlight)
                        delay(100)
                        inFlight--
                    }
                }
            }.awaitAll()
        }
        maxInFlight shouldBe 2
        testScheduler.currentTime shouldBe 200 // 4 items, 2 at a time -> two waves
    }

    @Test
    fun `withTimeoutOrNull only nulls the timeout, other exceptions propagate`() = runTest {
        val slow = withTimeoutOrNull(100) { delay(1000); "value" }
        slow shouldBe null

        shouldThrow<IllegalStateException> {
            withTimeoutOrNull(1000) { delay(10); error("boom") }
        }
    }

    @Test
    fun `a naive retry does not retry a per-attempt timeout`() = runTest {
        var attempts = 0
        shouldThrow<TimeoutCancellationException> {
            withRetry(maxAttempts = 3) {
                attempts++
                withTimeout(50) { delay(100) }
            }
        }
        // TimeoutCancellationException is a CancellationException, so it is rethrown, not retried
        attempts shouldBe 1
    }

    @Test
    fun `withRetry rejects invalid arguments`() = runTest {
        shouldThrow<IllegalArgumentException> { withRetry<String>(maxAttempts = 0) { "x" } }
    }
}
