package com.example.cybershield.feature.history

import androidx.lifecycle.ViewModelStore
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.example.cybershield.core.domain.model.QuizResultHistoryItem
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.testing.fake.FakeQuizRepository
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QuizHistoryViewModelTest {
    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    private lateinit var quizRepository: FakeQuizRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var getCurrentSession: GetCurrentSessionUseCase

    private val testUid = "test-uid"

    private val testHistory =
        listOf(
            QuizResultHistoryItem(
                localId = 2L,
                quizId = "quiz-2",
                moduleId = "module-1",
                moduleTitle = "Phishing Basics",
                isCorrect = true,
                selectedAnswer = "A",
                answeredAt = 2_000L,
                synced = true,
            ),
            QuizResultHistoryItem(
                localId = 1L,
                quizId = "quiz-1",
                moduleId = "module-1",
                moduleTitle = "Phishing Basics",
                isCorrect = false,
                selectedAnswer = "B",
                answeredAt = 1_000L,
                synced = false,
            ),
        )

    @Before
    fun setup() {
        quizRepository = FakeQuizRepository()
        authRepository = mockk()
    }

    private fun signedInSession(uid: String = testUid) {
        every { authRepository.currentSession() } returns
            AuthRepository.AuthSession(
                uid = uid,
                email = "test@cybershield.com",
                isEmailVerified = true,
            )
        getCurrentSession = GetCurrentSessionUseCase(authRepository)
    }

    private fun signedOutSession() {
        every { authRepository.currentSession() } returns null
        getCurrentSession = GetCurrentSessionUseCase(authRepository)
    }

    private fun createViewModel(): QuizHistoryViewModel =
        QuizHistoryViewModel(
            quizRepository = quizRepository,
            getCurrentSession = getCurrentSession,
        )

    // cachedIn(viewModelScope) wraps the flow in a perpetual, non-completing shared flow, so
    // asSnapshot() can't rely on the flow itself completing to know loading is done — it needs
    // explicit terminal LoadStates. Without this, PagingData.from(list) alone (no LoadStates)
    // leaves asSnapshot() waiting forever on a flow that never completes.
    // See: https://developer.android.com/jetpack/androidx/releases/paging (asSnapshot +
    // PagingData.from(List) hang, non-completable-flow workaround).
    private val fullyLoadedStates =
        LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `historyPaged emits repository data for the signed-in user`() =
        runTest(testCoroutineRule.testDispatcher) {
            signedInSession()
            quizRepository.quizResultHistoryProvider = { uid ->
                assertEquals(testUid, uid)
                flowOf(PagingData.from(testHistory, sourceLoadStates = fullyLoadedStates))
            }

            val viewModel = createViewModel()
            val store = ViewModelStore()
            store.put("history", viewModel)

            val snapshot = viewModel.historyPaged.asSnapshot()
            advanceUntilIdle()
            assertEquals(testHistory, snapshot)

            // cachedIn(viewModelScope) is designed to live for the ViewModel's lifetime, so its
            // internal collector never completes on its own. Clear the ViewModel (via a
            // ViewModelStore, since ViewModel.clear() isn't public) to cancel viewModelScope and
            // let that job wind down before the test ends.
            store.clear()
            advanceUntilIdle()
        }

    @Test
    fun `historyPaged is empty when no user is signed in`() =
        runTest(testCoroutineRule.testDispatcher) {
            signedOutSession()
            quizRepository.quizResultHistoryProvider = {
                flowOf(PagingData.from(testHistory))
            }

            val viewModel = createViewModel()

            val snapshot = viewModel.historyPaged.asSnapshot()
            assertTrue(snapshot.isEmpty())
        }

    @Test
    fun `historyPaged never queries the repository when signed out`() =
        runTest(testCoroutineRule.testDispatcher) {
            signedOutSession()
            var wasQueried = false
            quizRepository.quizResultHistoryProvider = {
                wasQueried = true
                flowOf(PagingData.from(testHistory))
            }

            val viewModel = createViewModel()
            viewModel.historyPaged.asSnapshot()

            assertTrue(!wasQueried)
        }
}
