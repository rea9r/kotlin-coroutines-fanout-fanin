package fanout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Choosing a failure policy from the requirement, not the API. */
class FailurePolicyTest {

    @Test
    fun `one failure cancels the siblings and exits at failure time`() = runTest {
        var siblingCompleted = false
        val ex = shouldThrow<IllegalStateException> {
            coroutineScope {
                val a = async { delay(50); error("boom") }
                val b = async { delay(1000); siblingCompleted = true; "b" }
                awaitAll(a, b)
            }
        }
        ex.message shouldBe "boom"
        siblingCompleted shouldBe false
        testScheduler.currentTime shouldBe 50 // fails at 50ms, not after the slow 1000ms sibling
    }

    @Test
    fun `runSuspendCatching value-ifies failures but rethrows cancellation`() = runTest {
        val failed = runSuspendCatching { throw IOException("io") }
        failed.isFailure shouldBe true

        shouldThrow<CancellationException> {
            runSuspendCatching { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `supervisorScope keeps siblings alive when one fails`() = runTest {
        val api = ItemApi { id -> delay(50); if (id == 2) throw IOException("io") else Item(id) }
        val results = loadAllSupervised(api, listOf(1, 2, 3))
        results.first { it.id == 2 }.outcome.shouldBeInstanceOf<FetchOutcome.Failed>()
        results.first { it.id == 1 }.outcome.shouldBeInstanceOf<FetchOutcome.Success<Item>>()
        results.first { it.id == 3 }.outcome.shouldBeInstanceOf<FetchOutcome.Success<Item>>()
    }

    @Test
    fun `one value-ified failure leaves the other successes intact`() = runTest {
        val results = coroutineScope {
            (1..3).map { id ->
                async {
                    runSuspendCatching {
                        delay(100)
                        if (id == 2) throw IOException("io") else Item(id)
                    }
                }
            }.awaitAll()
        }
        results.count { it.isSuccess } shouldBe 2
        results.count { it.isFailure } shouldBe 1
    }

    @Test
    fun `swallowing cancellation keeps a cancelled coroutine running, rethrowing stops it`() = runTest {
        // kotlin.runCatching swallows the CancellationException, so execution continues
        var continuedAfterSwallow = false
        val swallowing = launch {
            runCatching { delay(1_000) }
            continuedAfterSwallow = true
        }
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        swallowing.cancel()
        swallowing.join()
        continuedAfterSwallow shouldBe true

        // runSuspendCatching rethrows it, so the code after never runs
        var continuedAfterRethrow = false
        val rethrowing = launch {
            runSuspendCatching { delay(1_000) }
            continuedAfterRethrow = true
        }
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        rethrowing.cancel()
        rethrowing.join()
        continuedAfterRethrow shouldBe false
    }

    @Test
    fun `coroutineScope with per-await catching cannot save the siblings`() = runTest {
        var slowCompleted = false
        shouldThrow<IOException> {
            coroutineScope {
                val failing = async { delay(50); throw IOException("io") }
                val slow = async { delay(1_000); slowCompleted = true; Item(1) }
                listOf(failing, slow).map { d ->
                    try {
                        FetchOutcome.Success(d.await())
                    } catch (e: IOException) {
                        FetchOutcome.Failed(e) // catching at await() is too late: the scope is already failing
                    }
                }
            }
        }
        slowCompleted shouldBe false // the failure cancelled the sibling before its await was reached
    }

    @Test
    fun `caller cancellation still reaches supervisorScope children`() = runTest {
        var childSawCancellation = false
        val api = ItemApi { id ->
            try {
                delay(10_000)
                Item(id)
            } catch (e: CancellationException) {
                childSawCancellation = true
                throw e
            }
        }
        val job = launch { loadAllSupervised(api, listOf(1)) }
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        job.cancel()
        job.join()
        childSawCancellation shouldBe true // supervisorScope isolates sibling failure, not caller cancellation
    }

    @Test
    fun `await distinguishes its two cancellation causes with ensureActive`() = runTest {
        // (b) the awaited Deferred was cancelled: ensureActive() does NOT throw
        var reachedDeferredCancelledBranch = false
        coroutineScope {
            val d = async { delay(1000) }
            d.cancel()
            try {
                d.await()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                reachedDeferredCancelledBranch = true
            }
        }
        reachedDeferredCancelledBranch shouldBe true

        // (a) the awaiting coroutine itself was cancelled: ensureActive() throws
        var ensureActiveThrew = false
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        coroutineScope {
            val child = launch {
                try {
                    started.complete(Unit)
                    never.await()
                } catch (e: CancellationException) {
                    try {
                        currentCoroutineContext().ensureActive()
                    } catch (inner: CancellationException) {
                        ensureActiveThrew = true
                    }
                    throw e
                }
            }
            started.await()
            child.cancel()
            child.join()
        }
        ensureActiveThrew shouldBe true
    }
}
