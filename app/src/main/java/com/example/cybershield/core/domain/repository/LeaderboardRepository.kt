package com.example.cybershield.core.domain.repository

import com.example.cybershield.feature.leaderboard.LeaderboardEntry
import kotlinx.coroutines.flow.Flow
import com.example.cybershield.core.domain.util.Result

interface LeaderboardRepository {
    /** Real-time top-20 leaderboard, ordered by XP descending. */
    fun getTopLeaderboard(limit: Int = 20): Flow<Result<List<LeaderboardEntry>>>
}