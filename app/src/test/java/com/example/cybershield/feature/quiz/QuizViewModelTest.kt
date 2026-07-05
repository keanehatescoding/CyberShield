package com.example.cybershield.feature.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import app.cash.turbine.test
import com.example.cybershield.QuizRoute
import com.example.cybershield.core.domain.model.AnswerValidation
import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.usecase.AwardXpUseCase
import com.example.cybershield.core.domain.usecase.GenerateCertificateUseCase
import com.example.cybershield.core.domain.usecase.GetQuizUseCase
import com.example.cybershield.core.domain.usecase.SubmitAnswerUseCase
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.testing.fake.TestCoroutineRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Grading now always comes from SubmitAnswerUseCase's return value — either
 * Result.Success(AnswerValidation) for an online, server-graded answer, or
 * Result.Success(null) for an offline answer that's cached and deferred.
 * These tests script that return value directly rather than deriving
 * correctness from a client-held correctIndex (which no longer exists).
 *
 * Quiz completion also no longer hands a QuizResult through navigation args —
 * finishQuiz persists it via quizRepository.saveQuizAttempt(resultId, ...)
 * and emits QuizUiEvent.NavigateToResult(resultId); QuizResultScreen re-reads
 * it from Room by that id.
 */
class QuizViewModelTest {
    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private lateinit var getQuiz: GetQuizUseCase
    private lateinit var submitAnswer: SubmitAnswerUseCase
    private lateinit var awardXp: AwardXpUseCase
    private lateinit var generateCertificate: GenerateCertificateUseCase
    private lateinit var userRepository: UserRepository
    private lateinit var quizRepository: QuizRepository
    private lateinit var getCurrentSession: GetCurrentSessionUseCase
    private lateinit var savedStateHandle: SavedStateHandle

    private val testUid = "user-123"
    private val testQuizId = "quiz-abc"

    // Stand-in for SystemClock.elapsedRealtime(). Tests set this directly to
    // simulate monotonic time passing, independent of any wall-clock behavior.
    private var fakeElapsed: Long = 0L

    private fun question(
        id: String,
        options: List<String> = listOf("A", "B", "C", "D"),
        moduleId: String = "module-1",
        moduleName: String = "Phishing Awareness",
        order: Int = 0,
        quizTitle: String = "Phishing Quiz",
    ) = Question(
        id = id,
        moduleId = moduleId,
        moduleName = moduleName,
        text = "Sample question $id",
        options = options,
        order = order,
        quizTitle = quizTitle,
    )

    private fun validation(
        questionId: String,
        isCorrect: Boolean,
        correctIndex: Int = 0,
        explanation: String = "Because.",
    ) = AnswerValidation(questionId = questionId, isCorrect = isCorrect, correctIndex = correctIndex, explanation = explanation)

    // NOTE: QuizViewModel's @Inject constructor only takes the real
    // dependencies + SavedStateHandle. elapsedRealtimeProvider, loadTimeoutMs,
    // and resultIdProvider are exposed as internal vars on the ViewModel (not
    // constructor params) so that Hilt's generated code never has to resolve
    // a binding for a bare Long, () -> Long, or () -> String. Tests set them
    // here, post-construction.
    private fun buildViewModel(
        elapsedRealtimeProvider: () -> Long = { fakeElapsed },
        loadTimeoutMs: Long = QuizViewModel.LOAD_TIMEOUT_MS,
        resultIdProvider: () -> String = { "result-fixed-id" },
    ): QuizViewModel =
        QuizViewModel(
            getQuiz = getQuiz,
            submitAnswer = submitAnswer,
            awardXp = awardXp,
            generateCertificate = generateCertificate,
            userRepository = userRepository,
            quizRepository = quizRepository,
            getCurrentSession = getCurrentSession,
            savedStateHandle = savedStateHandle,
        ).apply {
            this.elapsedRealtimeProvider = elapsedRealtimeProvider
            this.loadTimeoutMs = loadTimeoutMs
            this.resultIdProvider = resultIdProvider
        }

    @Before
    fun setUp() {
        getQuiz = mockk()
        submitAnswer = mockk()
        awardXp = mockk()
        generateCertificate = mockk()
        userRepository = mockk(relaxed = true)
        quizRepository = mockk(relaxed = true)
        getCurrentSession = mockk()
        savedStateHandle = mockk()
        fakeElapsed = 0L

        every { getCurrentSession() } returns
            AuthRepository.AuthSession(
                uid = testUid,
                email = "keane@example.com",
                isEmailVerified = true,
            )
        every { savedStateHandle.toRoute<QuizRoute>() } returns QuizRoute(quizId = testQuizId)
        coEvery { quizRepository.saveQuizAttempt(any(), any()) } just Runs
    }

    // ---------------------------------------------------------------------
    // Loading & success
    // ---------------------------------------------------------------------

    @Test
    fun `loadQuiz emits Active state with first question on success`() =
        runTest {
            val questions = listOf(question(id = "q1"), question(id = "q2", order = 1))
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(questions))

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is QuizUiState.Active)
                val active = state as QuizUiState.Active
                assertEquals(0, active.questionIndex)
                assertEquals(2, active.totalQuestions)
                assertEquals(0, active.score)
                assertEquals(QuizViewModel.QUESTION_TIME_SECONDS, viewModel.timeLeft.value)
                assertFalse(active.isAnswered)
            }
        }

    @Test
    fun `loadQuiz emits Error state when questions list is empty`() =
        runTest {
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(emptyList()))

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is QuizUiState.Error)
                assertEquals("No questions found.", (state as QuizUiState.Error).message)
            }
        }

    @Test
    fun `loadQuiz emits Error state when repository returns Result Error`() =
        runTest {
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Error(IllegalStateException("offline")))

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state is QuizUiState.Error)
                assertEquals("offline", (state as QuizUiState.Error).message)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `loadQuiz emits timeout Error state when flow never completes`() =
        runTest {
            // A flow that never emits simulates a hung/slow source.
            coEvery { getQuiz(testQuizId) } returns flow { /* never emits */ }

            val viewModel = buildViewModel(loadTimeoutMs = QuizViewModel.LOAD_TIMEOUT_MS)

            viewModel.uiState.test {
                advanceTimeBy((QuizViewModel.LOAD_TIMEOUT_MS + 100).milliseconds)
                val state = awaitItem()
                assertTrue(state is QuizUiState.Error)
                assertTrue((state as QuizUiState.Error).message.contains("longer than expected"))
            }
        }

    // ---------------------------------------------------------------------
    // Answer selection — server-graded (online) path
    // ---------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `selectAnswer shows a neutral awaiting state before the server responds`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(validation("q1", isCorrect = true))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(50)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem() // initial Active state for q1

                viewModel.selectAnswer(selectedIndex = 1)

                // Immediate emission: answered, but not yet graded.
                val awaiting = awaitItem() as QuizUiState.Active
                assertTrue(awaiting.isAnswered)
                assertNull(awaiting.isCorrect)
                assertFalse(awaiting.isPending)
                assertEquals(1, awaiting.selectedOption)

                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `selectAnswer with server-confirmed correct answer reveals correctIndex and increases score`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns
                Result.Success(validation("q1", isCorrect = true, correctIndex = 1, explanation = "B is correct."))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(50)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem() // initial Active state for q1

                viewModel.selectAnswer(selectedIndex = 1)
                awaitItem() // awaiting-grade emission

                val graded = awaitItem() as QuizUiState.Active
                assertEquals(true, graded.isCorrect)
                assertEquals(1, graded.revealedCorrectIndex)
                assertEquals("B is correct.", graded.revealedExplanation)
                assertTrue(graded.score > 0)

                // Only one question — grading completes the quiz next.
                advanceUntilIdle()
                val finalState = expectMostRecentItem()
                assertTrue(finalState is QuizUiState.Completed)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `selectAnswer with server-confirmed incorrect answer awards zero points`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns
                Result.Success(validation("q1", isCorrect = false, correctIndex = 2))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.selectAnswer(selectedIndex = 0)
                awaitItem() // awaiting-grade emission

                val graded = awaitItem() as QuizUiState.Active
                assertEquals(false, graded.isCorrect)
                assertEquals(0, graded.score)

                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 0) { userRepository.awardBadge(any(), any()) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `selectAnswer ignored if question already answered`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(validation("q1", isCorrect = true))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(50)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.selectAnswer(0)
                awaitItem()

                // Second call should be a no-op since isAnswered is already true.
                viewModel.selectAnswer(2)

                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            // Only ever graded once, regardless of the second call.
            coVerify(exactly = 1) { submitAnswer(any(), any(), any(), any(), any()) }
        }

    // ---------------------------------------------------------------------
    // Offline path — deferred grading
    // ---------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `selectAnswer while offline shows pending state and defers scoring`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            // SubmitAnswerUseCase returns Success(null) for the offline / cached path.
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(null)

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.selectAnswer(selectedIndex = 1)
                awaitItem() // awaiting-grade emission

                val pending = awaitItem() as QuizUiState.Active
                assertTrue(pending.isPending)
                assertNull(pending.isCorrect)
                assertNull(pending.revealedCorrectIndex)
                assertEquals(0, pending.score) // no points until graded

                advanceUntilIdle()
                val completed = expectMostRecentItem() as QuizUiState.Completed
                assertTrue(completed.result.provisional)
                assertEquals(0, completed.result.xpEarned)
            }

            // No reward path runs for a provisional (still-offline) completion.
            coVerify(exactly = 0) { awardXp(any(), any(), any()) }
            coVerify(exactly = 0) { userRepository.awardBadge(any(), any()) }
            coVerify(exactly = 0) {
                generateCertificate(userId = any(), userName = any(), moduleId = any(), moduleName = any(), quizTitle = any(), score = any())
            }
            // The provisional attempt is still persisted (so the result screen has something to show).
            coVerify(exactly = 1) { quizRepository.saveQuizAttempt(any(), match { it.provisional }) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `saveFailed flag is set when submitAnswer throws`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } throws RuntimeException("network down")
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.selectAnswer(0)

                awaitItem() // immediate "answered" emission, saveFailed not yet known

                val withSaveFailed = awaitItem() as QuizUiState.Active
                assertTrue(withSaveFailed.saveFailed)

                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------
    // Timeout auto-submit
    // ---------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `question auto-submits with selectedIndex -1 when timer reaches zero`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(validation("q1", isCorrect = false))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel(elapsedRealtimeProvider = { fakeElapsed })

            viewModel.uiState.test {
                awaitItem() // initial Active

                repeat(QuizViewModel.QUESTION_TIME_SECONDS + 1) {
                    fakeElapsed += 1_000L
                    advanceTimeBy(1_000L.milliseconds)
                }

                advanceUntilIdle()

                when (val timedOut = expectMostRecentItem()) {
                    is QuizUiState.Active -> {
                        assertTrue(timedOut.isAnswered)
                        assertEquals(-1, timedOut.selectedOption)
                    }
                    is QuizUiState.Completed -> assertFalse(timedOut.result.passed)
                    else -> error("Unexpected state after timeout: $timedOut")
                }
            }

            // The client sends -1 (its sentinel for "no answer") — never a claimed isCorrect.
            coVerify { submitAnswer(quizId = testQuizId, question = q1, selectedIndex = -1, selectedAnswer = "", userId = testUid) }
        }

    // ---------------------------------------------------------------------
    // Quiz completion: pass / fail, certificate, badge, attempt persistence
    // ---------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `finishQuiz saves attempt and emits NavigateToResult with matching id`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(validation("q1", isCorrect = true))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel(resultIdProvider = { "result-abc" })

            viewModel.events.test {
                viewModel.uiState.test {
                    awaitItem()
                    viewModel.selectAnswer(0)
                    awaitItem()
                    awaitItem()
                    advanceUntilIdle()
                    cancelAndIgnoreRemainingEvents()
                }

                val event = awaitItem()
                assertTrue(event is QuizUiEvent.NavigateToResult)
                assertEquals("result-abc", (event as QuizUiEvent.NavigateToResult).resultId)
                cancelAndIgnoreRemainingEvents()
            }

            coVerify { quizRepository.saveQuizAttempt("result-abc", any()) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `finishQuiz awards badge and generates certificate when passed`() =
        runTest {
            // Single question so the pass/fail outcome depends only on this one
            // server-confirmed answer, not on timer/timeout interactions.
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(validation("q1", isCorrect = true))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(100)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)
            coEvery { userRepository.awardBadge(any(), any()) } returns Result.Success(Unit)
            coEvery { userRepository.getUserProfileOnce(testUid) } returns
                Result.Success(
                    User(uid = testUid, displayName = "Keane M.", email = "keane@example.com"),
                )
            coEvery {
                generateCertificate(
                    userId = any(),
                    userName = any(),
                    moduleId = any(),
                    moduleName = any(),
                    quizTitle = any(),
                    score = any(),
                )
            } returns Result.Success(mockk<Certificate>(relaxed = true))

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem() // q1 active

                viewModel.selectAnswer(0)
                awaitItem() // awaiting-grade emission
                awaitItem() // graded emission
                advanceUntilIdle()

                val completed = expectMostRecentItem()
                assertTrue(completed is QuizUiState.Completed)
                assertTrue((completed as QuizUiState.Completed).result.passed)
                assertFalse(completed.result.provisional)
            }

            coVerify { userRepository.awardBadge(testUid, "CyberDefender") }
            coVerify {
                generateCertificate(
                    userId = testUid,
                    userName = "Keane M.",
                    moduleId = q1.moduleId,
                    moduleName = q1.moduleName,
                    quizTitle = q1.quizTitle,
                    score = any(),
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `finishQuiz does not award badge or certificate when failed`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(validation("q1", isCorrect = false))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.selectAnswer(selectedIndex = 3) // server reports it wrong
                awaitItem()
                awaitItem()
                advanceUntilIdle()

                val completed = expectMostRecentItem()
                assertTrue(completed is QuizUiState.Completed)
                assertFalse((completed as QuizUiState.Completed).result.passed)
            }

            coVerify(exactly = 0) { userRepository.awardBadge(any(), any()) }
            coVerify(exactly = 0) {
                generateCertificate(
                    userId = any(),
                    userName = any(),
                    moduleId = any(),
                    moduleName = any(),
                    quizTitle = any(),
                    score = any(),
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `finishQuiz falls back to default display name when profile fetch fails`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(validation("q1", isCorrect = true))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(100)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)
            coEvery { userRepository.awardBadge(any(), any()) } returns Result.Success(Unit)
            coEvery { userRepository.getUserProfileOnce(testUid) } returns Result.Error(RuntimeException("not found"))
            coEvery {
                generateCertificate(
                    userId = any(),
                    userName = any(),
                    moduleId = any(),
                    moduleName = any(),
                    quizTitle = any(),
                    score = any(),
                )
            } returns Result.Success(mockk<Certificate>(relaxed = true))

            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.selectAnswer(0)
                awaitItem()
                awaitItem()
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            coVerify {
                generateCertificate(
                    userId = testUid,
                    userName = "CyberShield User",
                    moduleId = q1.moduleId,
                    moduleName = q1.moduleName,
                    quizTitle = q1.quizTitle,
                    score = any(),
                )
            }
        }

    // ---------------------------------------------------------------------
    // Elapsed-time calculation (monotonic clock, not wall clock)
    // ---------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `finishQuiz computes timeTaken from elapsedRealtimeProvider not wall clock`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(validation("q1", isCorrect = true))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            fakeElapsed = 1_000L
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem() // q1 active, quizStartElapsed captured = 1_000L

                fakeElapsed = 1_047_000L // 47 simulated seconds later, monotonic source only

                viewModel.selectAnswer(0)
                awaitItem()
                awaitItem()
                advanceUntilIdle()

                val completed = expectMostRecentItem() as QuizUiState.Completed
                assertEquals(47L, completed.result.timeTaken)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `finishQuiz timeTaken stays correct even if wall clock would have gone backward`() =
        runTest {
            // Regression test: previously quizStartTime/timeTaken used System.currentTimeMillis(),
            // which is not monotonic. This test proves the ViewModel now depends solely on
            // elapsedRealtimeProvider (a stand-in for SystemClock.elapsedRealtime(), which IS
            // monotonic in production), so a wall-clock jump cannot produce a negative or
            // bogus duration.
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(validation("q1", isCorrect = true))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            fakeElapsed = 500_000L
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem() // quizStartElapsed = 500_000L

                fakeElapsed = 500_010L // 10s later on the monotonic clock

                viewModel.selectAnswer(0)
                awaitItem()
                awaitItem()
                advanceUntilIdle()

                val completed = expectMostRecentItem() as QuizUiState.Completed
                assertEquals(10L, completed.result.timeTaken)
                assertTrue(completed.result.timeTaken >= 0)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `emits AnswerSyncFailed event when submitAnswer throws`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } throws RuntimeException("network down")
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel()

            viewModel.events.test {
                viewModel.uiState.test {
                    awaitItem()
                    viewModel.selectAnswer(0)
                    awaitItem()
                    advanceUntilIdle()
                    cancelAndIgnoreRemainingEvents()
                }

                val event = awaitItem()
                assertTrue(event is QuizUiEvent.AnswerSyncFailed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `emits AnswerSyncFailed event when the answer is cached offline`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(null)

            val viewModel = buildViewModel()

            viewModel.events.test {
                viewModel.uiState.test {
                    awaitItem()
                    viewModel.selectAnswer(0)
                    awaitItem()
                    advanceUntilIdle()
                    cancelAndIgnoreRemainingEvents()
                }

                val event = awaitItem() as QuizUiEvent.AnswerSyncFailed
                assertTrue(event.message.contains("offline", ignoreCase = true))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `concurrent selectAnswer calls from different threads process exactly once`() =
        runTest {
            val q1 = question(id = "q1")
            coEvery { getQuiz(testQuizId) } returns flowOf(Result.Success(listOf(q1)))
            coEvery { submitAnswer(any(), any(), any(), any(), any()) } returns Result.Success(validation("q1", isCorrect = true))
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)
            coEvery { userRepository.markQuizCompleted(any(), any()) } returns Result.Success(Unit)

            val viewModel = buildViewModel()
            advanceUntilIdle()

            // Drive both calls in concurrently from real background threads
            // (Dispatchers.Default), synchronized with a shared start signal,
            // then rejoin with a suspending `joinAll()`. A prior version of
            // this test used raw Thread + CountDownLatch.join(), which blocks
            // the runTest thread and can deadlock/flake against the virtual
            // time scheduler; suspend-based joining plays nicely with it.
            val go = CompletableDeferred<Unit>()
            val jobs =
                List(2) { i ->
                    launch(Dispatchers.Default) {
                        go.await()
                        viewModel.selectAnswer(i)
                    }
                }
            go.complete(Unit)
            jobs.joinAll()

            advanceUntilIdle()

            coVerify(exactly = 1) { submitAnswer(any(), any(), any(), any(), any()) }
        }
}
