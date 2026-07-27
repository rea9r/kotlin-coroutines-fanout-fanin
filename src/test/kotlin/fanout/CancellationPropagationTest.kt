package fanout

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Down-propagation in the Job tree: cancelling one parent cancels every child
 * under it. Each child stops at its own suspension point and runs its finally,
 * so a single cancel on the parent tears down the whole subtree.
 */
class CancellationPropagationTest {

    @Test
    fun `cancelling a parent cancels every child`() = runTest {
        val stopped = mutableListOf<Int>()
        val parent = launch {
            repeat(3) { i ->
                launch {
                    try {
                        while (true) { delay(10) } // delay is a cancellable suspension point
                    } finally {
                        stopped += i // reached only because the child was cancelled here
                    }
                }
            }
        }
        testScheduler.advanceTimeBy(25) // let the three children start and suspend at delay
        testScheduler.runCurrent()
        parent.cancelAndJoin()
        stopped.sorted() shouldBe listOf(0, 1, 2) // all three stopped, not just the parent
    }
}
