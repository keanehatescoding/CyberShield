package com.example.cybershield.core.data.repository

import com.example.cybershield.core.domain.model.LeaderboardEntry
import com.example.cybershield.core.domain.repository.LeaderboardRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.firebase.FirestoreLeaderboardDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaderboardRepositoryImpl
    @Inject
    constructor(
        private val leaderboardDataSource: FirestoreLeaderboardDataSource,
    ) : LeaderboardRepository {
        override fun getTopLeaderboard(limit: Int): Flow<Result<List<LeaderboardEntry>>> =
            leaderboardDataSource
                .topUsers(limit)
                .map { docs ->
                    Result.Success(
                        docs.mapNotNull { data ->
                            val uid = data["_docId"] as? String ?: return@mapNotNull null
                            LeaderboardEntry(
                                uid = uid,
                                displayName = data["displayName"] as? String ?: "Anonymous",
                                xp = (data["xp"] as? Long)?.toInt() ?: 0,
                                level = (data["level"] as? Long)?.toInt() ?: 1,
                                badges = (data["badges"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            )
                        },
                    )
                }
    }
