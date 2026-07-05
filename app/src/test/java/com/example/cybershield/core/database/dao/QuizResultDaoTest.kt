package com.example.cybershield.core.database.dao

import com.example.cybershield.core.database.entity.QuizResultEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizResultDaoTest : RoomDbTestBase() {
    private val dao get() = db.quizResultDao()

    private fun fakeResult(
        userId: String = "user1",
        quizId: String = "quiz1",
        questionId: String = "q1",
        isCorrect: Boolean? = null,
        synced: Boolean = false,
    ) = QuizResultEntity(
        userId = userId,
        quizId = quizId,
        questionId = questionId,
        moduleId = "module1",
        isCorrect = isCorrect,
        selectedIndex = 0,
        selectedAnswer = "A",
        answeredAt = 1_000_000L,
        synced = synced,
    )

    @Test
    fun `insert autogenerates localId`() =
        runTest {
            dao.insert(fakeResult())
            dao.insert(fakeResult())

            val pending = dao.getPendingResults()

            // autoGenerate primary key should give distinct, non-zero ids
            assertEquals(2, pending.map { it.localId }.toSet().size)
            assertTrue(pending.none { it.localId == 0L })
        }

    @Test
    fun `insert returns the generated localId`() =
        runTest {
            val id1 = dao.insert(fakeResult())
            val id2 = dao.insert(fakeResult())

            assertTrue(id1 != id2)
        }

    @Test
    fun `getPendingResults returns only unsynced rows`() =
        runTest {
            dao.insert(fakeResult(synced = false))
            dao.insert(fakeResult(isCorrect = true, synced = true))

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
            val idToGrade = dao.insert(fakeResult(synced = false))
            dao.insert(fakeResult(synced = false))

            dao.markGraded(localId = idToGrade, isCorrect = false, explanation = "Nope.")

            val stillPending = dao.getPendingResults()
            assertEquals(1, stillPending.size)
            assertTrue(stillPending.none { it.localId == idToGrade })
        }

    @Test
    fun `getResultsForUser returns only that user's results`() =
        runTest {
            dao.insert(fakeResult(userId = "user1"))
            dao.insert(fakeResult(userId = "user2"))

            val result = dao.getResultsForUser("user1")

            assertEquals(1, result.size)
            assertEquals("user1", result.single().userId)
        }

    @Test
    fun `deleteSyncedResults removes only synced rows`() =
        runTest {
            dao.insert(fakeResult(isCorrect = true, synced = true))
            dao.insert(fakeResult(synced = false))

            dao.deleteSyncedResults()

            val remaining = dao.getResultsForUser("user1")
            assertEquals(1, remaining.size)
            assertTrue(!remaining.single().synced)
        }

    @Test
    fun `deleteByLocalIds removes only specified rows`() =
        runTest {
            dao.insert(fakeResult())
            dao.insert(fakeResult())
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
}
