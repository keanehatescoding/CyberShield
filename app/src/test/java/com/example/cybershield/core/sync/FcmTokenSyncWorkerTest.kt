package com.example.cybershield.core.sync

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.cybershield.core.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import androidx.work.ListenableWorker.Result as WorkResult
import com.example.cybershield.core.domain.util.Result as DomainResult

/**
 * Covers FcmTokenSyncWorker.doWork(): the missing-token guard, the
 * not-signed-in-yet no-op path, the success path, and the retry/failure
 * cutoff at MAX_RETRIES (3) when UserRepository.updateFcmToken() throws.
 */
class FcmTokenSyncWorkerTest {
    private lateinit var userRepository: UserRepository
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        userRepository = mockk()
        firebaseAuth = mockk()
    }

    @Test
    fun `doWork returns failure when token is missing from input data`() =
        runTest {
            val worker = directConstruct(runAttemptCount = 0, token = null)

            val result = worker.doWork()

            assertTrue(result is WorkResult.Failure)
            coVerify(exactly = 0) { userRepository.updateFcmToken(any(), any()) }
        }

    @Test
    fun `doWork returns success without updating when no user is signed in`() =
        runTest {
            every { firebaseAuth.currentUser } returns null
            val worker = directConstruct(runAttemptCount = 0, token = "token-123")

            val result = worker.doWork()

            assertTrue(result is WorkResult.Success)
            coVerify(exactly = 0) { userRepository.updateFcmToken(any(), any()) }
        }

    @Test
    fun `doWork returns success and updates token when user is signed in`() =
        runTest {
            val firebaseUser = mockk<FirebaseUser>()
            every { firebaseUser.uid } returns "uid-1"
            every { firebaseAuth.currentUser } returns firebaseUser
            coEvery { userRepository.updateFcmToken("uid-1", "token-123") } returns DomainResult.Success(Unit)

            val worker = directConstruct(runAttemptCount = 0, token = "token-123")
            val result = worker.doWork()

            assertTrue(result is WorkResult.Success)
            coVerify(exactly = 1) { userRepository.updateFcmToken("uid-1", "token-123") }
        }

    @Test
    fun `doWork retries when updateFcmToken throws and under max retries`() =
        runTest {
            val firebaseUser = mockk<FirebaseUser>()
            every { firebaseUser.uid } returns "uid-1"
            every { firebaseAuth.currentUser } returns firebaseUser
            coEvery { userRepository.updateFcmToken(any(), any()) } throws RuntimeException("network error")

            val worker = directConstruct(runAttemptCount = 1, token = "token-123") // < MAX_RETRIES (3)
            val result = worker.doWork()

            assertTrue(result is WorkResult.Retry)
        }

    @Test
    fun `doWork returns failure when updateFcmToken throws at max retries`() =
        runTest {
            val firebaseUser = mockk<FirebaseUser>()
            every { firebaseUser.uid } returns "uid-1"
            every { firebaseAuth.currentUser } returns firebaseUser
            coEvery { userRepository.updateFcmToken(any(), any()) } throws RuntimeException("network error")

            val worker = directConstruct(runAttemptCount = 3, token = "token-123") // == MAX_RETRIES
            val result = worker.doWork()

            assertTrue(result is WorkResult.Failure)
        }

    private fun directConstruct(
        runAttemptCount: Int,
        token: String?,
    ): FcmTokenSyncWorker {
        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.runAttemptCount } returns runAttemptCount
        every { workerParams.inputData } returns
            if (token != null) workDataOf("fcm_token" to token) else workDataOf()
        return FcmTokenSyncWorker(
            context = context,
            workerParams = workerParams,
            userRepository = userRepository,
            firebaseAuth = firebaseAuth,
        )
    }
}
