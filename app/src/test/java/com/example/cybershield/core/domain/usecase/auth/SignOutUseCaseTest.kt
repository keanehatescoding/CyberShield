package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.database.CyberShieldDatabase
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * [SignOutUseCase] must wipe the local Room cache after signing out of Firebase — otherwise
 * unsynced quiz_results/quiz_attempts rows (SyncQuizResultsWorker's queries don't filter by
 * userId) leak into whichever account signs in next on the same device. See the class kdoc.
 */
class SignOutUseCaseTest {
    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val database: CyberShieldDatabase = mockk(relaxed = true)
    private val useCase = SignOutUseCase(authRepository, database)

    @Test
    fun `invoke signs out of Firebase before clearing the local database`() =
        runTest {
            every { authRepository.signOut() } returns Unit

            useCase()

            coVerifyOrder {
                authRepository.signOut()
                database.clearAllTables()
            }
        }
}
