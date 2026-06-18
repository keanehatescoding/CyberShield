package com.example.cybershield.feature.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState(
        currentUid = firebaseAuth.currentUser?.uid ?: ""
    ))
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init { loadLeaderboard() }

    private fun loadLeaderboard() {
        viewModelScope.launch {
            val listener = firestore
                .collection("users")
                .orderBy("xp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        _uiState.update { it.copy(isLoading = false, error = error.message) }
                        return@addSnapshotListener
                    }
                    val entries = snap?.documents?.mapIndexedNotNull { _, doc ->
                        LeaderboardEntry(
                            uid         = doc.id,
                            displayName = doc.getString("displayName") ?: "Anonymous",
                            xp          = doc.getLong("xp")?.toInt() ?: 0,
                            level       = doc.getLong("level")?.toInt() ?: 1,
                            badges      = (doc.get("badges") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                        )
                    } ?: emptyList()
                    _uiState.update {
                        it.copy(entries = entries, isLoading = false, error = null)
                    }
                }
        }
    }
}