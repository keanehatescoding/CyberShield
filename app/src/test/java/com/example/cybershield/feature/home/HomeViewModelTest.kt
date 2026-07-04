package com.example.cybershield.feature.home

import app.cash.turbine.test
import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.repository.AuthRepository.AuthSession
import com.example.cybershield.core.domain.usecase.EnsureUserProfileUseCase
import com.example.cybershield.core.domain.usecase.ProfileRepairOutcome
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.testing.fake.FakeAuthRepository
import com.example.cybershield.core.testing.fake.FakeModuleRepository
import com.example.cybershield.core.testing.fake.FakeUserRepository
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

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

    private val signedInSession =
        AuthSession(
            uid = "test-uid",
            email = "test@cybershield.com",
            isEmailVerified = true,
        )

    // Arbitrary fixed instant for tests that don't care about greeting() —
    // 2026-01-01T10:00:00Z, i.e. 10 AM UTC, chosen only so the default never
    // accidentally lands on a DST/midnight edge case. Tests that DO care about
    // greeting() override this explicitly via buildViewModel(clock = ...).
    private val defaultClock: Clock =
        Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        fakeAuthRepository =
            FakeAuthRepository().apply {
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

    private fun buildViewModel(clock: Clock = defaultClock) =
        HomeViewModel(
            userRepository = fakeUserRepository,
            moduleRepository = fakeModuleRepository,
            getCurrentSession = getCurrentSession,
            ensureUserProfile = fakeEnsureUserProfile,
            clock = clock,
        )

    // ── Profile loading — happy path ────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loadUserProfile emits user on success`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                advanceUntilIdle()
                val state = expectMostRecentItem()
                assertFalse(state.isUserLoading)
                assertEquals(fakeUserRepository.fakeUser, state.user)
                assertNull(state.userError)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loadUserProfile surfaces isUserLoading while pending`() =
        runTest {
            fakeUserRepository.userProfileResult = Result.Loading
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                advanceUntilIdle()
                assertTrue(expectMostRecentItem().isUserLoading)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `successful profile load notifies the use case so repair can be attempted again later`() =
        runTest {
            advanceUntilIdle()

            assertEquals(1, fakeEnsureUserProfile.onProfileLoadedSuccessfullyCallCount)
        }

    // ── Profile loading — error delegation to EnsureUserProfileUseCase ──────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `NotApplicable outcome surfaces its message as userError`() =
        runTest {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `RepairSucceeded outcome does not surface an error and stays in loading state`() =
        runTest {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `RepairFailed outcome surfaces a stable user-facing error and stops loading`() =
        runTest {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `AlreadyAttempted outcome surfaces its carried message as userError`() =
        runTest {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `error path passes the current session into the use case`() =
        runTest {
            fakeUserRepository.userProfileResult = Result.Error(Exception("Profile not found"))
            fakeEnsureUserProfile.outcomeToReturn = ProfileRepairOutcome.RepairSucceeded

            advanceUntilIdle()

            assertEquals(signedInSession, fakeEnsureUserProfile.lastSessionPassed)
        }

    // ── Modules ──────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loadModules emits modules on success`() =
        runTest {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loadModules surfaces error message on failure`() =
        runTest {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `clearModulesError resets modulesError to null`() =
        runTest {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `refresh success re-runs loadModules and picks up fresh data`() =
        runTest {
            val refreshedModules = listOf(sampleModule("fresh-1"))
            var callCount = 0
            fakeModuleRepository.getModulesFlowProvider = {
                callCount++
                flow {
                    emit(Result.Loading)
                    // First call (init) returns empty; second call (post-refresh) returns fresh data.
                    if (callCount == 1) {
                        emit(Result.Success(emptyList()))
                    } else {
                        emit(Result.Success(refreshedModules))
                    }
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

        @OptIn(ExperimentalCoroutinesApi::class)
        @Test
        fun `isRefreshing stays true until the refreshed modules are actually collected`() =
                runTest {
                        // Second call to getModules() (triggered by refresh()) is backed by a
                        // Channel we control by hand, so nothing is emitted until we say so —
                        // this lets us catch the bug where isRefreshing flips to false the
                        // instant refreshModules() succeeds, before loadModules()'s new
                        // collector has actually received anything.
                        var callCount = 0
                        val secondCallChannel = Channel<Result<List<Module>>>(Channel.UNLIMITED)
                        fakeModuleRepository.getModulesFlowProvider = {
                                callCount++
                                if (callCount == 1) {
                                        flow {
                                                emit(Result.Loading)
                                                emit(Result.Success(emptyList()))
                                            }
                                    } else {
                                        secondCallChannel.receiveAsFlow()
                                    }
                            }
                        val viewModel = buildViewModel()
                        advanceUntilIdle() // finish the init-time load (callCount == 1)

                        fakeModuleRepository.refreshModulesResult = Result.Success(Unit)

                        // Run refresh() concurrently so we can inspect mid-flight state —
                        // it will suspend inside modulesJob?.join() until the channel emits.
                        launch { viewModel.refresh() }
                        advanceUntilIdle()

                        // refreshModules() has succeeded and loadModules() has started collecting
                        // the second flow, but secondCallChannel hasn't emitted anything yet.
                        assertTrue(
                                "isRefreshing should still be true while awaiting fresh module data",
                                viewModel.uiState.value.isRefreshing,
                            )

                        val refreshedModules = listOf(sampleModule("fresh-1"))
                        secondCallChannel.send(Result.Loading)
                        secondCallChannel.send(Result.Success(refreshedModules))
                        secondCallChannel.close()
                        advanceUntilIdle()

                        val state = viewModel.uiState.value
                        assertFalse(state.isRefreshing)
                        assertEquals(refreshedModules, state.modules)
                    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `refresh failure surfaces a connection error and clears isRefreshing`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            fakeModuleRepository.refreshModulesResult = Result.Error(Exception("timeout"))

            viewModel.refresh()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRefreshing)
            assertEquals("Couldn't refresh modules. Check your connection.", state.modulesError)
        }

    // ── Greeting (now testable via injected Clock) ─────────────────────

    @Test
    fun `greeting returns Good morning for early hours`() =
        runTest {
            val morningClock = Clock.fixed(Instant.parse("2026-01-01T07:00:00Z"), ZoneOffset.UTC)
            val viewModel = buildViewModel(clock = morningClock)

            assertEquals("Good morning", viewModel.greeting())
        }

    @Test
    fun `greeting returns Good afternoon for midday hours`() =
        runTest {
            val afternoonClock = Clock.fixed(Instant.parse("2026-01-01T14:00:00Z"), ZoneOffset.UTC)
            val viewModel = buildViewModel(clock = afternoonClock)

            assertEquals("Good afternoon", viewModel.greeting())
        }

    @Test
    fun `greeting returns Good evening for night hours`() =
        runTest {
            val nightClock = Clock.fixed(Instant.parse("2026-01-01T22:00:00Z"), ZoneOffset.UTC)
            val viewModel = buildViewModel(clock = nightClock)

            assertEquals("Good evening", viewModel.greeting())
        }

    @Test
    fun `greeting boundary at hour 5 is morning, hour 4 is evening`() =
        runTest {
            val justBeforeMorning = Clock.fixed(Instant.parse("2026-01-01T04:00:00Z"), ZoneOffset.UTC)
            val exactlyMorning = Clock.fixed(Instant.parse("2026-01-01T05:00:00Z"), ZoneOffset.UTC)

            assertEquals("Good evening", buildViewModel(clock = justBeforeMorning).greeting())
            assertEquals("Good morning", buildViewModel(clock = exactlyMorning).greeting())
        }

    private fun sampleModule(id: String) =
        Module(
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

    override suspend operator fun invoke(
        error: Exception,
        session: AuthSession?,
    ): ProfileRepairOutcome {
        lastSessionPassed = session
        return outcomeToReturn
    }
}
