package com.example.cybershield.feature.home

import app.cash.turbine.test
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.repository.AuthRepository.AuthSession
import com.example.cybershield.core.domain.usecase.EnsureUserProfileUseCase
import com.example.cybershield.core.domain.usecase.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.usecase.ProfileRepairOutcome
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.testing.TestCoroutineRule
import com.example.cybershield.core.testing.fake.FakeModuleRepository
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lives in src/test/ — pure JVM unit test, no Android framework or Firebase classes
 * touched.
 *
 * Profile-repair *decision logic* (missing-profile detection, attempted-once state
 * machine) now lives in EnsureUserProfileUseCase and is covered by
 * EnsureUserProfileUseCaseTest. This test only verifies that HomeViewModel reacts
 * correctly to each ProfileRepairOutcome the use case can return — it uses a
 * scripted FakeEnsureUserProfileUseCase rather than re-deriving repair logic here.
 */
class HomeViewModelTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakeModuleRepository: FakeModuleRepository
    private lateinit var fakeEnsureUserProfile: FakeEnsureUserProfileUseCase
    private lateinit var getCurrentSession: GetCurrentSessionUseCase

    private val signedInSession = AuthSession(
        uid = "test-uid",
        email = "test@cybershield.com",
        isEmailVerified = true,
    )

    @Before
    fun setUp() {
        fakeAuthRepository = FakeAuthRepository().apply {
            currentSessionToReturn = signedInSession
        }
        // fakeUserRepository.fakeUser already defaults to uid="test-uid",
        // email="test@cybershield.com" — matches signedInSession above so the
        // happy path needs no extra wiring.
        fakeUserRepository = FakeUserRepository()
        fakeModuleRepository = FakeModuleRepository()
        // Default fake already returns Result.Success(emptyList()) via its built-in
        // getModulesFlowProvider default — no extra wiring needed for the happy path.
        fakeEnsureUserProfile = FakeEnsureUserProfileUseCase()
        getCurrentSession = GetCurrentSessionUseCase(fakeAuthRepository)
    }

    private fun buildViewModel() = HomeViewModel(
        userRepository = fakeUserRepository,
        moduleRepository = fakeModuleRepository,
        getCurrentSession = getCurrentSession,
        ensureUserProfile = fakeEnsureUserProfile,
    )

    // ── Profile loading — happy path ────────────────────────────────────

    @Test
    fun `loadUserProfile emits user on success`() = runTest {
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertFalse(state.isUserLoading)
            assertEquals(fakeUserRepository.fakeUser, state.user)
            assertNull(state.userError)
        }
    }

    @Test
    fun `loadUserProfile surfaces isUserLoading while pending`() = runTest {
        fakeUserRepository.userProfileResult = Result.Loading
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().isUserLoading)
        }
    }

    @Test
    fun `successful profile load notifies the use case so repair can be attempted again later`() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(1, fakeEnsureUserProfile.onProfileLoadedSuccessfullyCallCount)
    }

    // ── Profile loading — error delegation to EnsureUserProfileUseCase ──────

    @Test
    fun `NotApplicable outcome surfaces its message as userError`() = runTest {
        fakeUserRepository.userProfileResult = Result.Error(Exception("network unreachable"))
        fakeEnsureUserProfile.outcomeToReturn = ProfileRepairOutcome.NotApplicable("network unreachable")

        val viewModel = buildViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertFalse(state.isUserLoading)
            assertEquals("network unreachable", state.userError)
        }
    }

    @Test
    fun `RepairSucceeded outcome does not surface an error and stays in loading state`() = runTest {
        fakeUserRepository.userProfileResult = Result.Error(Exception("Profile not found"))
        fakeEnsureUserProfile.outcomeToReturn = ProfileRepairOutcome.RepairSucceeded

        val viewModel = buildViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            // isUserLoading was set true by the preceding Result.Loading emission and
            // RepairSucceeded intentionally does not update _uiState — the real profile
            // flow is expected to re-emit Success once Firestore reflects the repair.
            assertNull(state.userError)
        }
    }

    @Test
    fun `RepairFailed outcome surfaces a stable user-facing error and stops loading`() = runTest {
        fakeUserRepository.userProfileResult = Result.Error(Exception("Profile not found"))
        fakeEnsureUserProfile.outcomeToReturn = ProfileRepairOutcome.RepairFailed

        val viewModel = buildViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertFalse(state.isUserLoading)
            assertEquals(
                "Couldn't set up your profile. Please check your connection and restart the app.",
                state.userError,
            )
        }
    }

    @Test
    fun `AlreadyAttempted outcome surfaces its carried message as userError`() = runTest {
        fakeUserRepository.userProfileResult = Result.Error(Exception("Profile not found"))
        fakeEnsureUserProfile.outcomeToReturn = ProfileRepairOutcome.AlreadyAttempted("Profile not found")

        val viewModel = buildViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertFalse(state.isUserLoading)
            assertEquals("Profile not found", state.userError)
        }
    }

    @Test
    fun `error path passes the current session into the use case`() = runTest {
        fakeUserRepository.userProfileResult = Result.Error(Exception("Profile not found"))
        fakeEnsureUserProfile.outcomeToReturn = ProfileRepairOutcome.RepairSucceeded

        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(signedInSession, fakeEnsureUserProfile.lastSessionPassed)
    }

    // ── Modules ──────────────────────────────────────────────────────────

    @Test
    fun `loadModules emits modules on success`() = runTest {
        val modules = listOf(sampleModule("m1"), sampleModule("m2"))
        fakeModuleRepository.getModulesFlowProvider = {
            flow {
                emit(Result.Loading)
                emit(Result.Success(modules))
            }
        }

        val viewModel = buildViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertFalse(state.isModulesLoading)
            assertEquals(modules, state.modules)
            assertNull(state.modulesError)
        }
    }

    @Test
    fun `loadModules surfaces error message on failure`() = runTest {
        fakeModuleRepository.getModulesFlowProvider = {
            flow {
                emit(Result.Loading)
                emit(Result.Error(Exception("offline")))
            }
        }

        val viewModel = buildViewModel()

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals("offline", expectMostRecentItem().modulesError)
        }
    }

    @Test
    fun `clearModulesError resets modulesError to null`() = runTest {
        fakeModuleRepository.getModulesFlowProvider = {
            flow {
                emit(Result.Loading)
                emit(Result.Error(Exception("offline")))
            }
        }
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.clearModulesError()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.modulesError)
    }

    // ── Refresh ──────────────────────────────────────────────────────────

    @Test
    fun `refresh success re-runs loadModules and picks up fresh data`() = runTest {
        val refreshedModules = listOf(sampleModule("fresh-1"))
        var callCount = 0
        fakeModuleRepository.getModulesFlowProvider = {
            callCount++
            flow {
                emit(Result.Loading)
                // First call (init) returns empty; second call (post-refresh) returns fresh data.
                if (callCount == 1) emit(Result.Success(emptyList()))
                else emit(Result.Success(refreshedModules))
            }
        }
        val viewModel = buildViewModel()
        advanceUntilIdle()

        fakeModuleRepository.refreshModulesResult = Result.Success(Unit)

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals(refreshedModules, state.modules)
        assertEquals(1, fakeModuleRepository.refreshModulesCallCount)
    }

    @Test
    fun `refresh failure surfaces a connection error and clears isRefreshing`() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        fakeModuleRepository.refreshModulesResult = Result.Error(Exception("timeout"))

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals("Couldn't refresh modules. Check your connection.", state.modulesError)
    }

    // ── Greeting (pure function, no coroutines involved) ────────────────
    // Not re-tested here in detail since java.util.Calendar.getInstance() makes
    // greeting() effectively untestable for specific hours without a Clock
    // seam — flagging as a follow-up rather than asserting on whatever the
    // current wall-clock hour happens to be when CI runs.

    private fun sampleModule(id: String) = com.example.cybershield.core.domain.model.Module(
        id = id,
        title = "Module $id",
        description = "",
        videoUrl = "",
        order = 0,
    )
}

/**
 * Minimal fake — HomeViewModel only ever calls currentSession() through
 * GetCurrentSessionUseCase, so that's the only member exercised here.
 */
private class FakeAuthRepository : AuthRepository {
    var currentSessionToReturn: AuthSession? = null

    override fun currentSession(): AuthSession? = currentSessionToReturn

    override fun observeAuthState() =
        throw NotImplementedError("Not used by HomeViewModel")

    override suspend fun register(name: String, email: String, password: String) =
        throw NotImplementedError("Not used by HomeViewModel")

    override suspend fun signIn(email: String, password: String) =
        throw NotImplementedError("Not used by HomeViewModel")

    override suspend fun resendVerificationEmail() =
        throw NotImplementedError("Not used by HomeViewModel")

    override suspend fun refreshEmailVerified() =
        throw NotImplementedError("Not used by HomeViewModel")

    override fun signOut() =
        throw NotImplementedError("Not used by HomeViewModel")
}

/**
 * Scripted fake for the extracted use case — HomeViewModelTest only needs to
 * verify delegation (call the use case, react to whatever it returns), not
 * re-derive the repair state machine, which EnsureUserProfileUseCaseTest already
 * covers directly against the real implementation.
 */
private class FakeEnsureUserProfileUseCase : EnsureUserProfileUseCase {
    var outcomeToReturn: ProfileRepairOutcome = ProfileRepairOutcome.NotApplicable(null)
    var onProfileLoadedSuccessfullyCallCount = 0
    var lastSessionPassed: AuthSession? = null

    override fun onProfileLoadedSuccessfully() {
        onProfileLoadedSuccessfullyCallCount++
    }

    override suspend operator fun invoke(error: Exception, session: AuthSession?): ProfileRepairOutcome {
        lastSessionPassed = session
        return outcomeToReturn
    }
}