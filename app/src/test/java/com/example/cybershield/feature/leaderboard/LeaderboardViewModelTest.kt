package com.example.cybershield.feature.leaderboard

import app.cash.turbine.test
import com.example.cybershield.core.domain.model.LeaderboardEntry
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.testing.fake.FakeLeaderboardRepository
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LeaderboardViewModelTest {
    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    private lateinit var leaderboardRepository: FakeLeaderboardRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var getCurrentSession: GetCurrentSessionUseCase

    private val testUid = "test-uid"

    private val testEntries =
        listOf(
            LeaderboardEntry(
                uid = "uid-1",
                displayName = "Alice",
                xp = 340,
                level = 4,
                badges = listOf("phishing-pro"),
            ),
            LeaderboardEntry(
                uid = "uid-2",
                displayName = "Bob",
                xp = 120,
                level = 2,
                badges = emptyList(),
            ),
        )

    @Before
    fun setup() {
        leaderboardRepository = FakeLeaderboardRepository()
        authRepository = mockk()

        every { authRepository.currentSession() } returns
                AuthRepository.AuthSession(
                    uid = testUid,
                    email = "test@cybershield.com",
                    isEmailVerified = true,
                )

        getCurrentSession = GetCurrentSessionUseCase(authRepository)
    }

    private fun createViewModel(): LeaderboardViewModel =
        LeaderboardViewModel(
            leaderboardRepository = leaderboardRepository,
            getCurrentSession = getCurrentSession,
        )

    @Test
    fun `loadLeaderboard emits loading then success with entries`() =
        runTest(testCoroutineRule.testDispatcher) {
            leaderboardRepository.getTopLeaderboardFlowProvider =
                { flowOf(Result.Success(testEntries)) }

            val viewModel = createViewModel()

            viewModel.uiState.test {
                val loading = awaitItem()
                assertTrue(loading.isLoading)

                val success = awaitItem()
                assertFalse(success.isLoading)
                assertEquals(testEntries, success.entries)
                assertNull(success.error)
            }
        }

    @Test
    fun `loadLeaderboard surfaces error message and keeps previous entries out of state`() =
        runTest(testCoroutineRule.testDispatcher) {
            leaderboardRepository.getTopLeaderboardFlowProvider = {
                flowOf(Result.Error(Exception("permission denied")))
            }

            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem() // loading
                val errorState = awaitItem()
                assertFalse(errorState.isLoading)
                assertEquals("permission denied", errorState.error)
                assertTrue(errorState.entries.isEmpty())
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loadLeaderboard requests the default limit of 50`() =
        runTest(testCoroutineRule.testDispatcher) {
            leaderboardRepository.getTopLeaderboardFlowProvider =
                { flowOf(Result.Success(testEntries)) }

            createViewModel()
            advanceUntilIdle()

            assertEquals(listOf(50), leaderboardRepository.getTopLeaderboardCalls)
        }

    @Test
    fun `successive emissions from a real-time flow update state without re-triggering loading`() =
        runTest(testCoroutineRule.testDispatcher) {
            leaderboardRepository.getTopLeaderboardFlowProvider = {
                flow {
                    emit(Result.Success(testEntries))
                    emit(Result.Success(testEntries.take(1)))
                }
            }

            val viewModel = createViewModel()

            viewModel.uiState.test {
                awaitItem() // loading
                val first = awaitItem()
                assertEquals(testEntries, first.entries)

                val second = awaitItem()
                assertEquals(testEntries.take(1), second.entries)
                assertFalse(second.isLoading)
            }
        }
}