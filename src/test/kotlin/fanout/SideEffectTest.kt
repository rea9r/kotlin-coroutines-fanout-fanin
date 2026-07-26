package fanout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Chapter 7: cancellation is not a rollback. */
class SideEffectTest {

    @Test
    fun `completed side effects remain when a sibling fails`() = runTest {
        val committed = mutableListOf<Int>()
        shouldThrow<IllegalStateException> {
            coroutineScope {
                val a = async { delay(10); committed.add(1) } // completes, side effect stays
                val b = async { delay(10); committed.add(2) } // completes, side effect stays
                val c = async { delay(20); error("boom") }    // fails later
                awaitAll(a, b, c)
            }
        }
        committed.sorted() shouldBe listOf(1, 2)
    }
}
