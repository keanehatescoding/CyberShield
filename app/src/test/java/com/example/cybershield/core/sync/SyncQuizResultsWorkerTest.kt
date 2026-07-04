package com.example.cybershield.core.sync

import android.content.Context
import androidx.work.WorkerParameters
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.Result as DomainResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import androidx.work.ListenableWorker.Result as WorkResult

/**
 * The Firestore batching, chunking, and per-chunk mark-and-delete behavior
 * now lives in QuizRepositoryImpl (see QuizRepositoryImplTest). This worker
 * test only covers doWork()'s own responsibilities: the offline guard, and
 * mapping QuizRepository.syncPendingResults()'s outcome to a WorkManager
 * Result, including the runAttemptCount retry/failure cutoff.
 */
class SyncQuizResultsWorkerTest {
    private lateinit var quizRepository: QuizRepository
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        quizRepository = mockk()
        networkMonitor = mockk()
    }

    @Test
    fun `doWork returns retry when offline`() =
        runTest {
            every { networkMonitor.isCurrentlyOnline() } returns false

            val worker = directConstruct(runAttemptCount = 0)
            val result = worker.doWork()

            assertTrue(result is WorkResult.Retry)
            coVerify(exactly = 0) { quizRepository.syncPendingResults() }
        }

    @Test
    fun `doWork returns success when repository reports success`() =
        runTest {
            every { networkMonitor.isCurrentlyOnline() } returns true
            coEvery { quizRepository.syncPendingResults() } returns DomainResult.Success(Unit)

            val worker = directConstruct(runAttemptCount = 0)
            val result = worker.doWork()

            assertTrue(result is WorkResult.Success)
        }

    @Test
    fun `doWork retries on repository error when under max retries`() =
        runTest {
            every { networkMonitor.isCurrentlyOnline() } returns true
            coEvery { quizRepository.syncPendingResults() } returns
                DomainResult.Error(RuntimeException("quota exceeded"))

            val worker = directConstruct(runAttemptCount = 1) // < MAX_RETRIES (3)
            val result = worker.doWork()

            assertTrue(result is WorkResult.Retry)
        }

    @Test
    fun `doWork returns failure when repository error occurs at max retries`() =
        runTest {
            every { networkMonitor.isCurrentlyOnline() } returns true
            coEvery { quizRepository.syncPendingResults() } returns
                DomainResult.Error(RuntimeException("quota exceeded"))

            val worker = directConstruct(runAttemptCount = 3) // == MAX_RETRIES
            val result = worker.doWork()

            assertTrue(result is WorkResult.Failure)
        }

    private fun directConstruct(runAttemptCount: Int): SyncQuizResultsWorker {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.runAttemptCount } returns runAttemptCount
        return SyncQuizResultsWorker(
            context = context,
            workerParams = workerParams,
            quizRepository = quizRepository,
            networkMonitor = networkMonitor,
        )
    }
}
