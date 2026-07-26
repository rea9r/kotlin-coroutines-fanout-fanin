package fanout

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Using coroutines and parallelizing are separate decisions. A suspend
 * function can compose a suspend call, a plain fun, and a dependent suspend call in
 * sequence - no async needed.
 */
class PlainVsAsyncTest {

    private data class Request(val userId: Int)
    private data class User(val id: Int)
    private data class Policy(val name: String)
    private data class Outcome(val value: String)
    private data class Response(val body: String)

    private suspend fun fetchUser(userId: Int): User {
        delay(1)
        return User(userId)
    }

    private fun calculatePolicy(user: User): Policy = Policy("policy-${user.id}")

    private suspend fun execute(user: User, policy: Policy): Outcome {
        delay(1)
        return Outcome("${user.id}/${policy.name}")
    }

    private fun toResponse(outcome: Outcome): Response = Response(outcome.value)

    private suspend fun createResponse(request: Request): Response {
        val user = fetchUser(request.userId) // suspend: has a network wait
        val policy = calculatePolicy(user)   // plain fun: pure computation
        val outcome = execute(user, policy)  // suspend: depends on the previous result, so sequential
        return toResponse(outcome)           // plain fun
    }

    @Test
    fun `createResponse composes sequentially`() = runTest {
        createResponse(Request(7)) shouldBe Response("7/policy-7")
    }
}
