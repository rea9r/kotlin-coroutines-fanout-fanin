package fanout

/** A domain item fetched from a remote API. */
data class Item(val id: Int)

/** The remote API. Fakes are injected in tests. */
fun interface ItemApi {
    suspend fun fetchItem(id: Int): Item
}

/**
 * The outcome of loading one item.
 *
 * Only *expected* failures become values here. Unexpected exceptions and
 * cancellation are not turned into values; they propagate out.
 */
sealed interface FetchOutcome<out T> {
    data class Success<T>(val value: T) : FetchOutcome<T>
    data object TimedOut : FetchOutcome<Nothing>
    data class Failed(val cause: Exception) : FetchOutcome<Nothing>
}

/** An item id paired with its outcome, so the caller keeps the id -> result mapping. */
data class ItemLoadResult(val id: Int, val outcome: FetchOutcome<Item>)
