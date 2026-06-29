package com.example.cybershield.core.sync

import android.content.Context
import androidx.work.WorkerParameters
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.database.entity.QuizResultEntity
import androidx.work.ListenableWorker.Result as WorkResult
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM unit test — no Robolectric, no work-testing artifact (neither is on the
 * testImplementation classpath per the project's libs.versions.toml). Confirmed doWork() never
 * touches `context` directly, so a relaxed mockk<Context>() stands in for it; the worker is
 * constructed directly via its @AssistedInject constructor rather than TestListenableWorkerBuilder.
 * CONFIRMED AGAINST REAL SOURCE:
 *  - QuizResultEntity: localId: Long, userId: String, quizId: String, moduleId: String,
 *    isCorrect: Boolean, selectedAnswer: String, answeredAt: Long, synced: Boolean
 *  - QuizResultDao.getPendingResults(): suspend fun returning List<QuizResultEntity>
 *  - QuizResultDao.markSyncedAndDelete(localIds: List<Long>): suspend fun, @Transaction wrapping
 *    markSynced + deleteByLocalIds — mocked at the interface boundary here, so Room's actual
 *    transaction behavior is NOT exercised by this test (that belongs in a DAO/instrumented test).
 *  - NetworkMonitor.isCurrentlyOnline() is a plain (non-suspend) fun returning Boolean
 */
class SyncQuizResultsWorkerTest {

    private lateinit var resultDao: QuizResultDao
    private lateinit var firestore: FirebaseFirestore
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var context: Context

    // Firestore mock chain pieces, rebuilt per test so call captures don't bleed across tests
    private lateinit var writeBatch: WriteBatch

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        resultDao = mockk()
        firestore = mockk()
        networkMonitor = mockk()
        writeBatch = mockk(relaxed = true)

        // firestore.batch() always hands back the same mock batch in a given test
        every { firestore.batch() } returns writeBatch
        // batch.set(ref, map) — relaxed mock already returns the batch itself (chaining), but
        // be explicit so intent is clear and so it survives if relaxed=true is removed later.
        every { writeBatch.set(any<DocumentReference>(), any<Map<String, Any?>>()) } returns writeBatch
        every { writeBatch.commit() } returns Tasks.forResult(null)

        // firestore.collection(...).document(...).collection(...).document(...) chain
        val usersCollection = mockk<CollectionReference>()
        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document(any()) } answers {
            val userDocRef = mockk<DocumentReference>(relaxed = true)
            val resultsCollection = mockk<CollectionReference>()
            every { userDocRef.collection("quizResults") } returns resultsCollection
            every { resultsCollection.document(any()) } returns mockk(relaxed = true)
            userDocRef
        }
    }

    private fun fakeEntity(localId: Long, userId: String = "user1") = QuizResultEntity(
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
    fun `doWork returns retry when offline`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false

        val worker = directConstruct(runAttemptCount = 0)
        val result = worker.doWork()

        assertTrue(result is WorkResult.Retry)
        coVerify(exactly = 0) { resultDao.getPendingResults() }
    }

    @Test
    fun `doWork returns success when no pending results`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { resultDao.getPendingResults() } returns emptyList()

        val worker = directConstruct(runAttemptCount = 0)
        val result = worker.doWork()

        assertTrue(result is WorkResult.Success)
        coVerify(exactly = 0) { resultDao.markSyncedAndDelete(any()) }
    }

    @Test
    fun `doWork commits single batch and deletes synced rows for small pending list`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val pending = (1L..5L).map { fakeEntity(it) }
        coEvery { resultDao.getPendingResults() } returns pending
        val deletedIdsSlot = slot<List<Long>>()
        coEvery { resultDao.markSyncedAndDelete(capture(deletedIdsSlot)) } returns Unit

        val worker = directConstruct(runAttemptCount = 0)
        val result = worker.doWork()

        assertTrue(result is WorkResult.Success)
        coVerify(exactly = 1) { resultDao.markSyncedAndDelete(any()) }
        assertTrue(deletedIdsSlot.captured.toSet() == pending.map { it.localId }.toSet())
        // Exactly one commit — under BATCH_CHUNK_SIZE (450), should be a single chunk
        coVerify(exactly = 1) { writeBatch.commit() }
    }

    @Test
    fun `doWork splits into multiple commits when pending exceeds chunk size`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        // 451 rows -> 2 chunks given BATCH_CHUNK_SIZE = 450
        val pending = (1L..451L).map { fakeEntity(it) }
        coEvery { resultDao.getPendingResults() } returns pending
        coEvery { resultDao.markSyncedAndDelete(any()) } returns Unit

        val worker = directConstruct(runAttemptCount = 0)
        val result = worker.doWork()

        assertTrue(result is WorkResult.Success)
        coVerify(exactly = 2) { firestore.batch() }
        coVerify(exactly = 2) { writeBatch.commit() }
        coVerify(exactly = 1) { resultDao.markSyncedAndDelete(match { it.size == 451 }) }
    }

    @Test
    fun `doWork retries on firestore exception when under max retries`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { resultDao.getPendingResults() } returns listOf(fakeEntity(1L))
        every { writeBatch.commit() } returns Tasks.forException(RuntimeException("quota exceeded"))

        val worker = directConstruct(runAttemptCount = 1) // < MAX_RETRIES (3)
        val result = worker.doWork()

        assertTrue(result is WorkResult.Retry)
        coVerify(exactly = 0) { resultDao.markSyncedAndDelete(any()) }
    }

    @Test
    fun `doWork returns failure when exception occurs at max retries`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { resultDao.getPendingResults() } returns listOf(fakeEntity(1L))
        every { writeBatch.commit() } returns Tasks.forException(RuntimeException("quota exceeded"))

        val worker = directConstruct(runAttemptCount = 3) // == MAX_RETRIES
        val result = worker.doWork()

        assertTrue(result is WorkResult.Failure)
    }

    @Test
    fun `doWork does not delete already-synced chunks if a later chunk fails`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val pending = (1L..451L).map { fakeEntity(it) } // 2 chunks
        coEvery { resultDao.getPendingResults() } returns pending
        coEvery { resultDao.markSyncedAndDelete(any()) } returns Unit

        var callCount = 0
        every { writeBatch.commit() } answers {
            callCount++
            if (callCount == 1) Tasks.forResult(null) else Tasks.forException(RuntimeException("boom"))
        }

        val worker = directConstruct(runAttemptCount = 0)
        val result = worker.doWork()

        // Whole doWork is wrapped in one try/catch, so a failure on chunk 2 means
        // markSyncedAndDelete is never called at all — not even for chunk 1's synced IDs.
        // This is worth flagging as a real behavior gap: chunk 1 successfully committed to
        // Firestore but its local rows are never marked synced, so they'll be resent next run.
        // That's safe (idempotent overwrite via deterministic doc IDs) but wasteful. Confirming
        // this is intentional, not testing around a bug silently.
        assertTrue(result is WorkResult.Retry)
        coVerify(exactly = 0) { resultDao.markSyncedAndDelete(any()) }
    }

    private fun directConstruct(runAttemptCount: Int): SyncQuizResultsWorker {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.runAttemptCount } returns runAttemptCount
        return SyncQuizResultsWorker(
            context = context,
            workerParams = workerParams,
            resultDao = resultDao,
            firestore = firestore,
            networkMonitor = networkMonitor,
        )
    }
}