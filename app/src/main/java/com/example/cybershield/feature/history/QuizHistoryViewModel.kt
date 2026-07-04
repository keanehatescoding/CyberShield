package com.example.cybershield.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.cybershield.core.domain.model.QuizResultHistoryItem
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class QuizHistoryViewModel
    @Inject
    constructor(
        quizRepository: QuizRepository,
        getCurrentSession: GetCurrentSessionUseCase,
    ) : ViewModel() {
        private val uid: String = getCurrentSession()?.uid ?: ""

        val historyPaged: Flow<PagingData<QuizResultHistoryItem>> =
            if (uid.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                quizRepository.getQuizResultHistory(uid).cachedIn(viewModelScope)
            }
    }
