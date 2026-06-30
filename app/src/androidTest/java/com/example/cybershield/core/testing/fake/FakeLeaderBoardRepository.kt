package com.example.cybershield.core.testing.fake

import com.example.cybershield.core.domain.model.LeaderboardEntry
import com.example.cybershield.core.domain.repository.LeaderboardRepository
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLeaderboardRepository : LeaderboardRepository {
    private val entries = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    private var error: Exception? = null

    fun setEntries(list: List<LeaderboardEntry>) {
        error = null
        entries.value = list
    }

    fun setError(exception: Exception) {
        error = exception
    }

    override fun getTopLeaderboard(limit: Int): Flow<Result<List<LeaderboardEntry>>> =
        entries.map { list ->
            error?.let { return@map Result.Error(it) }
            Result.Success(list.take(limit))
        }
}