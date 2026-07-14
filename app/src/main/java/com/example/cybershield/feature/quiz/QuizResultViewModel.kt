package com.example.cybershield.feature.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.cybershield.QuizResultRoute
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.domain.repository.QuizRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class QuizResultViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val resultId = savedStateHandle.toRoute<QuizResultRoute>().resultId

    val uiState: StateFlow<QuizResultUiState> =
        flow { emit(quizRepository.getQuizAttempt(resultId)) }
            .map { result -> result?.let { QuizResultUiState.Loaded(it) } ?: QuizResultUiState.NotFound }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuizResultUiState.Loading)
}