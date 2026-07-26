package fanout

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Minimal exponential-backoff retry. Illustrative, not production-grade: it retries on
 * any [Exception] except [CancellationException].
 *
 * Note: because `TimeoutCancellationException` is a subclass of [CancellationException],
 * a per-attempt `withTimeout` is *not* retried here. Model an attempt timeout as a value
 * (see [FetchOutcome.TimedOut]) if you want to retry it.
 */
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    initialDelay: Long = 100,
    factor: Double = 2.0,
    block: suspend () -> T,
): T {
    require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
    require(factor >= 1.0) { "factor must be at least 1.0" }
    require(initialDelay >= 0) { "initialDelay must not be negative" }
    var delayMs = initialDelay
    repeat(maxAttempts - 1) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e // cancellation is not retried
        } catch (e: Exception) {
            delay(delayMs) // delay is cancellable
            delayMs = (delayMs * factor).toLong()
        }
    }
    return block() // the last attempt lets its failure propagate
}
