package com.example.cybershield.core.sync

import android.content.Context
import androidx.work.WorkerParameters
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.usecase.FinalizeQuizAttemptsUseCase
import com.example.cybershield.core.domain.util.CrashReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import androidx.work.ListenableWorker.Result as WorkResult
import com.example.cybershield.core.domain.util.Result as DomainResult

/**
 * The Firestore batching, chunking, and per-chunk mark-and-delete behavior
 * lives in QuizRepositoryImpl (see QuizRepositoryImplTest). This worker test
 * covers doWork()'s own responsibilities: the offline guard, mapping
 * QuizRepository.syncPendingResults()'s outcome to a WorkManager Result
 * (including the runAttemptCount retry/failure cutoff), and triggering
 * FinalizeQuizAttemptsUseCase after a successful sync — best-effort, so a
 * finalization failure doesn't turn an otherwise-successful sync into a
 * retry (see FinalizeQuizAttemptsUseCaseTest for finalization itself).
 */
class SyncQuizResultsWorkerTest {
    private lateinit var quizRepository: QuizRepository
    private lateinit var finalizeQuizAttempts: FinalizeQuizAttemptsUseCase
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var crashReporter: CrashReporter
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        quizRepository = mockk()
        finalizeQuizAttempts = mockk()
        networkMonitor = mockk()
        crashReporter = mockk(relaxed = true)
    }

    @Test
    fun `doWork returns retry when offline`() =
        runTest {
            every { networkMonitor.isCurrentlyOnline() } returns false

            val worker = directConstruct(runAttemptCount = 0)
            val result = worker.doWork()

            assertTrue(result is WorkResult.Retry)
            coVerify(exactly = 0) { quizRepository.syncPendingResults() }
            coVerify(exactly = 0) { finalizeQuizAttempts() }
        }

    @Test
    fun `doWork returns success and runs finalization when repository reports success`() =
        runTest {
            every { networkMonitor.isCurrentlyOnline() } returns true
            coEvery { quizRepository.syncPendingResults() } returns DomainResult.Success(Unit)
            coEvery { finalizeQuizAttempts() } returns Unit

            val worker = directConstruct(runAttemptCount = 0)
            val result = worker.doWork()

            assertTrue(result is WorkResult.Success)
            coVerify(exactly = 1) { finalizeQuizAttempts() }
        }

    @Test
    fun `doWork still returns success when finalization throws`() =
        runTest {
            // Finalization is best-effort: the answers themselves are safely
            // synced regardless of whether awarding XP/badge/certificate
            // succeeds. A failure here should not undo a successful sync.
            every { networkMonitor.isCurrentlyOnline() } returns true
            coEvery { quizRepository.syncPendingResults() } returns DomainResult.Success(Unit)
            val boom = RuntimeException("certificate generation blew up")
            coEvery { finalizeQuizAttempts() } throws boom

            val worker = directConstruct(runAttemptCount = 0)
            val result = worker.doWork()

            assertTrue(result is WorkResult.Success)
            // ...but it shouldn't vanish silently either.
            verify(exactly = 1) { crashReporter.recordException(boom) }
        }

    @Test
    fun `doWork does not attempt finalization when sync itself fails`() =
        runTest {
            every { networkMonitor.isCurrentlyOnline() } returns true
            coEvery { quizRepository.syncPendingResults() } returns
                DomainResult.Error(RuntimeException("quota exceeded"))

            val worker = directConstruct(runAttemptCount = 0)
            worker.doWork()

            coVerify(exactly = 0) { finalizeQuizAttempts() }
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
            finalizeQuizAttempts = finalizeQuizAttempts,
            networkMonitor = networkMonitor,
            crashReporter = crashReporter,
        )
    }
}
