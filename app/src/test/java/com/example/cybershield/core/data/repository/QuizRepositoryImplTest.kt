package com.example.cybershield.core.data.repository

import com.example.cybershield.core.database.dao.QuizDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.database.entity.QuizResultEntity
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.firebase.FirestoreQuizDataSource
import com.example.cybershield.core.firebase.QuizResultUpload
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers SyncQuizResultsWorker's former responsibilities, now owned by
 * QuizRepositoryImpl.syncPendingResults(): chunked Firestore commits and
 * per-chunk mark-and-delete so a later chunk's failure doesn't force
 * re-upload of chunks that already committed successfully.
 */
class QuizRepositoryImplTest {
    private lateinit var remoteSource: FirestoreQuizDataSource
    private lateinit var quizDao: QuizDao
    private lateinit var resultDao: QuizResultDao
    private lateinit var repository: QuizRepositoryImpl

    @Before
    fun setUp() {
        remoteSource = mockk()
        quizDao = mockk()
        resultDao = mockk()
        repository = QuizRepositoryImpl(remoteSource, quizDao, resultDao)
    }

    private fun fakeEntity(
        localId: Long,
        userId: String = "user1",
    ) = QuizResultEntity(
        localId = localId,
        userId = userId,
        quizId = "quiz1",
        moduleId = "module1",
        isCorrect = true,
        selectedAnswer = "A",
        answeredAt = 1_000_000L,
        synced = false,
    )

    @Test
    fun `syncPendingResults returns success with no Firestore calls when nothing pending`() =
        runTest {
            coEvery { resultDao.getPendingResults() } returns emptyList()

            val result = repository.syncPendingResults()

            assertTrue(result is Result.Success)
            coVerify(exactly = 0) { remoteSource.uploadQuizResults(any()) }
            coVerify(exactly = 0) { resultDao.markSyncedAndDelete(any()) }
        }

    @Test
    fun `syncPendingResults commits single batch and deletes synced rows for small pending list`() =
        runTest {
            val pending = (1L..5L).map { fakeEntity(it) }
            coEvery { resultDao.getPendingResults() } returns pending
            coEvery { remoteSource.uploadQuizResults(any()) } returns Unit
            val deletedIdsSlot = slot<List<Long>>()
            coEvery { resultDao.markSyncedAndDelete(capture(deletedIdsSlot)) } returns Unit

            val result = repository.syncPendingResults()

            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { remoteSource.uploadQuizResults(any()) }
            coVerify(exactly = 1) { resultDao.markSyncedAndDelete(any()) }
            assertTrue(deletedIdsSlot.captured.toSet() == pending.map { it.localId }.toSet())
        }

    @Test
    fun `syncPendingResults splits into multiple uploads when pending exceeds chunk size`() =
        runTest {
            // 451 rows -> 2 chunks given SYNC_CHUNK_SIZE = 450
            val pending = (1L..451L).map { fakeEntity(it) }
            coEvery { resultDao.getPendingResults() } returns pending
            coEvery { remoteSource.uploadQuizResults(any()) } returns Unit
            coEvery { resultDao.markSyncedAndDelete(any()) } returns Unit

            val result = repository.syncPendingResults()

            assertTrue(result is Result.Success)
            coVerify(exactly = 2) { remoteSource.uploadQuizResults(any()) }
            coVerify(exactly = 1) { resultDao.markSyncedAndDelete(match { it.size == 450 }) }
            coVerify(exactly = 1) { resultDao.markSyncedAndDelete(match { it.size == 1 }) }
        }

    @Test
    fun `syncPendingResults keeps earlier chunks marked-and-deleted when a later chunk fails`() =
        runTest {
            val pending = (1L..451L).map { fakeEntity(it) } // 2 chunks
            coEvery { resultDao.getPendingResults() } returns pending
            coEvery { resultDao.markSyncedAndDelete(any()) } returns Unit

            var callCount = 0
            coEvery { remoteSource.uploadQuizResults(any()) } answers {
                callCount++
                if (callCount == 2) throw RuntimeException("boom")
            }

            val result = repository.syncPendingResults()

            // This is the actual bug fix: chunk 1 committed to Firestore and is marked
            // synced-and-deleted *before* chunk 2 is attempted, so its failure doesn't
            // undo chunk 1's already-successful work.
            assertTrue(result is Result.Error)
            coVerify(exactly = 1) { resultDao.markSyncedAndDelete(match { it.size == 450 }) }
            coVerify(exactly = 0) { resultDao.markSyncedAndDelete(match { it.size == 1 }) }
        }

    @Test
    fun `syncPendingResults uploads results mapped from entity fields`() =
        runTest {
            val entity = fakeEntity(localId = 7L, userId = "userX")
            coEvery { resultDao.getPendingResults() } returns listOf(entity)
            val uploadedSlot = slot<List<QuizResultUpload>>()
            coEvery { remoteSource.uploadQuizResults(capture(uploadedSlot)) } returns Unit
            coEvery { resultDao.markSyncedAndDelete(any()) } returns Unit

            repository.syncPendingResults()

            val uploaded = uploadedSlot.captured.single()
            assertTrue(uploaded.localId == entity.localId)
            assertTrue(uploaded.userId == entity.userId)
            assertTrue(uploaded.quizId == entity.quizId)
            assertTrue(uploaded.moduleId == entity.moduleId)
            assertTrue(uploaded.isCorrect == entity.isCorrect)
            assertTrue(uploaded.selectedAnswer == entity.selectedAnswer)
            assertTrue(uploaded.answeredAt == entity.answeredAt)
        }
}
