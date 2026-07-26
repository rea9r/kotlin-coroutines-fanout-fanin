package fanout

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but cooperative with cancellation.
 *
 * [runCatching] catches every [Throwable], including [CancellationException] and [Error];
 * swallowing cancellation breaks structured concurrency. This version rethrows
 * cancellation and turns only [Exception] into a failed [Result].
 *
 * This is an intermediate form: it still value-ifies *every* [Exception]. In real code,
 * catch only the expected exceptions (see [ItemLoader]).
 */
suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
