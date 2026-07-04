package com.example.cybershield.feature.history

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.example.cybershield.core.domain.model.QuizResultHistoryItem
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.testing.fake.FakeQuizRepository
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun `historyPaged emits repository data for the signed-in user`() =
        runTest {
            signedInSession()
            quizRepository.quizResultHistoryProvider = { uid ->
                assertEquals(testUid, uid)
                flowOf(PagingData.from(testHistory))
            }

            val viewModel = createViewModel()

            val snapshot = viewModel.historyPaged.asSnapshot()
            assertEquals(testHistory, snapshot)
        }

    @Test
    fun `historyPaged is empty when no user is signed in`() =
        runTest {
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
        runTest {
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
