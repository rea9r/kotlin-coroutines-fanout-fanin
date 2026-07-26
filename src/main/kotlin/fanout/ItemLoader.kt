package fanout

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Thrown when the whole [ItemLoader.loadAll] misses its overall deadline. */
class ItemLoadDeadlineException : RuntimeException("loadAll missed its overall deadline")

/**
 * A failure classified as transient. Only this type is retried; the boundary is
 * responsible for wrapping retryable failures in it.
 */
class RetryableItemApiException(cause: Throwable) :
    IOException("transient Item API failure", cause)

/**
 * The integrated example (chapter 10): an overall deadline, a per-attempt permit and
 * timeout, a limited retry, value-ified expected failures, and cancellation propagation.
 */
class ItemLoader(
    private val api: ItemApi,
    private val gate: Semaphore = Semaphore(permits = 4),
) {
    suspend fun loadAll(ids: List<Int>): List<ItemLoadResult> =
        withTimeoutOrNull(2_000) {                             // overall deadline
            coroutineScope {
                ids.map { id ->
                    async { ItemLoadResult(id, loadOne(id)) }  // only expected failures become values
                }.awaitAll()
            }
        } ?: throw ItemLoadDeadlineException()                // convert only *this* timeout

    private suspend fun loadOne(id: Int): FetchOutcome<Item> {
        var delayMs = 100L
        repeat(MAX_ATTEMPTS - 1) {
            when (val outcome = attemptOnce(id)) {
                is FetchOutcome.Success -> return outcome
                FetchOutcome.TimedOut -> Unit                  // an attempt timeout is retried
                is FetchOutcome.Failed ->
                    if (!shouldRetry(outcome.cause)) return outcome
            }
            delay(delayMs)                                     // cancellable wait
            delayMs *= 2
        }
        return attemptOnce(id)                                 // the last attempt's result is returned as-is
    }

    private suspend fun attemptOnce(id: Int): FetchOutcome<Item> =
        gate.withPermit {                                      // hold a permit only for the attempt
            try {
                when (val item = withTimeoutOrNull(500) { api.fetchItem(id) }) {
                    null -> FetchOutcome.TimedOut              // an attempt timeout becomes a value
                    else -> FetchOutcome.Success(item)
                }
            } catch (e: CancellationException) {
                throw e                                        // caller cancellation propagates
            } catch (e: IOException) {
                FetchOutcome.Failed(e)                         // only expected failures become values
            }
        }

    private fun shouldRetry(cause: Exception): Boolean =
        cause is RetryableItemApiException // only failures classified as transient are retried

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
