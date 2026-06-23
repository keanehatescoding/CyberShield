package com.example.cybershield.feature.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.repository.LeaderboardRepository
import com.example.cybershield.core.domain.util.onError
import com.example.cybershield.core.domain.util.onSuccess
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaderboardEntry(
    val uid:         String,
    val displayName: String,
    val xp:          Int,
    val level:       Int,
    val badges:      List<String>,
)

data class LeaderboardUiState(
    val entries:   List<LeaderboardEntry> = emptyList(),
    val currentUid: String                 = "",
    val isLoading:  Boolean                = true,
    val error:      String?                = null,
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val leaderboardRepository: LeaderboardRepository,
    firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState(
        currentUid = firebaseAuth.currentUser?.uid ?: ""
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