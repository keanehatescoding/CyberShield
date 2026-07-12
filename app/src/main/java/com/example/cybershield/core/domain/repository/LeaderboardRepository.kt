package com.example.cybershield.core.domain.repository

import com.example.cybershield.core.domain.model.LeaderboardEntry
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface LeaderboardRepository {
    /** Real-time top-20 leaderboard, ordered by XP descending. */
    fun getTopLeaderboard(limit: Int = 50): Flow<Result<List<LeaderboardEntry>>>
}
