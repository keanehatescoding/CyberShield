package com.example.cybershield.core.database.dao

import com.example.cybershield.core.database.entity.QuizResultEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizResultDaoTest : RoomDbTestBase() {
    private val dao get() = db.quizResultDao()

    private fun fakeResult(
        userId: String = "user1",
        quizId: String = "quiz1",
        synced: Boolean = false,
    ) = QuizResultEntity(
        userId = userId,
        quizId = quizId,
        moduleId = "module1",
        isCorrect = true,
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
    fun `getPendingResults returns only unsynced rows`() =
        runTest {
            dao.insert(fakeResult(synced = false))
            dao.insert(fakeResult(synced = true))

            val pending = dao.getPendingResults()

            assertEquals(1, pending.size)
            assertTrue(pending.all { !it.synced })
        }

    @Test
    fun `getPendingResults returns empty list when all synced`() =
        runTest {
            dao.insert(fakeResult(synced = true))

            assertTrue(dao.getPendingResults().isEmpty())
        }

    @Test
    fun `markSynced flips synced flag for given ids only`() =
        runTest {
            dao.insert(fakeResult(synced = false))
            dao.insert(fakeResult(synced = false))
            val ids = dao.getPendingResults().map { it.localId }
            val idToMark = ids.first()

            dao.markSynced(listOf(idToMark))

            val stillPending = dao.getPendingResults()
            assertEquals(1, stillPending.size)
            assertTrue(stillPending.none { it.localId == idToMark })
        }

    @Test
    fun `markSyncedAndDelete marks then deletes given rows transactionally`() =
        runTest {
            dao.insert(fakeResult(synced = false))
            dao.insert(fakeResult(synced = false))
            val ids = dao.getPendingResults().map { it.localId }

            dao.markSyncedAndDelete(ids)

            // Rows should be gone entirely, not just marked synced
            assertTrue(dao.getPendingResults().isEmpty())
            assertTrue(dao.getResultsForUser("user1").isEmpty())
        }

    @Test
    fun `markSyncedAndDelete with empty list is a no-op`() =
        runTest {
            dao.insert(fakeResult(synced = false))

            dao.markSyncedAndDelete(emptyList())

            assertEquals(1, dao.getPendingResults().size)
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
            dao.insert(fakeResult(synced = true))
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
