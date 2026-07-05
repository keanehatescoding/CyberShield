package com.example.cybershield.core.data.repository

import com.example.cybershield.core.database.dao.QuizAttemptDao
import com.example.cybershield.core.database.dao.QuizDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.database.entity.QuizAttemptEntity
import com.example.cybershield.core.database.entity.QuizResultEntity
import com.example.cybershield.core.domain.model.AnswerValidation
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.firebase.BatchAnswerResult
import com.example.cybershield.core.firebase.FirestoreQuizDataSource
import com.example.cybershield.core.firebase.FunctionsQuizDataSource
import com.example.cybershield.core.firebase.PendingAnswer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers QuizRepositoryImpl's role in the server-side-grading architecture:
 * validateAnswerOnline / cachePendingAnswer never compute isCorrect
 * themselves, and syncPendingResults grades the offline backlog through
 * validateAnswersBatch in chunks, recording each row's verdict individually
 * so one bad row (or one failed chunk) doesn't affect the rest. Also covers
 * saveQuizAttempt/getQuizAttempt, which the result screen relies on instead
 * of trusting a QuizResult passed as a raw navigation argument.
 */
class QuizRepositoryImplTest {
    private lateinit var remoteSource: FirestoreQuizDataSource
    private lateinit var functionsSource: FunctionsQuizDataSource
    private lateinit var quizDao: QuizDao
    private lateinit var quizAttemptDao: QuizAttemptDao
    private lateinit var resultDao: QuizResultDao
    private lateinit var repository: QuizRepositoryImpl

    @Before
    fun setUp() {
        remoteSource = mockk()
        functionsSource = mockk()
        quizDao = mockk()
        quizAttemptDao = mockk()
        resultDao = mockk()
        repository = QuizRepositoryImpl(remoteSource, functionsSource, quizDao, quizAttemptDao, resultDao)
    }

    private fun fakeEntity(
        localId: Long,
        userId: String = "user1",
        questionId: String = "q1",
    ) = QuizResultEntity(
        localId = localId,
        userId = userId,
        quizId = "quiz1",
        questionId = questionId,
        moduleId = "module1",
        isCorrect = null,
        selectedIndex = 0,
        selectedAnswer = "A",
        answeredAt = 1_000_000L,
        synced = false,
    )

    private fun fakeQuizResult(
        quizId: String = "quiz1",
        provisional: Boolean = false,
    ) = QuizResult(
        quizId = quizId,
        score = 400,
        totalQuestions = 4,
        correctCount = 4,
        percentage = 100,
        xpEarned = 100,
        passed = true,
        timeTaken = 60L,
        provisional = provisional,
    )

    // ── validateAnswerOnline ────────────────────────────────────────────

    @Test
    fun `validateAnswerOnline returns the server's verdict and caches it locally as synced`() =
        runTest {
            val validation = AnswerValidation(questionId = "q1", isCorrect = true, correctIndex = 2, explanation = "Because.")
            coEvery { functionsSource.validateAnswer("quiz1", "q1", 2, any()) } returns validation
            val cachedSlot = slot<QuizResultEntity>()
            coEvery { resultDao.insert(capture(cachedSlot)) } returns 1L

            val result =
                repository.validateAnswerOnline(
                    userId = "user1",
                    quizId = "quiz1",
                    questionId = "q1",
                    selectedIndex = 2,
                    selectedAnswer = "C",
                    moduleId = "module1",
                )

            assertTrue(result is Result.Success)
            assertEquals(validation, (result as Result.Success).data)
            assertEquals(true, cachedSlot.captured.isCorrect)
            assertTrue(cachedSlot.captured.synced)
        }

    @Test
    fun `validateAnswerOnline surfaces a function call failure as Result Error without caching`() =
        runTest {
            coEvery { functionsSource.validateAnswer(any(), any(), any(), any()) } throws RuntimeException("network down")

            val result =
                repository.validateAnswerOnline(
                    userId = "user1",
                    quizId = "quiz1",
                    questionId = "q1",
                    selectedIndex = 0,
                    selectedAnswer = "A",
                    moduleId = "module1",
                )

            assertTrue(result is Result.Error)
            coVerify(exactly = 0) { resultDao.insert(any()) }
        }

    // ── cachePendingAnswer ────────────────────────────────────────────

    @Test
    fun `cachePendingAnswer stores selectedIndex with a null isCorrect and unsynced`() =
        runTest {
            val cachedSlot = slot<QuizResultEntity>()
            coEvery { resultDao.insert(capture(cachedSlot)) } returns 1L

            repository.cachePendingAnswer(
                userId = "user1",
                quizId = "quiz1",
                questionId = "q1",
                moduleId = "module1",
                selectedIndex = 3,
                selectedAnswer = "D",
            )

            assertNull(cachedSlot.captured.isCorrect)
            assertEquals(3, cachedSlot.captured.selectedIndex)
            assertTrue(!cachedSlot.captured.synced)
        }

    // ── syncPendingResults ────────────────────────────────────────────

    @Test
    fun `syncPendingResults returns success with no function calls when nothing pending`() =
        runTest {
            coEvery { resultDao.getPendingResults() } returns emptyList()

            val result = repository.syncPendingResults()

            assertTrue(result is Result.Success)
            coVerify(exactly = 0) { functionsSource.validateAnswersBatch(any()) }
        }

    @Test
    fun `syncPendingResults grades every pending row via a single batch call`() =
        runTest {
            val pending = (1L..5L).map { fakeEntity(it) }
            coEvery { resultDao.getPendingResults() } returns pending
            coEvery { functionsSource.validateAnswersBatch(any()) } answers {
                val requested = firstArg<List<PendingAnswer>>()
                requested.map {
                    BatchAnswerResult(
                        localId = it.localId,
                        validation = AnswerValidation(it.questionId, isCorrect = true, correctIndex = 0, explanation = ""),
                        error = null,
                    )
                }
            }
            coEvery { resultDao.markGraded(any(), any(), any()) } returns Unit

            val result = repository.syncPendingResults()

            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { functionsSource.validateAnswersBatch(match { it.size == 5 }) }
            coVerify(exactly = 5) { resultDao.markGraded(any(), true, any()) }
        }

    @Test
    fun `syncPendingResults splits into multiple batches when pending exceeds MAX_BATCH_SIZE`() =
        runTest {
            // 101 rows -> 2 batches given MAX_BATCH_SIZE = 100
            val pending = (1L..101L).map { fakeEntity(it) }
            coEvery { resultDao.getPendingResults() } returns pending
            coEvery { functionsSource.validateAnswersBatch(any()) } answers {
                val requested = firstArg<List<PendingAnswer>>()
                requested.map { BatchAnswerResult(it.localId, AnswerValidation(it.questionId, true, 0, ""), null) }
            }
            coEvery { resultDao.markGraded(any(), any(), any()) } returns Unit

            val result = repository.syncPendingResults()

            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { functionsSource.validateAnswersBatch(match { it.size == 100 }) }
            coVerify(exactly = 1) { functionsSource.validateAnswersBatch(match { it.size == 1 }) }
            coVerify(exactly = 101) { resultDao.markGraded(any(), any(), any()) }
        }

    @Test
    fun `syncPendingResults leaves a row ungraded when the server reports an error for it`() =
        runTest {
            val pending = listOf(fakeEntity(1L, questionId = "deletedQuestion"), fakeEntity(2L, questionId = "q2"))
            coEvery { resultDao.getPendingResults() } returns pending
            coEvery { functionsSource.validateAnswersBatch(any()) } returns
                listOf(
                    BatchAnswerResult(localId = 1L, validation = null, error = "Question not found."),
                    BatchAnswerResult(localId = 2L, validation = AnswerValidation("q2", true, 1, ""), error = null),
                )
            coEvery { resultDao.markGraded(any(), any(), any()) } returns Unit

            val result = repository.syncPendingResults()

            assertTrue(result is Result.Success)
            // Only the row that graded successfully gets marked — the errored
            // one stays pending so a future sync (or manual cleanup) can retry it.
            coVerify(exactly = 1) { resultDao.markGraded(any(), any(), any()) }
            coVerify(exactly = 1) { resultDao.markGraded(2L, true, any()) }
            coVerify(exactly = 0) { resultDao.markGraded(1L, any(), any()) }
        }

    @Test
    fun `syncPendingResults returns Error and keeps rows pending when a batch call throws`() =
        runTest {
            val pending = (1L..5L).map { fakeEntity(it) }
            coEvery { resultDao.getPendingResults() } returns pending
            coEvery { functionsSource.validateAnswersBatch(any()) } throws RuntimeException("unavailable")

            val result = repository.syncPendingResults()

            assertTrue(result is Result.Error)
            coVerify(exactly = 0) { resultDao.markGraded(any(), any(), any()) }
        }

    // ── saveQuizAttempt / getQuizAttempt ──────────────────────────────

    @Test
    fun `saveQuizAttempt persists the result keyed by resultId, including provisional`() =
        runTest {
            val capturedEntity = slot<QuizAttemptEntity>()
            coEvery { quizAttemptDao.insert(capture(capturedEntity)) } returns Unit

            repository.saveQuizAttempt("result-1", fakeQuizResult(provisional = true))

            assertEquals("result-1", capturedEntity.captured.resultId)
            assertEquals("quiz1", capturedEntity.captured.quizId)
            assertTrue(capturedEntity.captured.provisional)
        }

    @Test
    fun `getQuizAttempt maps a stored entity back into a QuizResult`() =
        runTest {
            coEvery { quizAttemptDao.getById("result-1") } returns
                QuizAttemptEntity(
                    resultId = "result-1",
                    quizId = "quiz1",
                    score = 400,
                    totalQuestions = 4,
                    correctCount = 4,
                    percentage = 100,
                    xpEarned = 100,
                    passed = true,
                    timeTaken = 60L,
                    createdAt = 123L,
                    provisional = false,
                )

            val result = repository.getQuizAttempt("result-1")

            assertEquals(fakeQuizResult(), result)
        }

    @Test
    fun `getQuizAttempt returns null when no attempt is stored for that id`() =
        runTest {
            coEvery { quizAttemptDao.getById("missing") } returns null

            val result = repository.getQuizAttempt("missing")

            assertNull(result)
        }
}
