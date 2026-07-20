package com.example.cybershield.feature.quiz

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.usecase.GetQuizUseCase
import com.example.cybershield.core.domain.usecase.SubmitAnswerUseCase
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.util.QuizScoring
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
import java.util.UUID
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
    private val userRepository: UserRepository,
    private val quizRepository: QuizRepository,
    private val getCurrentSession: GetCurrentSessionUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    companion object {
        const val QUESTION_TIME_SECONDS = 30
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
    internal var resultIdProvider: () -> String = { UUID.randomUUID().toString() }

    private val quizId: String = requireNotNull(savedStateHandle["quizId"]) {
        "QuizViewModel requires a quizId in the SavedStateHandle (QuizRoute)"
    }
    private val uid: String get() = getCurrentSession()?.uid ?: ""

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val _timeLeft = MutableStateFlow(QUESTION_TIME_SECONDS)
    val timeLeft: StateFlow<Int> = _timeLeft.asStateFlow()

    private val _events = Channel<QuizUiEvent>(Channel.BUFFERED)
    val events: Flow<QuizUiEvent> = _events.receiveAsFlow()
    private var questions: List<Question> = emptyList()
    private var currentIndex: Int = 0
    private var score: Int = 0
    private var correctCount: Int = 0
    private var pendingCount: Int = 0
    private var timerJob: Job? = null
    private var quizStartElapsed: Long = 0L

    // Generated once, when the quiz starts loading — not at completion — so
    // every answer (including the very first one) can be tagged with the
    // same id. That's what lets FinalizeQuizAttemptsUseCase later recompute
    // a single attempt's score from Room, and what stops a retake of the
    // same quizId from blurring into a previous attempt's answers.
    private var resultId: String = ""

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
                                    resultId = resultIdProvider()
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
        _timeLeft.value = QUESTION_TIME_SECONDS

        _uiState.value =
            QuizUiState.Active(
                question = question,
                questionIndex = index,
                totalQuestions = questions.size,
                score = score,
            )

        timerJob =
            viewModelScope.launch {
                for (tick in QUESTION_TIME_SECONDS downTo 0) {
                    val current = _uiState.value as? QuizUiState.Active ?: break
                    if (current.isAnswered) break
                    _timeLeft.value = tick
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
        processAnswer(selectedIndex, _timeLeft.value)
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun processAnswer(
        selectedIndex: Int,
        timeRemaining: Int,
    ) {
        val current = _uiState.value as? QuizUiState.Active ?: return
        if (!hasAnswered.compareAndSet(false, newValue = true)) return

        val question = questions[currentIndex]

        // Show the "answered, awaiting result" state immediately — no
        // correct/incorrect reveal yet, because the client doesn't know the
        // answer. That only comes back from validateAnswer / a later sync.
        _uiState.value =
            current.copy(
                selectedOption = selectedIndex,
                isAnswered = true,
                isCorrect = null,
                score = score,
            )

        if (uid.isBlank()) {
            // Not signed in — nothing to grade against server-side (no uid
            // to attach the result to). Advance without a graded outcome.
            viewModelScope.launch {
                delay(FEEDBACK_DELAY_MS.milliseconds)
                advanceQuiz()
            }
            return
        }

        viewModelScope.launch {
            val submitJob =
                async {
                    try {
                        submitAnswer(
                            quizId = quizId,
                            resultId = resultId,
                            question = question,
                            selectedIndex = selectedIndex,
                            selectedAnswer = question.options.getOrElse(selectedIndex) { "" },
                            userId = uid,
                            timeRemaining = timeRemaining,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.Error(e)
                    }
                }
            val feedbackDelay = async { delay(FEEDBACK_DELAY_MS.milliseconds) }

            val result = submitJob.await()
            feedbackDelay.await()

            when (result) {
                is Result.Success -> {
                    val validation = result.data
                    if (validation != null) {
                        // Online path — server graded it immediately.
                        val points = QuizScoring.pointsFor(isCorrect = validation.isCorrect, timeRemaining = timeRemaining)
                        score += points
                        if (validation.isCorrect) correctCount++

                        _uiState.value =
                            (_uiState.value as? QuizUiState.Active)?.copy(
                                isCorrect = validation.isCorrect,
                                revealedCorrectIndex = validation.correctIndex,
                                revealedExplanation = validation.explanation,
                                score = score,
                            ) ?: _uiState.value
                    } else {
                        // Offline path — cached locally, no verdict yet.
                        pendingCount++
                        _uiState.value =
                            (_uiState.value as? QuizUiState.Active)?.copy(
                                isCorrect = null,
                                isPending = true,
                            ) ?: _uiState.value
                        _events.send(
                            QuizUiEvent.AnswerSyncFailed(
                                "You're offline — this answer is saved and will be graded once you're back online.",
                            ),
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.value =
                        (_uiState.value as? QuizUiState.Active)?.copy(saveFailed = true)
                            ?: _uiState.value
                    _events.send(
                        QuizUiEvent.AnswerSyncFailed(
                            "Couldn't save your answer — it'll sync once you're back online.",
                        ),
                    )
                }

                Result.Loading -> Unit // never emitted by submitAnswer
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
        if (!hasFinished.compareAndSet(expectedValue = false, newValue = true)) return

        viewModelScope.launch {
            val total = questions.size
            // Pass/fail (and the displayed percentage) reflects correctness, not
            // the speed-weighted score — otherwise a user who answers everything
            // right but slowly can fail, while a fast-but-wrong run can pass.
            val percentage = if (total > 0) (correctCount * 100) / total else 0
            val passed = percentage >= QuizScoring.PASS_PERCENTAGE
            val timeTaken = (elapsedRealtimeProvider() - quizStartElapsed) / 1000
            val provisional = pendingCount > 0
            val moduleId = questions.firstOrNull()?.moduleId ?: ""
            val moduleName = questions.firstOrNull()?.moduleName ?: ""
            val quizTitle = questions.firstOrNull()?.quizTitle ?: "CyberShield Quiz"

            // While any answer from this session is still awaiting server
            // grading (answered offline), correctCount/percentage/passed are
            // necessarily provisional — awarding XP, a badge, or a
            // certificate off an unverified score would reopen the same
            // hole this refactor closes. FinalizeQuizAttemptsUseCase awards
            // these later, off a recomputed score, once
            // SyncQuizResultsWorker confirms every answer has synced.
            val xpEarned =
                if (uid.isNotBlank() && !provisional) {
                    // XP, the certificate, and the CyberDefender badge are all
                    // computed and applied server-side by finalizeQuizAttemptFn
                    // now — never locally, so none of them can be forged (XP
                    // used to be a direct client Firestore increment, which let
                    // a malicious client set its own xp to anything). Called
                    // for every attempt, pass or fail, since XP is awarded
                    // either way; only a passing attempt also gets a cert/badge.
                    val finalizeResult = quizRepository.finalizeQuizAttemptServer(resultId)
                    if (finalizeResult is Result.Error && passed) {
                        _events.send(
                            QuizUiEvent.CertificateGenerationFailed(
                                "You passed, but we couldn't issue your certificate. Please try again from your profile.",
                            ),
                        )
                    }
                    finalizeResult.dataOrNull?.xpEarned ?: 0
                } else if (uid.isNotBlank() && provisional) {
                    _events.send(
                        QuizUiEvent.AnswerSyncFailed(
                            "Some answers are still offline — your final score, XP, and certificate will be finalized once they sync.",
                        ),
                    )
                    0
                } else {
                    0
                }

            val quizResult =
                QuizResult(
                    quizId = quizId,
                    score = score,
                    totalQuestions = total,
                    correctCount = correctCount,
                    percentage = percentage,
                    xpEarned = xpEarned,
                    passed = passed,
                    timeTaken = timeTaken,
                    provisional = provisional,
                )
            quizRepository.saveQuizAttempt(
                resultId = resultId,
                userId = uid,
                moduleId = moduleId,
                moduleName = moduleName,
                quizTitle = quizTitle,
                result = quizResult,
            )

            _uiState.value = QuizUiState.Completed(quizResult)
            _events.send(QuizUiEvent.NavigateToResult(resultId))
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
    }
}
