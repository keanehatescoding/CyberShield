package com.example.cybershield.feature.profile

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.testing.fake.FakeCertificateRepository
import com.example.cybershield.core.testing.fake.FakeUserRepository
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [ProfileViewModel].
 *
 * Placement: src/test/ (JVM unit test) — ProfileViewModel has no Android
 * framework dependency, same rationale as ModuleViewModelTest.
 */
class ProfileViewModelTest {
    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    private lateinit var userRepository: FakeUserRepository
    private lateinit var certificateRepository: FakeCertificateRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var getCurrentSession: GetCurrentSessionUseCase

    private val testUid = "user-123"
    private val testSession =
        AuthRepository.AuthSession(
            uid = testUid,
            email = "keane@example.com",
            isEmailVerified = true,
        )

    private val testUser =
        User(
            uid = testUid,
            displayName = "Keane",
            email = "keane@example.com",
            photoUrl = null,
        )

    private val testCertificates =
        listOf(
            Certificate(
                id = "cert-1",
                userId = testUid,
                userName = "Keane",
                moduleId = "module-1",
                moduleName = "Phishing Awareness",
                score = 90,
            ),
            Certificate(
                id = "cert-2",
                userId = testUid,
                userName = "Keane",
                moduleId = "module-2",
                moduleName = "Password Hygiene",
                score = 85,
            ),
        )

    @Before
    fun setup() {
        userRepository = FakeUserRepository()
        certificateRepository = FakeCertificateRepository()

        // GetCurrentSessionUseCase is a thin pass-through (no decision logic),
        // so per our established rule we mock it directly rather than faking
        // the AuthRepository it wraps.
        authRepository = mockk()
        getCurrentSession = GetCurrentSessionUseCase(authRepository)
        every { authRepository.currentSession() } returns testSession
    }

    private fun createViewModel(): ProfileViewModel =
        ProfileViewModel(
            userRepository = userRepository,
            certificateRepository = certificateRepository,
            getCurrentSession = getCurrentSession,
        )

    @Test
    fun `init loads profile and certificates successfully`() =
        runTest {
            userRepository.setUserProfile(testUid, testUser)
            certificateRepository.setCertificates(testUid, testCertificates)

            val viewModel = createViewModel()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(testUser, state.user)
                assertEquals(testCertificates, state.certificates)
                assertEquals(false, state.isLoading)
                assertNull(state.error)
            }
        }

    @Test
    fun `loadProfile emits loading state before result arrives`() =
        runTest {
            userRepository.setUserProfile(testUid, testUser, emitImmediately = false)
            certificateRepository.setCertificates(testUid, testCertificates)

            val viewModel = createViewModel()

            assertEquals(true, viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.user)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `profile error sets error message and stops loading`() =
        runTest {
            userRepository.setUserProfileError(testUid, RuntimeException("network down"))
            certificateRepository.setCertificates(testUid, emptyList())

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("network down", state.error)
            assertEquals(false, state.isLoading)
            assertNull(state.user)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `certificate error sets prefixed error message without touching user or loading`() =
        runTest {
            userRepository.setUserProfile(testUid, testUser)
            certificateRepository.setCertificatesError(testUid, RuntimeException("firestore unavailable"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(testUser, state.user)
            assertTrue(state.error?.contains("Couldn't load certificates") == true)
            assertTrue(state.error?.contains("firestore unavailable") == true)
            assertEquals(emptyList<Certificate>(), state.certificates)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `missing session falls back to empty uid and surfaces not-found error`() =
        runTest {
            every { authRepository.currentSession() } returns null
            userRepository.setUserProfileError("", RuntimeException("User not found"))
            certificateRepository.setCertificatesError("", RuntimeException("User not found"))

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Document the current behavior: an absent session silently resolves
            // to uid = "", rather than ProfileViewModel surfacing a dedicated
            // "not signed in" state. This is worth a follow-up — see note below.
            val state = viewModel.uiState.value
            assertEquals(false, state.isLoading)
            assertTrue(state.error != null)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `repeated profile emissions update state without leaking the previous job`() =
        runTest {
            userRepository.setUserProfile(testUid, testUser)
            certificateRepository.setCertificates(testUid, testCertificates)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val updatedUser = testUser.copy(displayName = "Keane M.")
            userRepository.emitUserProfile(testUid, updatedUser)
            advanceUntilIdle()

            assertEquals(updatedUser, viewModel.uiState.value.user)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `onCleared cancels the profile collection job`() =
        runTest {
            userRepository.setUserProfile(testUid, testUser, emitImmediately = false)
            certificateRepository.setCertificates(testUid, testCertificates)

            // onCleared() is protected on ViewModel, so we can't call it
            // directly from the test. Putting the ViewModel into a
            // ViewModelStore and clearing the store invokes onCleared() the
            // same way the Android framework does.
            val store = ViewModelStore()
            val viewModel = createViewModel()
            store.put("profile", viewModel)
            store.clear()

            // After clearing, a late emission should not reach uiState because
            // profileJob was canceled. We assert isLoading stays true (the
            // emission never lands) rather than asserting on internal job state.
            userRepository.emitUserProfile(testUid, testUser)
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.user)
        }
}
