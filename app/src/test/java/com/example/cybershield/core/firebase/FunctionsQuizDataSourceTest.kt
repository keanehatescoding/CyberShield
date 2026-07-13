package com.example.cybershield.core.firebase

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * These tests are the client-side half of the grading contract — they
 * verify FunctionsQuizDataSource sends {quizId, questionId, selectedIndex,
 * answeredAt, resultId, timeRemaining} (never a claimed isCorrect) and
 * correctly unpacks whatever validateAnswer / validateAnswersBatch hands
 * back. See functions/src/index.ts for the server side of this contract.
 *
 * The Firebase HTTPS-callable transport is swapped for an in-test seam
 * (the internal `httpsCallable` var) because HttpsCallableReference.call
 * is a final member with an inline extension that MockK cannot stub.
 * HttpsCallableResult is a final class whose `data` is a final field, so the
 * response mock stubs the explicit `getData()` method rather than the
 * property (MockK cannot always intercept the field-backed getter).
 */
class FunctionsQuizDataSourceTest {
    private lateinit var dataSource: FunctionsQuizDataSource

    @Before
    fun setUp() {
        // `functions` is only used by the default httpsCallable implementation,
        // which every test below overrides — so a plain mock is sufficient.
        dataSource = FunctionsQuizDataSource(mockk())
    }

    // -- validateAnswer --------------------------------------------------

    @Test
    fun `validateAnswer sends only quizId, questionId, selectedIndex, answeredAt, resultId, and timeRemaining`() =
        runTest {
            var captured: Map<String, Any?>? = null
            val response = mockk<HttpsCallableResult>()
            every { response.getData() } returns
                    mapOf(
                        "questionId" to "q1",
                        "isCorrect" to true,
                        "correctIndex" to 2L,
                        "explanation" to "Because."
                    )
            dataSource.httpsCallable = { name, payload ->
                assertEquals("validateAnswer", name)
                captured = payload
                response
            }

            dataSource.validateAnswer(
                quizId = "quiz1",
                questionId = "q1",
                selectedIndex = 2,
                answeredAt = 555L,
                resultId = "r1",
                timeRemaining = 30
            )

            val sent = captured!!
            assertEquals(
                setOf(
                    "quizId",
                    "questionId",
                    "selectedIndex",
                    "answeredAt",
                    "resultId",
                    "timeRemaining"
                ), sent.keys
            )
            assertEquals("quiz1", sent["quizId"])
            assertEquals("q1", sent["questionId"])
            assertEquals(2, sent["selectedIndex"])
            assertEquals(555L, sent["answeredAt"])
            assertEquals("r1", sent["resultId"])
            assertEquals(30, sent["timeRemaining"])
        }

    @Test
    fun `validateAnswer maps a successful response into AnswerValidation`() =
        runTest {
            val response = mockk<HttpsCallableResult>()
            every { response.getData() } returns
                    mapOf(
                        "questionId" to "q1",
                        "isCorrect" to false,
                        "correctIndex" to 1L,
                        "explanation" to "Not quite."
                    )
            dataSource.httpsCallable = { _, _ -> response }

            val validation = dataSource.validateAnswer(
                "quiz1",
                "q1",
                selectedIndex = 3,
                answeredAt = 0L,
                resultId = "r1",
                timeRemaining = 0
            )

            assertEquals("q1", validation.questionId)
            assertEquals(false, validation.isCorrect)
            assertEquals(1, validation.correctIndex)
            assertEquals("Not quite.", validation.explanation)
        }

    // -- validateAnswersBatch --------------------------------------------

    @Test
    fun `validateAnswersBatch sends every pending answer without any isCorrect field`() =
        runTest {
            var captured: Map<String, Any?>? = null
            val response = mockk<HttpsCallableResult>()
            every { response.getData() } returns
                    mapOf(
                        "results" to
                                listOf(
                                    mapOf(
                                        "questionId" to "q1",
                                        "isCorrect" to true,
                                        "correctIndex" to 0L,
                                        "explanation" to "",
                                        "error" to null
                                    ),
                                ),
                    )
            dataSource.httpsCallable = { name, payload ->
                assertEquals("validateAnswersBatch", name)
                captured = payload
                response
            }

            dataSource.validateAnswersBatch(
                listOf(
                    PendingAnswer(
                        localId = 1L,
                        resultId = "r1",
                        quizId = "quiz1",
                        questionId = "q1",
                        selectedIndex = 0,
                        answeredAt = 10L,
                        timeRemaining = 20
                    )
                ),
            )

            @Suppress("UNCHECKED_CAST")
            val answers = captured!!["answers"] as List<Map<String, Any?>>
            assertEquals(1, answers.size)
            assertEquals(
                setOf(
                    "quizId",
                    "questionId",
                    "selectedIndex",
                    "answeredAt",
                    "resultId",
                    "timeRemaining"
                ), answers.single().keys
            )
            assertEquals("r1", answers.single()["resultId"])
            assertEquals(20, answers.single()["timeRemaining"])
        }

    @Test
    fun `validateAnswersBatch pairs each result back to its localId in request order`() =
        runTest {
            val response = mockk<HttpsCallableResult>()
            every { response.getData() } returns
                    mapOf(
                        "results" to
                                listOf(
                                    mapOf(
                                        "questionId" to "q1",
                                        "isCorrect" to true,
                                        "correctIndex" to 0L,
                                        "explanation" to "ok",
                                        "error" to null
                                    ),
                                    mapOf(
                                        "questionId" to "q2",
                                        "isCorrect" to null,
                                        "correctIndex" to null,
                                        "explanation" to null,
                                        "error" to "Question not found."
                                    ),
                                ),
                    )
            dataSource.httpsCallable = { _, _ -> response }

            val results =
                dataSource.validateAnswersBatch(
                    listOf(
                        PendingAnswer(
                            localId = 100L,
                            resultId = "r1",
                            quizId = "quiz1",
                            questionId = "q1",
                            selectedIndex = 0,
                            answeredAt = 1L,
                            timeRemaining = 5
                        ),
                        PendingAnswer(
                            localId = 200L,
                            resultId = "r2",
                            quizId = "quiz1",
                            questionId = "q2",
                            selectedIndex = 1,
                            answeredAt = 2L,
                            timeRemaining = 9
                        ),
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
    }
}
