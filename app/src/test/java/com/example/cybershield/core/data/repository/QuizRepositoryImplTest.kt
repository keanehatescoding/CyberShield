package com.example.cybershield.core.data.repository

import com.example.cybershield.core.database.dao.QuizAttemptDao
import com.example.cybershield.core.database.dao.QuizDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.database.entity.QuizAttemptEntity
import com.example.cybershield.core.database.entity.QuizResultEntity
import com.example.cybershield.core.domain.model.AnswerValidation
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.domain.util.QuizScoring
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
 * saveQuizAttempt/getQuizAttempt/getAttemptsReadyToFinalize/finalizeAttempt,
 * which back the resultId-based result screen and background finalization
 * of offline-graded attempts.
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
        resultId: String = "result-1",
        userId: String = "user1",
        questionId: String = "q1",
        isCorrect: Boolean? = null,
        timeRemaining: Int = 10,
        synced: Boolean = false,
    ) = QuizResultEntity(
        localId = localId,
        resultId = resultId,
        userId = userId,
        quizId = "quiz1",
        questionId = questionId,
        moduleId = "module1",
        isCorrect = isCorrect,
        selectedIndex = 0,
        selectedAnswer = "A",
        answeredAt = 1_000_000L,
        timeRemaining = timeRemaining,
        synced = synced,
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

    private fun fakeAttempt(
        resultId: String = "result-1",
        userId: String = "user1",
        quizId: String = "quiz1",
        provisional: Boolean = true,
    ) = QuizAttemptEntity(
        resultId = resultId,
        userId = userId,
        quizId = quizId,
        moduleId = "module1",
        moduleName = "Phishing Awareness",
        quizTitle = "Phishing Quiz",
        score = 0,
        totalQuestions = 4,
        correctCount = 0,
        percentage = 0,
        xpEarned = 0,
        passed = false,
        timeTaken = 60L,
        createdAt = 1_000_000L,
        provisional = provisional,
    )

    // ── validateAnswerOnline ────────────────────────────────────────────

    @Test
    fun `validateAnswerOnline returns the server's verdict and caches it locally as synced, tagged with resultId`() =
        runTest {
            val validation = AnswerValidation(questionId = "q1", isCorrect = true, correctIndex = 2, explanation = "Because.")
            coEvery { functionsSource.validateAnswer("quiz1", "q1", 2, any()) } returns validation
            val cachedSlot = slot<QuizResultEntity>()
            coEvery { resultDao.insert(capture(cachedSlot)) } returns 1L

            val result =
                repository.validateAnswerOnline(
                    userId = "user1",
                    resultId = "result-1",
                    quizId = "quiz1",
                    questionId = "q1",
                    selectedIndex = 2,
                    selectedAnswer = "C",
                    moduleId = "module1",
                    timeRemaining = 15,
                )

            assertTrue(result is Result.Success)
            assertEquals(validation, (result as Result.Success).data)
            assertEquals("result-1", cachedSlot.captured.resultId)
            assertEquals(true, cachedSlot.captured.isCorrect)
            assertEquals(15, cachedSlot.captured.timeRemaining)
            assertTrue(cachedSlot.captured.synced)
        }

    @Test
    fun `validateAnswerOnline surfaces a function call failure as Result Error without caching`() =
        runTest {
            coEvery { functionsSource.validateAnswer(any(), any(), any(), any()) } throws RuntimeException("network down")

            val result =
                repository.validateAnswerOnline(
                    userId = "user1",
                    resultId = "result-1",
                    quizId = "quiz1",
                    questionId = "q1",
                    selectedIndex = 0,
                    selectedAnswer = "A",
                    moduleId = "module1",
                    timeRemaining = 10,
                )

            assertTrue(result is Result.Error)
            coVerify(exactly = 0) { resultDao.insert(any()) }
        }

    // ── cachePendingAnswer ────────────────────────────────────────────

    @Test
    fun `cachePendingAnswer stores selectedIndex, resultId, and timeRemaining with a null isCorrect and unsynced`() =
        runTest {
            val cachedSlot = slot<QuizResultEntity>()
            coEvery { resultDao.insert(capture(cachedSlot)) } returns 1L

            repository.cachePendingAnswer(
                userId = "user1",
                resultId = "result-1",
                quizId = "quiz1",
                questionId = "q1",
                moduleId = "module1",
                selectedIndex = 3,
                selectedAnswer = "D",
                timeRemaining = 20,
            )

            assertEquals("result-1", cachedSlot.captured.resultId)
            assertNull(cachedSlot.captured.isCorrect)
            assertEquals(3, cachedSlot.captured.selectedIndex)
            assertEquals(20, cachedSlot.captured.timeRemaining)
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
    fun `saveQuizAttempt persists the result keyed by resultId, including userId, module context, and provisional`() =
        runTest {
            val capturedEntity = slot<QuizAttemptEntity>()
            coEvery { quizAttemptDao.insert(capture(capturedEntity)) } returns Unit

            repository.saveQuizAttempt(
                resultId = "result-1",
                userId = "user1",
                moduleId = "module1",
                moduleName = "Phishing Awareness",
                quizTitle = "Phishing Quiz",
                result = fakeQuizResult(provisional = true),
            )

            assertEquals("result-1", capturedEntity.captured.resultId)
            assertEquals("user1", capturedEntity.captured.userId)
            assertEquals("module1", capturedEntity.captured.moduleId)
            assertEquals("Phishing Awareness", capturedEntity.captured.moduleName)
            assertEquals("Phishing Quiz", capturedEntity.captured.quizTitle)
            assertEquals("quiz1", capturedEntity.captured.quizId)
            assertTrue(capturedEntity.captured.provisional)
        }

    @Test
    fun `getQuizAttempt maps a stored entity back into a QuizResult`() =
        runTest {
            coEvery { quizAttemptDao.getById("result-1") } returns
                QuizAttemptEntity(
                    resultId = "result-1",
                    userId = "user1",
                    quizId = "quiz1",
                    moduleId = "module1",
                    moduleName = "Phishing Awareness",
                    quizTitle = "Phishing Quiz",
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

    // ── getAttemptsReadyToFinalize ─────────────────────────────────────

    @Test
    fun `getAttemptsReadyToFinalize skips an attempt with any unsynced answer`() =
        runTest {
            coEvery { quizAttemptDao.getProvisionalAttempts() } returns listOf(fakeAttempt())
            coEvery { resultDao.countUnsyncedForAttempt("result-1") } returns 1

            val ready = repository.getAttemptsReadyToFinalize()

            assertTrue(ready.isEmpty())
            coVerify(exactly = 0) { resultDao.getResultsForAttempt(any()) }
        }

    @Test
    fun `getAttemptsReadyToFinalize skips an attempt with no persisted answers`() =
        runTest {
            coEvery { quizAttemptDao.getProvisionalAttempts() } returns listOf(fakeAttempt())
            coEvery { resultDao.countUnsyncedForAttempt("result-1") } returns 0
            coEvery { resultDao.getResultsForAttempt("result-1") } returns emptyList()

            val ready = repository.getAttemptsReadyToFinalize()

            assertTrue(ready.isEmpty())
        }

    @Test
    fun `getAttemptsReadyToFinalize recomputes correctCount and score from graded answers, not the stored attempt`() =
        runTest {
            coEvery { quizAttemptDao.getProvisionalAttempts() } returns listOf(fakeAttempt())
            coEvery { resultDao.countUnsyncedForAttempt("result-1") } returns 0
            coEvery { resultDao.getResultsForAttempt("result-1") } returns
                listOf(
                    fakeEntity(1L, isCorrect = true, timeRemaining = 10, synced = true),
                    fakeEntity(2L, isCorrect = false, timeRemaining = 5, synced = true),
                    fakeEntity(3L, isCorrect = true, timeRemaining = 0, synced = true),
                )

            val ready = repository.getAttemptsReadyToFinalize()

            assertEquals(1, ready.size)
            val attempt = ready.single()
            assertEquals("result-1", attempt.resultId)
            assertEquals("user1", attempt.userId)
            assertEquals(3, attempt.totalQuestions)
            assertEquals(2, attempt.correctCount)
            // (100 + 10*5) + 0 (wrong) + (100 + 0*5) = 150 + 0 + 100 = 250
            val expectedScore =
                QuizScoring.pointsFor(true, 10) + QuizScoring.pointsFor(false, 5) + QuizScoring.pointsFor(true, 0)
            assertEquals(expectedScore, attempt.score)
        }

    @Test
    fun `getAttemptsReadyToFinalize carries module context through for certificate generation`() =
        runTest {
            coEvery { quizAttemptDao.getProvisionalAttempts() } returns listOf(fakeAttempt())
            coEvery { resultDao.countUnsyncedForAttempt("result-1") } returns 0
            coEvery { resultDao.getResultsForAttempt("result-1") } returns listOf(fakeEntity(1L, isCorrect = true, synced = true))

            val attempt = repository.getAttemptsReadyToFinalize().single()

            assertEquals("module1", attempt.moduleId)
            assertEquals("Phishing Awareness", attempt.moduleName)
            assertEquals("Phishing Quiz", attempt.quizTitle)
        }

    // ── finalizeAttempt ────────────────────────────────────────────────

    @Test
    fun `finalizeAttempt delegates to QuizAttemptDao finalize`() =
        runTest {
            coEvery { quizAttemptDao.finalize(any(), any(), any(), any(), any(), any()) } returns Unit

            repository.finalizeAttempt(
                resultId = "result-1",
                score = 250,
                correctCount = 2,
                percentage = 67,
                xpEarned = 20,
                passed = false,
            )

            coVerify {
                quizAttemptDao.finalize(
                    resultId = "result-1",
                    score = 250,
                    correctCount = 2,
                    percentage = 67,
                    xpEarned = 20,
                    passed = false,
                )
            }
        }
}
