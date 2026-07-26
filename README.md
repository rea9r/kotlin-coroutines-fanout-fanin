# kotlin-coroutines-fanout-fanin

Runnable verification code for a Zenn article on designing fan-out / fan-in with
Kotlin Coroutines. Every behavioral claim in the article is pinned by a test here.

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
verifies it. Tests run in `runTest` virtual time, except `CooperativeCancellationTest`,
which needs a real dispatcher and real wall-clock time.

| Test | Article section | What it pins |
| --- | --- | --- |
| `PlainVsAsyncTest` | 1 | using coroutines vs parallelizing are separate decisions |
| `BasicFanOutTest` | 2, 9 | sequential = sum, concurrent = slowest; `awaitAll` keeps order |
| `FailurePolicyTest` | 5 | fail-fast, `runSuspendCatching`, `supervisorScope`, `await`'s two cancellation causes |
| `ConcurrencyControlTest` | 6 | `Semaphore` cap, `withTimeoutOrNull`, a naive retry vs a per-attempt timeout |
| `CooperativeCancellationTest` | 3, 4 | a busy CPU loop is only stopped at a cancellable check |
| `SideEffectTest` | 7 | completed side effects remain after a sibling fails (cancellation is not a rollback) |
| `IntegratedItemLoaderTest` | 10 | the full `ItemLoader`: overall deadline, per-attempt permit and timeout, limited retry |

Companion repository for a Zenn article; not a general-purpose library.
