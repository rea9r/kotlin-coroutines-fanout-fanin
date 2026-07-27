# kotlin-coroutines-fanout-fanin

Runnable verification code for a Zenn article on designing Fan-out / Fan-in with
Kotlin Coroutines. The behavioral claims listed in the article's verification
section are pinned by tests here.

```bash
./gradlew test
```

## Environment

- Kotlin 2.4.0
- kotlinx-coroutines 1.11.0 (`core` / `test`)
- Kotest 6.2.2 (assertions)
- JUnit 5 (runner)
- JDK 17

## Layout

`src/main/kotlin/fanout` holds the example code from the article; `src/test/kotlin/fanout`
verifies it. Tests run in `runTest` virtual time, except `CooperativeCancellationTest`
(real wall-clock time) and `SharedStateTest` (real parallelism), which need a real
dispatcher.

| Test | What it pins |
| --- | --- |
| `PlainVsAsyncTest` | using coroutines vs parallelizing are separate decisions |
| `BasicFanOutTest` | sequential = sum, concurrent = slowest; `awaitAll` keeps order |
| `FailurePolicyTest` | fail-fast, `runSuspendCatching`, `supervisorScope`, `await`'s two cancellation causes |
| `CancellationPropagationTest` | cancelling a parent cancels every child (down-propagation in the Job tree) |
| `ConcurrencyControlTest` | `Semaphore` cap, `withTimeoutOrNull`, retry with backoff (success and cancellation), a naive retry vs a per-attempt timeout |
| `CooperativeCancellationTest` | a busy CPU loop is only stopped at a cancellable check |
| `SideEffectTest` | completed side effects remain after a sibling fails (cancellation is not a rollback) |
| `SharedStateTest` | the four guards for shared mutable state (`Mutex`, atomic, confinement, `Channel` single owner) under real parallelism |
| `IntegratedItemLoaderTest` | the full `ItemLoader`: overall deadline, per-attempt permit and timeout, limited retry, concurrency cap |

Companion repository for a Zenn article; not a general-purpose library.
