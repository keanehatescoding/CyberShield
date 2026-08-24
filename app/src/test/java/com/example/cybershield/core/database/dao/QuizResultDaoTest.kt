package com.example.cybershield.core.database.dao

import com.example.cybershield.core.database.entity.QuizAttemptEntity
import com.example.cybershield.core.database.entity.QuizResultEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizResultDaoTest : RoomDbTestBase() {
    private val dao get() = db.quizResultDao()
    private val attemptDao get() = db.quizAttemptDao()

    private fun fakeAttempt(
        resultId: String,
        createdAt: Long,
        provisional: Boolean = false,
        abandoned: Boolean = false,
    ) = QuizAttemptEntity(
        resultId = resultId,
        userId = "user1",
        quizId = "quiz1",
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
        createdAt = createdAt,
        provisional = provisional,
        abandoned = abandoned,
    )

    private fun fakeResult(
        resultId: String = "result-1",
        userId: String = "user1",
        quizId: String = "quiz1",
        questionId: String = "q1",
        isCorrect: Boolean? = null,
        timeRemaining: Int = 10,
        synced: Boolean = false,
    ) = QuizResultEntity(
        resultId = resultId,
        userId = userId,
        quizId = quizId,
        questionId = questionId,
        moduleId = "module1",
        isCorrect = isCorrect,
        selectedIndex = 0,
        selectedAnswer = "A",
        answeredAt = 1_000_000L,
        timeRemaining = timeRemaining,
        synced = synced,
    )

    @Test
    fun `insert autogenerates localId`() =
        runTest {
            dao.insert(fakeResult(questionId = "q1"))
            dao.insert(fakeResult(questionId = "q2"))

            val pending = dao.getPendingResults()

            // autoGenerate primary key should give distinct, non-zero ids
            assertEquals(2, pending.map { it.localId }.toSet().size)
            assertTrue(pending.none { it.localId == 0L })
        }

    @Test
    fun `insert returns the generated localId`() =
        runTest {
            val id1 = dao.insert(fakeResult(questionId = "q1"))
            val id2 = dao.insert(fakeResult(questionId = "q2"))

            assertTrue(id1 != id2)
        }

    @Test
    fun `getPendingResults returns only unsynced rows`() =
        runTest {
            dao.insert(fakeResult(questionId = "q1", synced = false))
            dao.insert(fakeResult(questionId = "q2", isCorrect = true, synced = true))

            val pending = dao.getPendingResults()

            assertEquals(1, pending.size)
            assertTrue(pending.all { !it.synced })
        }

    @Test
    fun `getPendingResults returns empty list when all synced`() =
        runTest {
            dao.insert(fakeResult(isCorrect = true, synced = true))

            assertTrue(dao.getPendingResults().isEmpty())
        }

    @Test
    fun `pending rows have a null isCorrect until graded`() =
        runTest {
            dao.insert(fakeResult(isCorrect = null, synced = false))

            val pending = dao.getPendingResults().single()

            assertNull(pending.isCorrect)
        }

    @Test
    fun `markGraded records the server's verdict and flips synced`() =
        runTest {
            val localId = dao.insert(fakeResult(synced = false))

            dao.markGraded(localId = localId, isCorrect = true, explanation = "Because X.")

            val remaining = dao.getPendingResults()
            assertTrue(remaining.isEmpty()) // no longer pending

            val graded = dao.getResultsForUser("user1").single()
            assertEquals(true, graded.isCorrect)
            assertEquals("Because X.", graded.explanation)
            assertTrue(graded.synced)
        }

    @Test
    fun `markGraded only affects the targeted row`() =
        runTest {
            val idToGrade = dao.insert(fakeResult(questionId = "q1", synced = false))
            dao.insert(fakeResult(questionId = "q2", synced = false))

            dao.markGraded(localId = idToGrade, isCorrect = false, explanation = "Nope.")

            val stillPending = dao.getPendingResults()
            assertEquals(1, stillPending.size)
            assertTrue(stillPending.none { it.localId == idToGrade })
        }

    @Test
    fun `getResultsForUser returns only that user's results`() =
        runTest {
            dao.insert(fakeResult(questionId = "q1", userId = "user1"))
            dao.insert(fakeResult(questionId = "q2", userId = "user2"))

            val result = dao.getResultsForUser("user1")

            assertEquals(1, result.size)
            assertEquals("user1", result.single().userId)
        }

    @Test
    fun `deleteSyncedResults removes only synced rows`() =
        runTest {
            dao.insert(fakeResult(questionId = "q1", isCorrect = true, synced = true))
            dao.insert(fakeResult(questionId = "q2", synced = false))

            dao.deleteSyncedResults()

            val remaining = dao.getResultsForUser("user1")
            assertEquals(1, remaining.size)
            assertTrue(!remaining.single().synced)
        }

    @Test
    fun `deleteByLocalIds removes only specified rows`() =
        runTest {
            dao.insert(fakeResult(questionId = "q1"))
            dao.insert(fakeResult(questionId = "q2"))
            val ids = dao.getPendingResults().map { it.localId }
            val toDelete = listOf(ids.first())

            dao.deleteByLocalIds(toDelete)

            val remaining = dao.getPendingResults()
            assertEquals(1, remaining.size)
            assertTrue(remaining.none { it.localId in toDelete })
        }

    @Test
    fun `insert with REPLACE on duplicate localId overwrites row`() =
        runTest {
            dao.insert(fakeResult().copy(localId = 1L, selectedAnswer = "A"))
            dao.insert(fakeResult().copy(localId = 1L, selectedAnswer = "B"))

            val result = dao.getResultsForUser("user1")

            assertEquals(1, result.size)
            assertEquals("B", result.single().selectedAnswer)
        }

    @Test
    fun `insert with REPLACE on duplicate resultId+questionId overwrites row instead of duplicating`() =
        runTest {
            // Regression test: a resubmission for the same question within the
            // same attempt (e.g. a process-death resume replaying an
            // already-answered question — see QuizViewModel.processAnswer())
            // previously always got a fresh autogenerate localId, so the
            // question was scored/shown twice instead of once. The unique
            // index on (resultId, questionId) makes REPLACE actually dedupe.
            dao.insert(fakeResult(resultId = "attempt-A", questionId = "q1").copy(selectedAnswer = "A"))
            dao.insert(fakeResult(resultId = "attempt-A", questionId = "q1").copy(selectedAnswer = "B"))

            val forAttempt = dao.getResultsForAttempt("attempt-A")

            assertEquals(1, forAttempt.size)
            assertEquals("B", forAttempt.single().selectedAnswer)
        }

    @Test
    fun `insert does not dedupe across different resultIds for the same question`() =
        runTest {
            // A retake of the same quiz (different resultId) answering the
            // same questionId must not collide with the original attempt.
            dao.insert(fakeResult(resultId = "attempt-A", questionId = "q1"))
            dao.insert(fakeResult(resultId = "attempt-B", questionId = "q1"))

            assertEquals(1, dao.getResultsForAttempt("attempt-A").size)
            assertEquals(1, dao.getResultsForAttempt("attempt-B").size)
        }

    @Test
    fun `getResultsForAttempt returns only rows tagged with that resultId`() =
        runTest {
            dao.insert(fakeResult(resultId = "attempt-A", questionId = "q1"))
            dao.insert(fakeResult(resultId = "attempt-A", questionId = "q2"))
            dao.insert(fakeResult(resultId = "attempt-B", questionId = "q1")) // a retake of the same quiz

            val forAttemptA = dao.getResultsForAttempt("attempt-A")

            assertEquals(2, forAttemptA.size)
            assertTrue(forAttemptA.all { it.resultId == "attempt-A" })
        }

    @Test
    fun `countUnsyncedForAttempt is zero once every answer in that attempt is synced`() =
        runTest {
            dao.insert(fakeResult(resultId = "attempt-A", questionId = "q1", isCorrect = true, synced = true))
            dao.insert(fakeResult(resultId = "attempt-A", questionId = "q2", isCorrect = true, synced = true))

            assertEquals(0, dao.countUnsyncedForAttempt("attempt-A"))
        }

    @Test
    fun `countUnsyncedForAttempt reflects only that attempt's unsynced rows, not other attempts'`() =
        runTest {
            dao.insert(fakeResult(resultId = "attempt-A", questionId = "q1", synced = false))
            dao.insert(fakeResult(resultId = "attempt-B", questionId = "q1", isCorrect = true, synced = true))

            assertEquals(1, dao.countUnsyncedForAttempt("attempt-A"))
            assertEquals(0, dao.countUnsyncedForAttempt("attempt-B"))
        }

    @Test
    fun `deleteForFinalizedAttemptsOlderThan removes answers only for old, finalized attempts`() =
        runTest {
            attemptDao.insert(fakeAttempt(resultId = "old-final", createdAt = 1_000L, provisional = false))
            attemptDao.insert(fakeAttempt(resultId = "new-final", createdAt = 5_000L, provisional = false))
            dao.insert(fakeResult(resultId = "old-final", questionId = "q1"))
            dao.insert(fakeResult(resultId = "new-final", questionId = "q1"))

            dao.deleteForFinalizedAttemptsOlderThan(cutoff = 3_000L)

            assertTrue(dao.getResultsForAttempt("old-final").isEmpty())
            assertEquals(1, dao.getResultsForAttempt("new-final").size)
        }

    @Test
    fun `deleteForFinalizedAttemptsOlderThan never removes answers for a still-provisional attempt`() =
        runTest {
            // Even though it's old, a provisional attempt's answers are still
            // needed by getAttemptsReadyToFinalize() to recompute its score —
            // deleting them here would silently strand it half-graded forever.
            attemptDao.insert(fakeAttempt(resultId = "stuck", createdAt = 1_000L, provisional = true))
            dao.insert(fakeResult(resultId = "stuck", questionId = "q1", isCorrect = true, synced = true))

            dao.deleteForFinalizedAttemptsOlderThan(cutoff = 3_000L)

            assertEquals(1, dao.getResultsForAttempt("stuck").size)
        }

    @Test
    fun `deleteForFinalizedAttemptsOlderThan removes answers for an old abandoned attempt`() =
        runTest {
            attemptDao.insert(
                fakeAttempt(resultId = "abandoned-1", createdAt = 1_000L, provisional = true, abandoned = true),
            )
            dao.insert(fakeResult(resultId = "abandoned-1", questionId = "q1"))

            dao.deleteForFinalizedAttemptsOlderThan(cutoff = 3_000L)

            assertTrue(dao.getResultsForAttempt("abandoned-1").isEmpty())
        }
}
