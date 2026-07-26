package fanout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Chapters 3 and 4: cancellation is cooperative. A busy CPU loop is only stopped at a
 * cancellable suspension point or an explicit check. This uses a real dispatcher and
 * real wall-clock time, because a busy loop never lets virtual time advance.
 */
class CooperativeCancellationTest {

    @Test
    fun `withTimeout cannot stop a non-cooperative loop, but ensureActive makes it stop`() = runBlocking {
        // No cancellation check: the 100ms timeout cannot interrupt the ~500ms busy loop.
        val t0 = System.nanoTime()
        withTimeoutOrNull(100) {
            withContext(Dispatchers.Default) {
                val start = System.nanoTime()
                var x = 0L
                while (System.nanoTime() - start < 500_000_000L) { x++ }
                x
            }
        }
        val noCheckMs = (System.nanoTime() - t0) / 1_000_000

        // With ensureActive() the loop stops near the 100ms deadline.
        val t1 = System.nanoTime()
        val withCheck = withTimeoutOrNull(100) {
            withContext(Dispatchers.Default) {
                var x = 0L
                while (true) {
                    x++
                    if (x % 1_000_000L == 0L) ensureActive()
                }
                @Suppress("UNREACHABLE_CODE")
                x
            }
        }
        val withCheckMs = (System.nanoTime() - t1) / 1_000_000

        assertTrue(noCheckMs >= 400) { "no-check ran $noCheckMs ms, expected >= 400" }
        assertTrue(withCheckMs < 350) { "with-check ran $withCheckMs ms, expected < 350" }
        assertTrue(noCheckMs > withCheckMs) { "no-check ($noCheckMs) should exceed with-check ($withCheckMs)" }
        assertTrue(withCheck == null) { "with-check should be cancelled, so null" }
    }
}
