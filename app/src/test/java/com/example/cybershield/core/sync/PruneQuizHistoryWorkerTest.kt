package com.example.cybershield.core.sync

import android.content.Context
import androidx.work.WorkerParameters
import com.example.cybershield.core.database.dao.QuizAttemptDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.domain.util.CrashReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import androidx.work.ListenableWorker.Result as WorkResult

/**
 * Covers PruneQuizHistoryWorker.doWork(): the happy path (both DAO deletes
 * run, answers before attempts) and that a thrown exception is reported to
 * CrashReporter and mapped to Result.failure() rather than propagating.
 */
class PruneQuizHistoryWorkerTest {
    private lateinit var quizResultDao: QuizResultDao
    private lateinit var quizAttemptDao: QuizAttemptDao
    private lateinit var crashReporter: CrashReporter
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        quizResultDao = mockk()
        quizAttemptDao = mockk()
        crashReporter = mockk(relaxed = true)
    }

    @Test
    fun `doWork prunes results before attempts and returns success`() =
        runTest {
            coEvery { quizResultDao.deleteForFinalizedAttemptsOlderThan(any()) } returns Unit
            coEvery { quizAttemptDao.deleteOlderThan(any()) } returns Unit

            val worker = directConstruct()
            val result = worker.doWork()

            assertTrue(result is WorkResult.Success)
            // deleteForFinalizedAttemptsOlderThan reads quiz_attempts to decide
            // what's eligible, so it must run while those rows still exist.
            coVerifyOrder {
                quizResultDao.deleteForFinalizedAttemptsOlderThan(any())
                quizAttemptDao.deleteOlderThan(any())
            }
        }

    @Test
    fun `doWork reports to CrashReporter and returns failure when a DAO call throws`() =
        runTest {
            val boom = RuntimeException("disk full")
            coEvery { quizResultDao.deleteForFinalizedAttemptsOlderThan(any()) } throws boom

            val worker = directConstruct()
            val result = worker.doWork()

            assertTrue(result is WorkResult.Failure)
            verify(exactly = 1) { crashReporter.recordException(boom) }
            coVerify(exactly = 0) { quizAttemptDao.deleteOlderThan(any()) }
        }

    private fun directConstruct(): PruneQuizHistoryWorker {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.runAttemptCount } returns 0
        return PruneQuizHistoryWorker(
            context = context,
            workerParams = workerParams,
            quizResultDao = quizResultDao,
            quizAttemptDao = quizAttemptDao,
            crashReporter = crashReporter,
        )
    }
}
