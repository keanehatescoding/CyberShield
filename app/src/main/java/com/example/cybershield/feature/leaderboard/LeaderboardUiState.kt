package com.example.cybershield.feature.leaderboard

import com.example.cybershield.core.domain.model.LeaderboardEntry

data class LeaderboardUiState(
    val entries:    List<LeaderboardEntry> = emptyList(),
    val currentUid: String                 = "",
    val isLoading:  Boolean                = true,
    val error:      String?                = null,
)