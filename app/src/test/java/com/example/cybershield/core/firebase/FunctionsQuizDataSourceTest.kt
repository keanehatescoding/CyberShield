package com.example.cybershield.core.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * These tests are the client-side half of the grading contract — they
 * verify FunctionsQuizDataSource sends only {quizId, questionId,
 * selectedIndex, answeredAt} (never a claimed isCorrect) and correctly
 * unpacks whatever validateAnswer / validateAnswersBatch hands back. See
 * functions/src/index.ts for the server side of this contract.
 */
class FunctionsQuizDataSourceTest {
    private lateinit var functions: FirebaseFunctions
    private lateinit var callable: HttpsCallableReference
    private lateinit var dataSource: FunctionsQuizDataSource

    @Before
    fun setUp() {
        functions = mockk()
        callable = mockk()
        dataSource = FunctionsQuizDataSource(functions)
    }

    private fun <T> completedTask(value: T): Task<T> {
        val task = mockk<Task<T>>()
        every { task.isComplete } returns true
        every { task.isCanceled } returns false
        every { task.exception } returns null
        every { task.result } returns value
        return task
    }

    // ── validateAnswer ──────────────────────────────────────────────────

    @Test
    fun `validateAnswer sends only quizId, questionId, selectedIndex, and answeredAt`() =
        runTest {
            every { functions.getHttpsCallable("validateAnswer") } returns callable
            val payloadSlot = slot<Map<String, Any?>>()
            val response = mockk<HttpsCallableResult>()
            every { response.data } returns
                mapOf("questionId" to "q1", "isCorrect" to true, "correctIndex" to 2L, "explanation" to "Because.")
            every { callable.call(capture(payloadSlot)) } returns completedTask(response)

            dataSource.validateAnswer(quizId = "quiz1", questionId = "q1", selectedIndex = 2, answeredAt = 555L)

            assertEquals(setOf("quizId", "questionId", "selectedIndex", "answeredAt"), payloadSlot.captured.keys)
            assertEquals("quiz1", payloadSlot.captured["quizId"])
            assertEquals("q1", payloadSlot.captured["questionId"])
            assertEquals(2, payloadSlot.captured["selectedIndex"])
            assertEquals(555L, payloadSlot.captured["answeredAt"])
        }

    @Test
    fun `validateAnswer maps a successful response into AnswerValidation`() =
        runTest {
            every { functions.getHttpsCallable("validateAnswer") } returns callable
            val response = mockk<HttpsCallableResult>()
            every { response.data } returns
                mapOf("questionId" to "q1", "isCorrect" to false, "correctIndex" to 1L, "explanation" to "Not quite.")
            every { callable.call(any()) } returns completedTask(response)

            val validation = dataSource.validateAnswer("quiz1", "q1", selectedIndex = 3, answeredAt = 0L)

            assertEquals("q1", validation.questionId)
            assertEquals(false, validation.isCorrect)
            assertEquals(1, validation.correctIndex)
            assertEquals("Not quite.", validation.explanation)
        }

    // ── validateAnswersBatch ────────────────────────────────────────────

    @Test
    fun `validateAnswersBatch sends every pending answer without any isCorrect field`() =
        runTest {
            every { functions.getHttpsCallable("validateAnswersBatch") } returns callable
            val payloadSlot = slot<Map<String, Any?>>()
            val response = mockk<HttpsCallableResult>()
            every { response.data } returns
                mapOf(
                    "results" to
                        listOf(
                            mapOf("questionId" to "q1", "isCorrect" to true, "correctIndex" to 0L, "explanation" to "", "error" to null),
                        ),
                )
            every { callable.call(capture(payloadSlot)) } returns completedTask(response)

            dataSource.validateAnswersBatch(
                listOf(PendingAnswer(localId = 1L, quizId = "quiz1", questionId = "q1", selectedIndex = 0, answeredAt = 10L)),
            )

            @Suppress("UNCHECKED_CAST")
            val answers = payloadSlot.captured["answers"] as List<Map<String, Any?>>
            assertEquals(1, answers.size)
            assertEquals(setOf("quizId", "questionId", "selectedIndex", "answeredAt"), answers.single().keys)
        }

    @Test
    fun `validateAnswersBatch pairs each result back to its localId in request order`() =
        runTest {
            every { functions.getHttpsCallable("validateAnswersBatch") } returns callable
            val response = mockk<HttpsCallableResult>()
            every { response.data } returns
                mapOf(
                    "results" to
                        listOf(
                            mapOf("questionId" to "q1", "isCorrect" to true, "correctIndex" to 0L, "explanation" to "ok", "error" to null),
                            mapOf("questionId" to "q2", "isCorrect" to null, "correctIndex" to null, "explanation" to null, "error" to "Question not found."),
                        ),
                )
            every { callable.call(any()) } returns completedTask(response)

            val results =
                dataSource.validateAnswersBatch(
                    listOf(
                        PendingAnswer(localId = 100L, quizId = "quiz1", questionId = "q1", selectedIndex = 0, answeredAt = 1L),
                        PendingAnswer(localId = 200L, quizId = "quiz1", questionId = "q2", selectedIndex = 1, answeredAt = 2L),
                    ),
                )

            assertEquals(100L, results[0].localId)
            assertEquals(true, results[0].validation?.isCorrect)
            assertNull(results[0].error)

            assertEquals(200L, results[1].localId)
            assertNull(results[1].validation)
            assertEquals("Question not found.", results[1].error)
        }

    @Test
    fun `validateAnswersBatch respects the MAX_BATCH_SIZE contract with the server`() {
        assertEquals(100, FunctionsQuizDataSource.MAX_BATCH_SIZE)
        assertTrue(FunctionsQuizDataSource.MAX_BATCH_SIZE > 0)
    }
}
