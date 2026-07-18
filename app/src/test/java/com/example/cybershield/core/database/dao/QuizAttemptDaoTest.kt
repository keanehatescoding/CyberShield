package com.example.cybershield.core.database.dao

import com.example.cybershield.core.database.entity.QuizAttemptEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizAttemptDaoTest : RoomDbTestBase() {
    private val dao get() = db.quizAttemptDao()

    private fun fakeAttempt(
        resultId: String = "result-1",
        userId: String = "user1",
        quizId: String = "quiz1",
        provisional: Boolean = false,
        createdAt: Long = 1_000_000L,
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
        createdAt = createdAt,
        provisional = provisional,
    )

    @Test
    fun `insert then getById returns the stored attempt`() =
        runTest {
            dao.insert(fakeAttempt(resultId = "result-1"))

            val fetched = dao.getById("result-1")

            assertEquals("result-1", fetched?.resultId)
        }

    @Test
    fun `getById returns null for an unknown resultId`() =
        runTest {
            assertNull(dao.getById("does-not-exist"))
        }

    @Test
    fun `insert with REPLACE on duplicate resultId overwrites the row`() =
        runTest {
            dao.insert(fakeAttempt(resultId = "result-1").copy(score = 100))
            dao.insert(fakeAttempt(resultId = "result-1").copy(score = 250))

            assertEquals(250, dao.getById("result-1")?.score)
        }

    @Test
    fun `getProvisionalAttempts returns only attempts still awaiting sync`() =
        runTest {
            dao.insert(fakeAttempt(resultId = "provisional-1", provisional = true))
            dao.insert(fakeAttempt(resultId = "final-1", provisional = false))

            val provisional = dao.getProvisionalAttempts()

            assertEquals(1, provisional.size)
            assertEquals("provisional-1", provisional.single().resultId)
        }

    @Test
    fun `getProvisionalAttempts returns empty list when nothing is pending`() =
        runTest {
            dao.insert(fakeAttempt(provisional = false))

            assertTrue(dao.getProvisionalAttempts().isEmpty())
        }

    @Test
    fun `finalize updates score, correctCount, percentage, xpEarned, passed, and clears provisional`() =
        runTest {
            dao.insert(fakeAttempt(resultId = "result-1", provisional = true))

            dao.finalize(
                resultId = "result-1",
                score = 250,
                correctCount = 3,
                percentage = 75,
                xpEarned = 30,
                passed = true,
            )

            val finalized = dao.getById("result-1")
            assertEquals(250, finalized?.score)
            assertEquals(3, finalized?.correctCount)
            assertEquals(75, finalized?.percentage)
            assertEquals(30, finalized?.xpEarned)
            assertEquals(true, finalized?.passed)
            assertEquals(false, finalized?.provisional)
        }

    @Test
    fun `finalize only affects the targeted attempt`() =
        runTest {
            dao.insert(fakeAttempt(resultId = "result-A", provisional = true))
            dao.insert(fakeAttempt(resultId = "result-B", provisional = true))

            dao.finalize(resultId = "result-A", score = 100, correctCount = 1, percentage = 25, xpEarned = 10, passed = false)

            val stillProvisional = dao.getProvisionalAttempts()
            assertEquals(1, stillProvisional.size)
            assertEquals("result-B", stillProvisional.single().resultId)
        }

    @Test
    fun `deleteOlderThan removes only attempts created before the cutoff`() =
        runTest {
            dao.insert(fakeAttempt(resultId = "old", createdAt = 1_000L))
            dao.insert(fakeAttempt(resultId = "new", createdAt = 5_000L))

            dao.deleteOlderThan(cutoff = 3_000L)

            assertNull(dao.getById("old"))
            assertEquals("new", dao.getById("new")?.resultId)
        }

    @Test
    fun `updateFinalizeFailure records the count without abandoning below the caller's threshold`() =
        runTest {
            dao.insert(fakeAttempt(resultId = "result-1", provisional = true))

            dao.updateFinalizeFailure(resultId = "result-1", count = 3, abandoned = false)

            val updated = dao.getById("result-1")
            assertEquals(3, updated?.finalizeFailureCount)
            assertEquals(false, updated?.abandoned)
            // Still provisional=true, so it's still returned by getProvisionalAttempts —
            // the caller (QuizRepositoryImpl) hasn't hit its retry limit yet.
            assertEquals(1, dao.getProvisionalAttempts().size)
        }

    @Test
    fun `updateFinalizeFailure with abandoned=true clears provisional and excludes it from getProvisionalAttempts`() =
        runTest {
            dao.insert(fakeAttempt(resultId = "result-1", provisional = true))
            dao.insert(fakeAttempt(resultId = "result-2", provisional = true))

            dao.updateFinalizeFailure(resultId = "result-1", count = 5, abandoned = true)

            val abandoned = dao.getById("result-1")
            assertEquals(true, abandoned?.abandoned)
            assertEquals(false, abandoned?.provisional)

            val stillPending = dao.getProvisionalAttempts()
            assertEquals(1, stillPending.size)
            assertEquals("result-2", stillPending.single().resultId)
        }
}
