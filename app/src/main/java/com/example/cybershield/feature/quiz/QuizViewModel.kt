package com.example.cybershield.feature.quiz

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.cybershield.QuizRoute
import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.usecase.AwardXpUseCase
import com.example.cybershield.core.domain.usecase.GenerateCertificateUseCase
import com.example.cybershield.core.domain.usecase.GetQuizUseCase
import com.example.cybershield.core.domain.usecase.SubmitAnswerUseCase
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.dataOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class QuizViewModel
@Inject
constructor(
    private val getQuiz: GetQuizUseCase,
    private val submitAnswer: SubmitAnswerUseCase,
    private val awardXp: AwardXpUseCase,
    private val generateCertificate: GenerateCertificateUseCase,
    private val userRepository: UserRepository,
    private val getCurrentSession: GetCurrentSessionUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    companion object {
        const val QUESTION_TIME_SECONDS = 30
        const val BASE_POINTS = 100
        const val SPEED_BONUS = 5
        const val PASS_PERCENTAGE = 70
        const val FEEDBACK_DELAY_MS = 1_500L
        const val LOAD_TIMEOUT_MS = 15_000L
    }

    // Test seams. These are intentionally NOT constructor parameters: Hilt's generated
    // Java code calls the @Inject constructor with every declared parameter explicitly,
    // so it can't fall back to a Kotlin default value for an un-injectable type like
    // Long or () -> Long. Keeping them as internal vars lets tests override them
    // after construction while leaving the Hilt-visible constructor injectable.
    internal var elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
    internal var loadTimeoutMs: Long = LOAD_TIMEOUT_MS

    private val quizId: String = savedStateHandle.toRoute<QuizRoute>().quizId
    private val uid: String get() = getCurrentSession()?.uid ?: ""

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()
    private val _events = Channel<QuizUiEvent>(Channel.BUFFERED)
    val events: Flow<QuizUiEvent> = _events.receiveAsFlow()
    private var questions: List<Question> = emptyList()
    private var currentIndex: Int = 0
    private var score: Int = 0
    private var correctCount: Int = 0
    private var timerJob: Job? = null
    private var quizStartElapsed: Long = 0L
    @OptIn(ExperimentalAtomicApi::class)
    private val hasAnswered = AtomicBoolean(false)
    @OptIn(ExperimentalAtomicApi::class)
    private val hasFinished = AtomicBoolean(false)

    init {
        loadQuiz()
    }

    private fun loadQuiz() {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            val completed =
                withTimeoutOrNull(loadTimeoutMs.milliseconds) {
                    getQuiz(quizId).collect { result ->
                        when (result) {
                            is Result.Loading -> {
                                // Avoid clobbering an in-progress quiz if the underlying
                                // flow re-emits Loading (e.g. cache-then-network refresh).
                                if (_uiState.value is QuizUiState.Loading) {
                                    _uiState.value = QuizUiState.Loading
                                }
                            }

                            is Result.Success -> {
                                val quizList = result.data
                                if (quizList.isEmpty()) {
                                    _uiState.value = QuizUiState.Error("No questions found.")
                                    return@collect
                                }
                                if (questions.isEmpty()) {
                                    questions = quizList
                                    quizStartElapsed = elapsedRealtimeProvider()
                                    showQuestion(0)
                                }
                            }

                            is Result.Error -> {
                                if (_uiState.value is QuizUiState.Loading) {
                                    _uiState.value =
                                        QuizUiState.Error(
                                            result.exception.message ?: "Failed to load quiz.",
                                        )
                                }
                            }
                        }
                    }
                }
            if (completed == null && _uiState.value is QuizUiState.Loading) {
                _uiState.value =
                    QuizUiState.Error("This is taking longer than expected. Check your connection and try again.")
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun showQuestion(index: Int) {
        timerJob?.cancel()
        hasAnswered.store(false)
        currentIndex = index
        val question = questions[index]

        _uiState.value =
            QuizUiState.Active(
                question = question,
                questionIndex = index,
                totalQuestions = questions.size,
                score = score,
                timeLeft = QUESTION_TIME_SECONDS,
            )

        timerJob =
            viewModelScope.launch {
                for (tick in QUESTION_TIME_SECONDS downTo 0) {
                    val current = _uiState.value as? QuizUiState.Active ?: break
                    if (current.isAnswered) break
                    _uiState.value = current.copy(timeLeft = tick)
                    if (tick == 0) {
                        processAnswer(selectedIndex = -1, timeRemaining = 0)
                        break
                    }
                    delay(1_000L.milliseconds)
                }
            }
    }

    fun selectAnswer(selectedIndex: Int) {
        val current = _uiState.value as? QuizUiState.Active ?: return
        if (current.isAnswered) return
        timerJob?.cancel()
        processAnswer(selectedIndex, current.timeLeft)
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun processAnswer(
        selectedIndex: Int,
        timeRemaining: Int,
    ) {
        val current = _uiState.value as? QuizUiState.Active ?: return
        if (!hasAnswered.compareAndSet(false, true)) return

        val question = questions[currentIndex]
        val correct = selectedIndex == question.correctIndex
        val points = if (correct) BASE_POINTS + (timeRemaining * SPEED_BONUS) else 0
        score += points
        if (correct) correctCount++

        _uiState.value =
            current.copy(
                selectedOption = selectedIndex,
                isAnswered = true,
                isCorrect = correct,
                score = score,
            )

        if (uid.isBlank()) {
            viewModelScope.launch {
                delay(FEEDBACK_DELAY_MS.milliseconds)
                advanceQuiz()
            }
            return
        }

        viewModelScope.launch {
            val saveJob =
                async {
                    try {
                        submitAnswer(
                            quizId = quizId,
                            question = question,
                            selectedAnswer = question.options.getOrElse(selectedIndex) { "" },
                            isCorrect = correct,
                            userId = uid,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.Error(e)
                    }
                }
            val feedbackDelay = async { delay(FEEDBACK_DELAY_MS.milliseconds) }

            val result = saveJob.await()
            feedbackDelay.await()

            if (result is Result.Error) {
                _uiState.value = (_uiState.value as? QuizUiState.Active)
                    ?.copy(saveFailed = true)
                    ?: _uiState.value
                _events.send(
                    QuizUiEvent.AnswerSyncFailed(
                        "Couldn't save your answer — it'll sync once you're back online.",
                    ),
                )
            }

            advanceQuiz()
        }
    }

    private fun advanceQuiz() {
        if (currentIndex + 1 < questions.size) {
            showQuestion(currentIndex + 1)
        } else {
            finishQuiz()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun finishQuiz() {
        if (!hasFinished.compareAndSet(false, true)) return

        viewModelScope.launch {
            val total = questions.size
            // Pass/fail (and the displayed percentage) reflects correctness, not
            // the speed-weighted score — otherwise a user who answers everything
            // right but slowly can fail, while a fast-but-wrong run can pass.
            val percentage = if (total > 0) (correctCount * 100) / total else 0
            val passed = percentage >= PASS_PERCENTAGE
            val timeTaken = (elapsedRealtimeProvider() - quizStartElapsed) / 1000

            val xpEarned = if (uid.isNotBlank()) {
                val xpResult = awardXp(userId = uid, correctCount = correctCount, totalCount = total)
                userRepository.markQuizCompleted(uid, quizId)
                if (passed) {
                    userRepository.awardBadge(uid, "CyberDefender")
                    val displayName = (userRepository.getUserProfileOnce(uid) as? Result.Success)?.data?.displayName ?: "CyberShield User"
                    val certificateResult =
                        generateCertificate(
                            userId = uid,
                            userName = displayName,
                            moduleId = questions.firstOrNull()?.moduleId ?: "",
                            moduleName = questions.firstOrNull()?.moduleName ?: "",
                            quizTitle = questions.firstOrNull()?.quizTitle ?: "CyberShield Quiz",
                            score = score,
                        )
                    if (certificateResult is Result.Error) {
                        _events.send(
                            QuizUiEvent.CertificateGenerationFailed(
                                "You passed, but we couldn't generate your certificate. Please try again from your profile.",
                            ),
                        )
                    }
                }
                xpResult.dataOrNull ?: 0
            } else 0

            _uiState.value =
                QuizUiState.Completed(
                    QuizResult(
                        quizId = quizId,
                        score = score,
                        totalQuestions = total,
                        correctCount = correctCount,
                        percentage = percentage,
                        xpEarned = xpEarned,
                        passed = passed,
                        timeTaken = timeTaken,
                    ),
                )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}