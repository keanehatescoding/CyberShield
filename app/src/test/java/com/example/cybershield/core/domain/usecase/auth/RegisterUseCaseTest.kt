package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.model.AuthError
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.testing.fake.FakeUserRepository
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.example.cybershield.core.domain.repository.AuthRepository.AuthSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * [RegisterUseCase] is where account creation (AuthRepository) and profile creation
 * (UserRepository) get coordinated. AuthRepositoryImpl no longer knows UserRepository
 * exists, so this is the only place that ordering and rollback behavior is tested.
 */
class RegisterUseCaseTest {
    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val authRepository: AuthRepository = mockk()
    private lateinit var userRepository: FakeUserRepository
    private lateinit var useCase: RegisterUseCase

    private val session = AuthSession(uid = "uid-1", email = "jane@example.com", isEmailVerified = false)

    @Before
    fun setUp() {
        userRepository = FakeUserRepository()
        useCase = RegisterUseCase(authRepository, userRepository)
    }

    private fun stubHappyPath() {
        coEvery { authRepository.register("jane@example.com", "pw123456") } returns Result.Success(session)
        coEvery { authRepository.updateDisplayName("Jane") } returns Result.Success(Unit)
        coEvery { authRepository.resendVerificationEmail() } returns Result.Success(Unit)
    }

    @Test
    fun `blank name is rejected before touching either repository`() =
        runTest {
            val result = useCase("", "jane@example.com", "pw123456")

            assertTrue(result is Result.Error)
            coVerify(exactly = 0) { authRepository.register(any(), any()) }
        }

    @Test
    fun `blank email or password is rejected before touching either repository`() =
        runTest {
            val result = useCase("Jane", "", "pw123456")

            assertTrue(result is Result.Error)
            coVerify(exactly = 0) { authRepository.register(any(), any()) }
        }

    @Test
    fun `happy path succeeds and creates the profile with uid, name, and email`() =
        runTest {
            stubHappyPath()

            val result = useCase("Jane", "jane@example.com", "pw123456")

            assertEquals(Result.Success(Unit), result)
            assertEquals(1, userRepository.createUserProfileCallCount)
            val args = userRepository.lastCreateUserProfileArgs
            assertEquals("uid-1", args?.uid)
            assertEquals("Jane", args?.displayName)
            assertEquals("jane@example.com", args?.email)
            coVerify(exactly = 0) { authRepository.deleteCurrentUser() }
        }

    @Test
    fun `auth account creation failure returns the error and never touches UserRepository`() =
        runTest {
            coEvery { authRepository.register("jane@example.com", "pw123456") } returns
                    Result.Error(AuthError.EmailAlreadyInUse)

            val result = useCase("Jane", "jane@example.com", "pw123456")

            assertEquals(Result.Error(AuthError.EmailAlreadyInUse), result)
            assertEquals(0, userRepository.createUserProfileCallCount)
            coVerify(exactly = 0) { authRepository.updateDisplayName(any()) }
            coVerify(exactly = 0) { authRepository.deleteCurrentUser() }
        }

    @Test
    fun `display name failure rolls back the auth account and skips profile creation`() =
        runTest {
            coEvery { authRepository.register("jane@example.com", "pw123456") } returns Result.Success(session)
            coEvery { authRepository.updateDisplayName("Jane") } returns Result.Error(AuthError.Unknown())
            coEvery { authRepository.deleteCurrentUser() } returns Result.Success(Unit)

            val result = useCase("Jane", "jane@example.com", "pw123456")

            assertTrue(result is Result.Error)
            assertEquals(0, userRepository.createUserProfileCallCount)
            coVerify(exactly = 1) { authRepository.deleteCurrentUser() }
        }

    @Test
    fun `profile creation failure rolls back the auth account and surfaces an error`() =
        runTest {
            coEvery { authRepository.register("jane@example.com", "pw123456") } returns Result.Success(session)
            coEvery { authRepository.updateDisplayName("Jane") } returns Result.Success(Unit)
            coEvery { authRepository.deleteCurrentUser() } returns Result.Success(Unit)
            userRepository.createUserProfileResult = Result.Error(Exception("Firestore write failed"))

            val result = useCase("Jane", "jane@example.com", "pw123456")

            assertTrue(result is Result.Error)
            coVerify(exactly = 1) { authRepository.deleteCurrentUser() }
            coVerify(exactly = 0) { authRepository.resendVerificationEmail() }
        }

    @Test
    fun `verification email failure surfaces an error without rolling back the already-created profile`() =
        runTest {
            coEvery { authRepository.register("jane@example.com", "pw123456") } returns Result.Success(session)
            coEvery { authRepository.updateDisplayName("Jane") } returns Result.Success(Unit)
            coEvery { authRepository.resendVerificationEmail() } returns Result.Error(AuthError.NoNetwork)

            val result = useCase("Jane", "jane@example.com", "pw123456")

            assertEquals(Result.Error(AuthError.NoNetwork), result)
            assertEquals(1, userRepository.createUserProfileCallCount)
            // The profile was already created successfully by this point — the account
            // is fully usable, just unverified, so no rollback is warranted.
            coVerify(exactly = 0) { authRepository.deleteCurrentUser() }
        }
}