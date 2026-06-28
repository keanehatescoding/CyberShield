package com.example.cybershield.feature.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.repository.LeaderboardRepository
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.util.onError
import com.example.cybershield.core.domain.util.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
    getCurrentSession: GetCurrentSessionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState(
        currentUid = getCurrentSession()?.uid ?: ""
    ))
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init { loadLeaderboard() }

    private fun loadLeaderboard() {
        viewModelScope.launch {
            leaderboardRepository.getTopLeaderboard().collect { result ->
                result
                    .onSuccess { entries ->
                        _uiState.update {
                            it.copy(entries = entries, isLoading = false, error = null)
                        }
                    }
                    .onError { e ->
                        _uiState.update {
                            it.copy(isLoading = false, error = e.message)
                        }
                    }
            }
        }
    }
}