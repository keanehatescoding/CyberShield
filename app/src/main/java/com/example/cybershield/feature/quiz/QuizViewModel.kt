package com.example.cybershield.feature.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.cybershield.QuizRoute
import com.example.cybershield.core.domain.model.Quiz
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.usecase.GetQuizUseCase
import com.example.cybershield.core.domain.usecase.SubmitAnswerUseCase
import com.example.cybershield.core.domain.usecase.auth.AwardXpUseCase
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.dataOrNull
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val getQuiz: GetQuizUseCase,
    private val submitAnswer: SubmitAnswerUseCase,
    private val awardXp: AwardXpUseCase,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        const val QUESTION_TIME_SECONDS = 30
        const val BASE_POINTS           = 100
        const val SPEED_BONUS           = 5
        const val PASS_PERCENTAGE       = 70
        const val FEEDBACK_DELAY_MS     = 1_500L
    }

    private val quizId: String = savedStateHandle.toRoute<QuizRoute>().quizId
    private val uid: String get() = firebaseAuth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private var questions:     List<Quiz> = emptyList()
    private var currentIndex:  Int        = 0
    private var score:         Int        = 0
    private var correctCount:  Int        = 0
    private var timerJob:      Job?       = null
    private var quizStartTime: Long       = 0L

    init { loadQuiz() }

    private fun loadQuiz() {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            getQuiz(quizId).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        _uiState.value = QuizUiState.Loading
                    }
                    is Result.Success -> {
                        val quizList = result.data
                        if (quizList.isEmpty()) {
                            _uiState.value = QuizUiState.Error("No questions found.")
                            return@collect
                        }
                        questions     = quizList
                        quizStartTime = System.currentTimeMillis()
                        showQuestion(0)
                    }
                    is Result.Error -> {
                        _uiState.value = QuizUiState.Error(
                            result.exception.message ?: "Failed to load quiz."
                        )
                    }
                }
            }
        }
    }

    private fun showQuestion(index: Int) {
        timerJob?.cancel()
        currentIndex = index
        val quiz     = questions[index]

        _uiState.value = QuizUiState.Active(
            question       = quiz.toQuestion(),
            questionIndex  = index,
            totalQuestions = questions.size,
            score          = score,
            timeLeft       = QUESTION_TIME_SECONDS,
        )

        timerJob = viewModelScope.launch {
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

    private fun processAnswer(selectedIndex: Int, timeRemaining: Int) {
        val current = _uiState.value as? QuizUiState.Active ?: return
        val quiz    = questions[currentIndex]
        val correct = selectedIndex == quiz.correctIndex
        val points  = if (correct) BASE_POINTS + (timeRemaining * SPEED_BONUS) else 0
        score      += points
        if (correct) correctCount++

        viewModelScope.launch {
            submitAnswer(
                quiz           = quiz,
                selectedAnswer = quiz.options.getOrElse(selectedIndex) { "" },
                userId         = uid,
            )
        }

        _uiState.value = current.copy(
            selectedOption = selectedIndex,
            isAnswered     = true,
            isCorrect      = correct,
            score          = score,
        )

        viewModelScope.launch {
            delay(FEEDBACK_DELAY_MS.milliseconds)
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

    private fun finishQuiz() {
        viewModelScope.launch {
            val total      = questions.size
            val maxScore   = total * (BASE_POINTS + (QUESTION_TIME_SECONDS * SPEED_BONUS))
            val percentage = if (maxScore > 0) (score * 100) / maxScore else 0
            val passed     = percentage >= PASS_PERCENTAGE
            val timeTaken  = (System.currentTimeMillis() - quizStartTime) / 1000

            val xpResult = awardXp(
                userId       = uid,
                correctCount = correctCount,
                totalCount   = total,
            )
            val xpEarned = xpResult.dataOrNull ?: 0

            userRepository.markQuizCompleted(uid, quizId)
            if (passed) userRepository.awardBadge(uid, "CyberDefender")

            _uiState.value = QuizUiState.Completed(
                QuizResult(
                    quizId         = quizId,
                    score          = score,
                    totalQuestions = total,
                    xpEarned       = xpEarned,
                    passed         = passed,
                    timeTaken      = timeTaken,
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

private fun Quiz.toQuestion() = Question(
    id           = id,
    text         = text,
    options      = options,
    correctIndex = correctIndex,
    explanation  = explanation,
)