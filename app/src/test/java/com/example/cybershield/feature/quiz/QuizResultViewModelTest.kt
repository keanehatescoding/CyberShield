package com.example.cybershield.feature.quiz

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.testing.fake.FakeQuizRepository
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * QuizResultViewModel reads its argument via `savedStateHandle.toRoute<QuizResultRoute>()`
 * rather than a plain `savedStateHandle["resultId"]` lookup like ModuleViewModel/QuizViewModel
 * do. This is the same class of fixture problem noted for those two ViewModels: a
 * SavedStateHandle built by hand in a test needs the right key ("resultId", matching
 * QuizResultRoute's single property) for the type-safe decode to succeed instead of
 * throwing at construction time.
 */
class QuizResultViewModelTest {
    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    private lateinit var quizRepository: FakeQuizRepository

    private val testResult =
        QuizResult(
            quizId = "quiz-1",
            score = 80,
            totalQuestions = 5,
            correctCount = 4,
            percentage = 80,
            xpEarned = 40,
            passed = true,
            timeTaken = 120L,
        )

    @Before
    fun setup() {
        quizRepository = FakeQuizRepository()
    }

    private fun savedStateHandleFor(resultId: String): SavedStateHandle = SavedStateHandle(mapOf("resultId" to resultId))

    private fun createViewModel(resultId: String = "result-1"): QuizResultViewModel =
        QuizResultViewModel(
            quizRepository = quizRepository,
            savedStateHandle = savedStateHandleFor(resultId),
        )

    @Test
    fun `resultId is decoded from the SavedStateHandle via the type-safe route`() {
        val viewModel = createViewModel(resultId = "result-42")

        assertEquals("result-42", viewModel.resultId)
    }

    @Test
    fun `uiState emits Loading then Loaded when the attempt exists`() =
        runTest {
            quizRepository.saveQuizAttempt(
                resultId = "result-1",
                userId = "uid-1",
                moduleId = "module-1",
                moduleName = "Phishing Basics",
                quizTitle = "Phishing Quiz",
                result = testResult,
            )

            val viewModel = createViewModel(resultId = "result-1")

            viewModel.uiState.test {
                assertEquals(QuizResultUiState.Loading, awaitItem())
                val loaded = awaitItem()
                assertTrue(loaded is QuizResultUiState.Loaded)
                assertEquals(testResult, (loaded as QuizResultUiState.Loaded).result)
            }
        }

    @Test
    fun `uiState emits NotFound when no attempt matches the resultId`() =
        runTest {
            val viewModel = createViewModel(resultId = "missing-result")

            viewModel.uiState.test {
                assertEquals(QuizResultUiState.Loading, awaitItem())
                assertEquals(QuizResultUiState.NotFound, awaitItem())
            }
        }
}
