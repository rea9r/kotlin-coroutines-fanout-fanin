package fanout

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope

/**
 * Failure isolation with [supervisorScope] (chapter 5): one child's failure does not
 * cancel its siblings. The parent still has to collect each [Deferred] itself.
 */
suspend fun loadAllSupervised(api: ItemApi, ids: List<Int>): List<ItemLoadResult> =
    supervisorScope {
        val deferreds = ids.map { id -> id to async { api.fetchItem(id) } }
        deferreds.map { (id, d) -> awaitOutcome(id, d) }
    }

private suspend fun awaitOutcome(id: Int, deferred: Deferred<Item>): ItemLoadResult =
    try {
        ItemLoadResult(id, FetchOutcome.Success(deferred.await()))
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive() // throws if *we* were cancelled
        throw e                                   // otherwise the Deferred was cancelled; this example propagates it
    } catch (e: IOException) {
        ItemLoadResult(id, FetchOutcome.Failed(e))
    }
