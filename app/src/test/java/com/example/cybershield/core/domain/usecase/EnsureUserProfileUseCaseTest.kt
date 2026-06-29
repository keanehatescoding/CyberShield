package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.repository.AuthRepository.AuthSession
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.testing.fake.FakeUserRepository
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EnsureUserProfileUseCaseTest {
    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var useCase: EnsureUserProfileUseCase

    private val session =
        AuthSession(
            uid = "test-uid",
            email = "test@cybershield.com",
            isEmailVerified = true,
        )

    @Before
    fun setUp() {
        fakeUserRepository = FakeUserRepository()
        useCase = EnsureUserProfileUseCaseImpl(fakeUserRepository)
    }

    @Test
    fun `non-missing-profile error returns NotApplicable with original message`() =
        runTest {
            val outcome = useCase(Exception("permission denied"), session)

            assertEquals(ProfileRepairOutcome.NotApplicable("permission denied"), outcome)
            assertEquals(0, fakeUserRepository.createUserProfileIfNotExistsCallCount)
        }

    @Test
    fun `missing profile with no session returns NotApplicable and does not attempt repair`() =
        runTest {
            val outcome = useCase(Exception("Profile not found"), session = null)

            assertEquals(ProfileRepairOutcome.NotApplicable("Profile not found"), outcome)
            assertEquals(0, fakeUserRepository.createUserProfileIfNotExistsCallCount)
        }

    @Test
    fun `missing profile with session attempts repair using uid and email`() =
        runTest {
            fakeUserRepository.createUserProfileIfNotExistsResult = Result.Success(Unit)

            val outcome = useCase(Exception("Profile not found"), session)

            assertEquals(ProfileRepairOutcome.RepairSucceeded, outcome)
            assertEquals(1, fakeUserRepository.createUserProfileIfNotExistsCallCount)
            val captured = fakeUserRepository.lastCreateUserProfileIfNotExistsArgs
            assertEquals("test-uid", captured?.uid)
            assertEquals("test@cybershield.com", captured?.email)
            assertEquals("CyberShield User", captured?.displayName)
            assertNull(captured?.photoUrl)
        }

    @Test
    fun `repair failure returns RepairFailed`() =
        runTest {
            fakeUserRepository.createUserProfileIfNotExistsResult =
                Result.Error(Exception("Firestore write failed"))

            val outcome = useCase(Exception("Profile not found"), session)

            assertEquals(ProfileRepairOutcome.RepairFailed, outcome)
        }

    @Test
    fun `second call without a success in between returns AlreadyAttempted and does not retry`() =
        runTest {
            fakeUserRepository.createUserProfileIfNotExistsResult = Result.Success(Unit)
            useCase(Exception("Profile not found"), session)

            val secondOutcome = useCase(Exception("Profile not found"), session)

            assertEquals(ProfileRepairOutcome.AlreadyAttempted("Profile not found"), secondOutcome)
            // Still only the one call from the first invocation — no retry attempted.
            assertEquals(1, fakeUserRepository.createUserProfileIfNotExistsCallCount)
        }

    @Test
    fun `onProfileLoadedSuccessfully resets the attempted flag, allowing repair again`() =
        runTest {
            fakeUserRepository.createUserProfileIfNotExistsResult = Result.Success(Unit)
            useCase(Exception("Profile not found"), session)

            useCase.onProfileLoadedSuccessfully()

            val outcome = useCase(Exception("Profile not found"), session)
            assertEquals(ProfileRepairOutcome.RepairSucceeded, outcome)
            assertEquals(2, fakeUserRepository.createUserProfileIfNotExistsCallCount)
        }

    @Test
    fun `AlreadyAttempted after a failed repair still surfaces the underlying message`() =
        runTest {
            fakeUserRepository.createUserProfileIfNotExistsResult =
                Result.Error(Exception("still broken"))
            useCase(Exception("Profile not found"), session) // first attempt — fails

            val secondOutcome = useCase(Exception("Profile not found"), session)

            // Matches the pre-extraction ViewModel behavior: once profileRepairAttempted
            // is true, subsequent missing-profile errors re-surface the original message
            // rather than going silent or retrying.
            assertEquals(ProfileRepairOutcome.AlreadyAttempted("Profile not found"), secondOutcome)
            assertEquals(1, fakeUserRepository.createUserProfileIfNotExistsCallCount)
        }
}
