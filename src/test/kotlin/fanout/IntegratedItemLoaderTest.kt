package fanout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test

/** The integrated [ItemLoader], one behavior per test. */
class IntegratedItemLoaderTest {

    @Test
    fun `all succeed and results keep input order`() = runTest {
        val results = ItemLoader({ id -> delay(100); Item(id) }).loadAll(listOf(1, 2, 3))
        results.map { it.id } shouldBe listOf(1, 2, 3)
        results.forEach { it.outcome.shouldBeInstanceOf<FetchOutcome.Success<Item>>() }
    }

    @Test
    fun `an attempt timeout is retried while siblings still succeed`() = runTest {
        val calls = mutableMapOf<Int, Int>()
        val results = ItemLoader({ id ->
            calls.merge(id, 1, Int::plus)
            if (id == 2) delay(600) else delay(100) // id=2 always exceeds the 500ms attempt timeout
            Item(id)
        }).loadAll(listOf(1, 2, 3))
        results[1].outcome shouldBe FetchOutcome.TimedOut
        calls[2] shouldBe 3 // three attempts, each timed out
        results[0].outcome.shouldBeInstanceOf<FetchOutcome.Success<Item>>()
    }

    @Test
    fun `a retryable failure is retried then succeeds`() = runTest {
        var calls = 0
        val results = ItemLoader({ id ->
            calls++
            delay(50)
            if (calls < 3) throw RetryableItemApiException(IOException("x")) else Item(id)
        }).loadAll(listOf(7))
        results[0].outcome shouldBe FetchOutcome.Success(Item(7))
        calls shouldBe 3
    }

    @Test
    fun `a non-retryable IOException becomes Failed without retry`() = runTest {
        var calls = 0
        val results = ItemLoader({ _ -> calls++; delay(50); throw IOException("permanent") }).loadAll(listOf(7))
        results[0].outcome.shouldBeInstanceOf<FetchOutcome.Failed>()
        calls shouldBe 1 // shouldRetry is false, so no retry
    }

    @Test
    fun `an unexpected bug fails fast and cancels siblings`() = runTest {
        var siblingCompleted = false
        val loader = ItemLoader({ id ->
            if (id == 2) {
                delay(50)
                error("bug")
            } else {
                delay(1000)
                siblingCompleted = true
                Item(id)
            }
        })
        shouldThrow<IllegalStateException> { loader.loadAll(listOf(1, 2)) }
        siblingCompleted shouldBe false
    }

    @Test
    fun `missing the overall deadline throws ItemLoadDeadlineException`() = runTest {
        // 24 items x 400ms with 4 permits -> 6 waves x 400ms = 2400ms > 2000ms deadline
        val loader = ItemLoader({ id -> delay(400); Item(id) })
        shouldThrow<ItemLoadDeadlineException> { loader.loadAll((1..24).toList()) }
    }

    @Test
    fun `a downstream nested timeout is not mislabeled as the deadline`() = runTest {
        val api = ItemApi { id -> withTimeout(10) { delay(100); Item(id) } }
        // The downstream timeout propagates as a TimeoutCancellationException; if it were
        // mislabeled it would come out as ItemLoadDeadlineException (a plain RuntimeException).
        shouldThrow<TimeoutCancellationException> { ItemLoader(api).loadAll(listOf(1)) }
    }

    @Test
    fun `caller cancellation propagates as cancellation, not the deadline`() = runTest {
        var caught: Throwable? = null
        val loader = ItemLoader({ id -> delay(10_000); Item(id) })
        val job = launch {
            try {
                loader.loadAll(listOf(1))
            } catch (e: Throwable) {
                caught = e
                throw e
            }
        }
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        job.cancel()
        job.join()
        (caught is CancellationException) shouldBe true
        (caught is ItemLoadDeadlineException) shouldBe false
    }
}
